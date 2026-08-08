/**
 * 레거시 잠금 → SUSPENDED 전환 (admin-console 설계 §5)
 *
 * 이전 관리자 UI는 계정을 "잠글" 때 `lockedUntil`을 9999년으로 세팅했다. 이 방식은
 * 이미 발급된 세션 토큰(TTL 7일)을 폐기하지 않아 잠긴 사용자가 계속 활동할 수 있었다.
 * 새 모델은 `status = SUSPENDED` + 세션 폐기다. 이 스크립트는 남아 있는 레거시 문서를 옮긴다.
 *
 * 사용:
 *   mongosh "mongodb://localhost:27017/gole?replicaSet=rs0" scripts/migrate-legacy-locks.js
 *   DRY=1 mongosh ... scripts/migrate-legacy-locks.js   # 변경 없이 대상만 출력
 *
 * 주의: Redis 세션은 별도로 폐기해야 한다(아래 출력된 accountId 목록 참고).
 *       redis-cli --scan --pattern 'gole:session:acct:<id>' 로 확인 후 삭제하거나,
 *       해당 계정을 콘솔에서 한 번 더 정지/해제하면 새 경로가 정리한다.
 */

const LEGACY_THRESHOLD = new Date("9000-01-01T00:00:00Z");
const dryRun = process.env.DRY === "1";

const targets = db.accounts
  .find({ lockedUntil: { $gte: LEGACY_THRESHOLD } }, { _id: 1, email: 1, status: 1 })
  .toArray();

if (targets.length === 0) {
  print("전환 대상 없음. (레거시 잠금 계정이 없습니다)");
} else {
  print(`전환 대상 ${targets.length}건:`);
  targets.forEach((doc) => print(`  - ${doc._id} ${doc.email?.address ?? ""} (${doc.status})`));

  if (dryRun) {
    print("\nDRY=1 이므로 변경하지 않았습니다.");
  } else {
    const result = db.accounts.updateMany(
      { lockedUntil: { $gte: LEGACY_THRESHOLD } },
      {
        $set: { status: "SUSPENDED", suspendedReason: "마이그레이션: 레거시 잠금" },
        $unset: { lockedUntil: "" },
      },
    );
    print(`\n전환 완료: ${result.modifiedCount}건`);
    print("Redis 세션 폐기가 필요합니다 — 위 accountId 목록을 참고하세요.");
  }
}
