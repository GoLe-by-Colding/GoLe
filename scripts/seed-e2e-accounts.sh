#!/usr/bin/env bash
# 쓰기 플로우 E2E(create-listing·purchase)용 계정과 세션을 심는다.
#
# 왜 필요한가: 서버가 세션을 실제로 검증한다(UserAuthInterceptor → AccountService.resolve).
# 예전 테스트는 localStorage에 임의 토큰을 넣어 셀러인 척했지만, HttpOnly 쿠키 세션 전환과
# 행위자 위조 차단 이후로는 통하지 않는다. 매물 등록·이미지 업로드가 401로 막힌다.
#
# 왜 가입 API를 쓰지 않는가: 가입은 이메일 인증을 요구하고(AccountService.signIn이
# 미인증 계정을 거부한다), 인증 코드는 로컬에서 로그로만 나가 테스트가 집어올 수 없다.
# 그래서 저장소에 직접 심는다 — 운영 코드에 테스트 전용 우회로를 만들지 않기 위한 선택이다.
#
# 세션 해석이 요구하는 것은 두 가지뿐이다.
#   1) Redis  gole:session:<token> = "<accountId>|<ROLE>"
#   2) Mongo  accounts 문서가 존재하고 정지 상태가 아님
# 비밀번호로 로그인하지 않으므로 passwordHash는 검증되지 않는다(형식만 갖춘 값).
#
# 멱등하다. 여러 번 돌려도 같은 상태가 된다.
set -euo pipefail

MONGO_SERVICE="${MONGO_SERVICE:-mongo}"
REDIS_SERVICE="${REDIS_SERVICE:-redis}"
# 개발 데이터와 절대 섞이지 않는 전용 DB가 기본이다. 운영/개발 DB를 명시해도 아래 가드가
# 즉시 중단하므로, 이 스크립트는 어떤 기존 계정도 삭제하지 않는다.
MONGO_DB="${MONGO_DB:-gole_e2e}"
REDIS_HOST="${REDIS_HOST:-127.0.0.1}"
REDIS_PORT="${REDIS_PORT:-6379}"
REDIS_DATABASE="${REDIS_DATABASE:-15}"

if [[ "$MONGO_DB" == "gole" || "$MONGO_DB" == "admin" || "$MONGO_DB" == "local" ]]; then
  echo "✘ E2E 시드를 보호된 DB '$MONGO_DB'에 넣을 수 없습니다. MONGO_DB=gole_e2e를 사용하세요." >&2
  exit 2
fi

if [[ "$REDIS_DATABASE" == "0" ]]; then
  echo "✘ E2E 세션을 사용자 세션 DB 0에 넣을 수 없습니다. REDIS_DATABASE=15를 사용하세요." >&2
  exit 2
fi

# 테스트가 localStorage에 넣는 accountId와 같아야 한다. 클라이언트는 "내 매물" 판정처럼
# 화면 로직에 이 값을 쓰고, 서버는 토큰으로 신원을 정한다. 둘이 어긋나면 자기거래 금지
# 단정이 엉뚱하게 실패한다.
SELLER_ID="e2e-seller"
BUYER_ID="e2e-buyer"
SELLER_TOKEN="e2e-seller-session-token"
BUYER_TOKEN="e2e-buyer-session-token"

# 실제로 검증되지 않는 자리채움 해시. 이 계정들은 비밀번호 로그인을 하지 않는다.
PLACEHOLDER_HASH='$2a$10$e2eE2eE2eE2eE2eE2eE2eOa1b2c3d4e5f6g7h8i9j0k1l2m3n4o5p'

echo "▶ E2E 계정 시드 (mongo=$MONGO_SERVICE db=$MONGO_DB redis-db=$REDIS_DATABASE)"

docker compose exec -T "$MONGO_SERVICE" mongosh --quiet "$MONGO_DB" <<MONGO
const accounts = [
  { _id: "$SELLER_ID", email: "e2e-seller@gole.test" },
  { _id: "$BUYER_ID",  email: "e2e-buyer@gole.test"  },
];
const conflicting = db.accounts.find({
  email: { \$in: accounts.map((a) => a.email) },
  _id: { \$nin: accounts.map((a) => a._id) },
}).toArray();
if (conflicting.length > 0) {
  throw new Error("E2E 이메일과 충돌하는 계정이 있습니다. 삭제하지 않고 중단합니다: "
    + conflicting.map((a) => a._id).join(", "));
}
for (const a of accounts) {
  db.accounts.updateOne(
    { _id: a._id },
    {
      \$set: {
        email: a.email,
        passwordHash: "$PLACEHOLDER_HASH",
        status: "VERIFIED",
        role: "USER",
        verificationFailedAttempts: 0,
        failedAttempts: 0,
      },
      \$unset: { verificationCode: "", verificationCodeIssuedAt: "", lockedUntil: "", suspendedReason: "" },
    },
    { upsert: true },
  );
}
print("accounts: " + db.accounts.countDocuments({ _id: { \$in: accounts.map((a) => a._id) } }) + "/2");

// 결제 E2E는 운영 기본값(Stage 1)을 암묵적으로 열지 않는다. e2e 프로필의 준비 상태와
// 수동 정산 계약 플래그가 함께 맞을 때만 이 명시적 Stage 2 설정이 실행된다.
db.launch_config.updateOne(
  { _id: "launch" },
  {
    \$set: {
      stage: 2,
      overrides: {},
      readiness: {
        businessDisclosure: true,
        termsPrivacy: true,
        paymentFlow: true,
      },
      updatedAt: new Date(),
      updatedBy: "system:e2e-seed",
    },
    \$setOnInsert: { version: NumberLong("0") },
  },
  { upsert: true },
);
print("launch stage: " + db.launch_config.findOne({ _id: "launch" }).stage);
MONGO

# TTL은 넉넉히 준다(2시간). E2E 한 회차보다 길고, 남아도 다음 회차가 같은 키를 덮어쓴다.
# 로컬 macOS에서는 네이티브 Redis와 Docker Redis가 동시에 존재할 수 있으므로 API가 보는
# 호스트 엔드포인트를 우선한다. redis-cli가 없는 CI 러너에서는 Compose 컨테이너로 폴백한다.
if command -v redis-cli >/dev/null 2>&1 \
  && redis-cli -h "$REDIS_HOST" -p "$REDIS_PORT" -n "$REDIS_DATABASE" PING 2>/dev/null \
    | grep -qx PONG; then
  redis_cli=(redis-cli -h "$REDIS_HOST" -p "$REDIS_PORT" -n "$REDIS_DATABASE")
else
  redis_cli=(docker compose exec -T "$REDIS_SERVICE" redis-cli -n "$REDIS_DATABASE")
fi

"${redis_cli[@]}" SET "gole:session:$SELLER_TOKEN" "$SELLER_ID|USER" EX 7200 > /dev/null
"${redis_cli[@]}" SET "gole:session:$BUYER_TOKEN" "$BUYER_ID|USER" EX 7200 > /dev/null

echo "✔ 계정 2개와 세션 2개 준비 완료"
