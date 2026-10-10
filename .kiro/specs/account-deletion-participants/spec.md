# 계정 탈퇴 참여 포트 (account-deletion-participants)

## 왜 필요한가

회원 탈퇴의 연계 파기(`MongoAccountDeletionAdapter.complete`)가 account 어댑터 하나에서 다른 컨텍스트
14곳의 컬렉션 26개를 **이름 문자열로 직접** 읽고 지우고 고친다. 차단 사유 판정도 주문·정산·신고의 상태
문자열(`"FUNDS_HELD"`, `"PAID"`, `"PENDING"`)을 account 가 직접 비교한다.

`HexagonalArchitectureTest`는 클래스 의존만 봐서 이걸 잡지 못한다(볼트 `08_개선과제` B-P1). 그 결과
컬렉션·필드·상태를 바꾸는 컨텍스트는 account 를 모른 채 탈퇴 파기를 조용히 깨뜨릴 수 있다. 개인정보 파기
흐름이라 빠뜨리면 남지 말아야 할 정보가 남는다.

## 결정

### D1. 각 컨텍스트가 자기 데이터의 탈퇴 처리를 인바운드 포트로 연다

컨텍스트마다 `<Ctx>AccountErasureUseCase`(port.in)를 둔다. 두 가지만 한다.

- **차단 사유 판정**(해당 컨텍스트만) — "이 계정의 무엇이 남아 있어 아직 파기하면 안 되는가"를 그
  컨텍스트 언어로 답한다(예: listing `hasPublicContent`). account 의 `AccountDeletionBlocker` 를 모른다.
- **파기·가명화** — 자기 컬렉션에서 지울 것은 지우고, 남겨야 하는 공유 기록은 익명 주체
  (`withdrawn-<UUID>`)로 바꾸고 내용을 비운다. 처리 건수를 자기 결과 record 로 돌려준다.

무엇을 지우고 무엇을 익명화하는지는 **지금 규칙을 그대로 옮긴다**(아래 표). 이 스펙은 구조를 바꾸지
보존 정책을 바꾸지 않는다.

| 컨텍스트 | 차단 판정 | 파기·가명화(카운트 키) |
|---|---|---|
| order | 진행 중 주문(`ACTIVE_ORDER`), 미지급 정산(`UNSETTLED_PAYOUT`) | 없음 — 주문·배송·정산은 법정 보존 |
| report | 처리 대기 신고(`PENDING_REPORT`) | `reports` 신고자·계정 대상 가명화, 상세 삭제 |
| chat | 문의 기록(`SUPPORT_RECORDS_REQUIRE_PURGE`), 열린 소유 그룹(`OWNED_GROUP_REQUIRES_TRANSFER`) | `chatReadCursors`·`chatBlocks`·`chatMessages` 삭제, `marketChatRooms`·`socialChatRooms`·`chatReportSnapshots` 가명화 |
| listing | 공개 매물·댓글(`PUBLIC_CONTENT_…` 일부) | `retiredListings`·`deletedListingComments` 가명화 |
| community | 공개 글·댓글(`PUBLIC_CONTENT_…` 일부) | `deletedPosts`·`hiddenComments` 가명화 |
| media | 살아 있는 미디어(`MEDIA_REQUIRES_LIFECYCLE_REVIEW`) | `revokedMediaAssets` 가명화 |
| notification | — | `notifications`·`notificationPreferences` 삭제 |
| discovery | — | `wishlistEntries`·`follows` 삭제 |
| collection | — | `collectionItems`·`collectionValueSnapshots` 삭제 |
| parts | — | `partRequests` 삭제 |
| bid | — | `bids` 삭제 |
| offer | — | `offers` 삭제 |
| review | — | `reviews` 가명화(작성 내용·답글 삭제) |
| admin | — | `adminAuditTargets` 대상 ID 를 탈퇴 영수증 ID 로, 사유 삭제 |

### D2. account 는 자기 아웃바운드 포트 하나로 이들을 부른다

`AccountLinkedRecordsPort`(account port.out)에 `blockers(accountId)`·`erase(accountId, anonymousSubject,
receiptId)` 두 메서드를 두고, `CrossContextAccountLinkedRecordsAdapter`가 위 14개 포트로 위임한다(표준
"내 포트 → 내 어댑터 → 상대 port.in"). 다른 컨텍스트의 결과 record 를 지금과 같은 카운트 키·순서의 맵으로
옮기고, 차단 사유도 지금과 같은 순서로 모은다.

`MongoAccountDeletionAdapter`는 탈퇴 원장, account 자신의 컬렉션(`policy_acceptances`·
`third_party_provision_consent_events`·`accounts`), 탈퇴 잠금 확인, 트랜잭션만 맡는다. 저장소 포트
(`AccountDeletionRepositoryPort.evaluateBlockers`·`complete`)와 서비스는 바꾸지 않는다.

### D3. 한 트랜잭션은 그대로다

`complete`는 지금처럼 `@Transactional` 안에서 돈다. 참여 컨텍스트는 자기 트랜잭션을 열지 않고 같은
`MongoTemplate` 세션에 붙는다 — 하나라도 실패하면 계정과 모든 연계 기록이 함께 되돌려진다. 기존
`AccountDeletionPersistenceIntegrationTest`의 "전체 rollback" 단언이 그대로 증명한다.

### D4. 감사 기록 가명화는 사유까지 지우는 별도 메서드로 연다

문의 대화 파기(`replaceTargetId`)는 사유를 남긴다. 계정 파기는 사유에 회원 식별 정보가 들어갈 수 있어
지금처럼 사유를 지운다. 두 규칙을 한 메서드에 섞지 않고 `PseudonymizeAdminActionTargetsUseCase`에
`replaceTargetIdAndDropReason`을 더한다.

## 요구사항

- [x] R1. 표의 14개 컨텍스트가 `<Ctx>AccountErasureUseCase`를 열고, 차단 판정·파기를 자기 어댑터에서 한다.
- [x] R2. account 는 다른 컨텍스트의 컬렉션 이름·필드·상태 문자열을 쓰지 않는다(전수 문자열 검색으로 확인).
- [x] R3. 차단 사유 목록·순서, 카운트 키·순서, 삭제/가명화 대상과 필드가 바뀌지 않는다.
- [x] R4. 계정 파기와 연계 기록 처리가 한 트랜잭션으로 함께 커밋되거나 함께 되돌려진다.
- [x] R5. 기존 `AccountDeletionPersistenceIntegrationTest`가 단언을 바꾸지 않고 통과한다(배선만 바뀜).

## 범위에서 뺀 것

- 보존 정책 변경(무엇을 지우고 무엇을 남길지). 지금 규칙을 옮기기만 한다.
- 탈퇴 오케스트레이션을 어댑터에서 서비스로 올리는 일. 저장소 포트·서비스 테스트를 다시 써야 해서 따로 한다.
- 차단 사유를 화면에서 컨텍스트별로 더 잘게 보여 주는 일.

## 관련

- `.kiro/specs/privacy-rights-operations/`(탈퇴 원장·보존 매트릭스)
- `.kiro/specs/wanted-parts/` W11, `.kiro/specs/price-offer/` O22, `.kiro/specs/buy-bids/` D11(각 컨텍스트 파기 규칙)
- 볼트 `08_개선과제/알려진 개선 과제` B-P1
