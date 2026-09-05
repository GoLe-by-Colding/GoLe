/**
 * 온보딩 배포 이전 가입자 면제 표시 (onboarding D6, R10)
 *
 * 온보딩 스펙 배포 시점에 이미 존재하던 계정에는 닉네임·전화인증·관심태그·개인정보 동의가
 * 없다. 그 상태로 게이트(`@RequiresOnboarding`)를 켜면 기존 사용자가 매물 등록·구매·채팅
 * 시작에서 한꺼번에 403을 맞는다. 이 스크립트는 그런 계정에 `legacyExempt: true`를 찍어
 * 하드 게이트에서 빼 준다(프론트는 닫을 수 있는 배너만 노출한다).
 *
 * `legacyExempt`는 파생값이 아니라 이 시점에 저장하는 사실이다 — 이후 사용자가 배너를 보고
 * 자발적으로 일부 단계를 완료해도 값은 바뀌지 않는다.
 *
 * ## 실행 시점 — 반드시 백엔드 배포 "직전"
 *
 * 배포 후에 돌리면 그 사이 가입한 신규 계정까지 면제되고, 너무 일찍 돌리면 실행 이후
 * 배포 전까지 가입한 계정이 면제되지 않아 게이트에 걸린다. 그래서 이 스크립트는 멱등하며,
 * 배포 직후 한 번 더 돌려도 안전하다(이미 표시된 계정은 건드리지 않는다). 다만 두 번째
 * 실행은 그 사이 가입자까지 면제하므로, 배포 직전 1회 실행을 원칙으로 한다.
 *
 * ## 사용
 *
 *   DRY=1 mongosh "$MONGODB_URI" scripts/migrate-onboarding-legacy-exempt.js   # 대상 건수만 확인
 *   mongosh "$MONGODB_URI" scripts/migrate-onboarding-legacy-exempt.js         # 실제 적용
 *
 * 운영 URI 예시:
 *   mongosh "mongodb://localhost:27017/gole?replicaSet=rs0" scripts/migrate-onboarding-legacy-exempt.js
 *
 * ## 되돌리기
 *
 *   db.accounts.updateMany({ legacyExempt: true }, { $unset: { legacyExempt: "" } })
 *
 * 되돌리면 기존 사용자 전원이 온보딩 강제 대상이 되므로, 게이트를 먼저 끄지 않고는 실행하지
 * 않는다. 이 저장소의 원칙대로 "여는" 조치는 검증하되 "내리는" 조치는 막지 않는다.
 */

const dryRun = process.env.DRY === "1";

// 이미 표시된 계정은 제외한다 — 멱등성과 대상 건수 정확도를 위해서다.
const filter = { legacyExempt: { $ne: true } };

const total = db.accounts.countDocuments({});
const targetCount = db.accounts.countDocuments(filter);

print(`전체 계정 ${total}건 중 면제 대상 ${targetCount}건 (이미 표시됨: ${total - targetCount}건)`);

if (targetCount === 0) {
  print("표시할 계정이 없습니다.");
} else if (dryRun) {
  db.accounts
    .find(filter, { _id: 1, email: 1 })
    .limit(10)
    .forEach((doc) => print(`  - ${doc._id} ${doc.email ?? ""}`));
  print("\nDRY=1 이므로 변경하지 않았습니다.");
} else {
  const result = db.accounts.updateMany(filter, { $set: { legacyExempt: true } });
  print(`\n면제 표시 완료: ${result.modifiedCount}건`);
  print("이제 백엔드를 배포해도 기존 사용자는 매물등록·구매·채팅에서 차단되지 않습니다.");
}
