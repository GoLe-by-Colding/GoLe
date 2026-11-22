# 사용자 차단 (user-block)

## 왜 필요한가

지금 사용자가 괴롭힘을 당하면 할 수 있는 일은 신고 하나다. 신고는 운영자가 볼 때까지 시간이
걸리고, 그동안 상대는 계속 댓글을 달고 대화를 걸 수 있다. 차단 저장소와 API는 이미 있다
(`chat` 컨텍스트의 `chat_blocks`, `POST/DELETE /api/v1/chat/social/blocks/{accountId}`).
하지만 실제로 막는 곳은 대화뿐이다. 1:1·매물 대화의 새 방과 메시지 전송, 그리고 그 검사를 거치는 가격 제안이다.
차단 버튼도 대화방 머리(`views/chat-list`)에만 있다. 커뮤니티에서 괴롭히는 사람을 막으려면, 그 사람과 대화방을 열어야만
차단할 수 있다. 차단한 뒤에도 그 사람의 글과 댓글은 계속 보이고, 그 사람은 내 글에 계속 댓글을 달 수 있다.

앱 배포에도 걸린다. Apple App Review Guidelines 1.2(User-Generated Content)는 사용자 콘텐츠 앱에
"The ability to block abusive users from the service"를 요구한다. Google Play 사용자 제작 콘텐츠
정책도 공개 UGC 앱과 1:1 메시지 기능에 앱 안의 사용자 차단을 요구한다(둘 다 2026-11-23 원문 확인).
`apps/mobile`은 웹뷰 셸이므로 웹에 붙이면 앱에도 같이 붙는다.

이 스펙은 **이미 있는 차단 관계를 커뮤니티와 팔로우까지 넓히고, 커뮤니티·셀러샵에 차단 진입점과 프로필에 관리 화면을 붙인다.**
매물 대화·가격 제안의 차단은 이미 동작하므로 회귀 테스트로 고정만 한다. 반복 위반자 제재처럼 운영자가 거는 제한, 매물 목록 숨김,
입찰 매칭은 범위 밖이며 D3·후속에서 다룬다.

## 결정

### D1. 차단은 `chat`의 기존 `chat_blocks`를 그대로 쓰고, 다른 컨텍스트에는 조회 유스케이스로만 연다

저장소·API·자기 차단 거부(`CHAT_SELF_BLOCK`)·회원 탈퇴 정리(`MongoChatAccountErasureAdapter`)가 이미
`chat`에 있다. 차단을 `account`나 새 컨텍스트로 옮기면 컬렉션 이전과 API 경로 변경이 필요하지만, 얻는 동작 차이가 없다.
이름과 실제 역할이 어긋나는 것은 받아들이고 이전은 T4로 미룬다.

다른 컨텍스트는 `chat`의 인바운드 포트 `BlockRelationQueryUseCase`(`blockedBetween`·`blockedCounterparts`)만
본다. 이 포트는 차단 저장소만 의존하는 **별도 서비스**(`BlockRelationQueryService`)가 구현한다.
`SocialChatService`가 구현하면 순환이 생긴다. 차단하면서 팔로우를 끊으려면(D5) `chat` → `discovery`를 부르고,
팔로우할 때 차단을 확인하려면 `discovery` → `chat`을 불러 빈 순환 참조가 된다.

### D2. 차단은 양방향이다 — 누가 걸었든 서로의 글·댓글이 안 보이고 반응·대화가 막힌다

한쪽만 막으면 차단당한 사람은 차단한 사람의 글에 계속 댓글을 달 수 있다. 차단한 본인 눈에만 안 보일 뿐,
다른 사용자에게는 괴롭힘이 그대로 보인다. 기존 채팅 차단도 방향을 묻지 않는다
(`ChatBlockRepositoryPort.blockedBetween` 주석). 같은 단어가 화면마다 다르게 동작하지 않게 맞춘다.

차단 관리 목록에는 **내가 건 차단만** 보인다(`blockedTargets`, 기존 규칙). 상대가 나를 차단했다는 사실은
보여 주지 않는다.

### D3. 차단은 대화·반응을 막고, 매물은 숨기지 않는다

막는 것은 다음과 같다.
- 커뮤니티 글·댓글의 노출
- 댓글 작성과 좋아요
- 팔로우
- 새 매물 대화방 개설과 기존 매물 대화방의 메시지 전송 (이미 동작한다. `SocialChatService.requireCanStartPrivateConversation`·`requireSendable`)
- 가격 제안 (이미 동작한다. `OfferChatPort.requireSendableListingRoom`이 같은 `requireSendable`을 거쳐 `CHAT_BLOCKED`로 막힌다)

매물 검색·세트 페이지의 목록은 숨기지 않는다. 숨기면 같은 검색 결과가 사용자마다 달라진다. 그러면 시세 근거·세트별 집계와
캐시를 보는 사람마다 나눠야 하는데, 그만큼 얻는 보호가 없다. 대화 경로가 막히면 상대와 엮일 방법이 없다.

**주문 진행·분쟁은 막지 않는다.** 이미 돈이 걸린 거래를 차단 한 번으로 멈추면 환불·배송 확인이
막힌 채 남는다. 주문 화면의 조치와 운영 분쟁 경로는 차단과 상관없이 유지한다. 새 대화를 열 수 없는 대신
기존 방의 읽기는 남겨 거래 기록을 볼 수 있게 한다.

### D4. 피드는 페이지를 자르기 전에 서버에서 거른다

화면에서 이미 받은 카드만 거르면 차단 상대의 글이 많은 페이지는 6개가 아니라 1~2개만 보인다. 커서도
그 글을 기준으로 이어져 중복·누락이 생긴다. `community-engagement` D5가 검색을 서버로 올린 것과 같은
이유다.

홈이 쓰는 배열 피드(`GET /posts`)·공개 페이지 피드·팔로잉 피드·작성자별·세트별 목록 모두 `authorId $nin <차단 상대>` 조건을 다른 조건과 함께 Mongo에서
먼저 걸고 같은 키셋 커서로 자른다. 비로그인 조회는 거를 대상이 없으므로 지금과 같다.

### D5. 차단하면 양쪽 팔로우를 끊고, 차단 중에는 다시 팔로우할 수 없다

팔로우를 남겨 두면 차단 상대에게 `NEW_LISTING` 알림이 계속 가고, 팔로워 목록에도 남는다. 피드 필터만으로는
알림 경로가 막히지 않는다. 차단 저장과 양방향 언팔로우는 한 트랜잭션이다(rs0). 언팔로우가 실패하면 차단도 되돌린다.
차단을 풀어도 팔로우는 복원하지 않는다. 누가 누구를 팔로우했었는지 기억하려면 또 다른 상태가 필요하다.

### D6. 이미 남은 것은 지우지 않고 보이지 않게만 한다

차단 전에 달린 댓글과 이미 받은 알림은 저장소에서 지우지 않는다. 댓글은 목록에서 거르기만 하므로, 차단을 풀면
다시 보인다. 지운 데이터를 해제 때 되살릴 근거가 없고, 신고·분쟁 증거도 사라진다(`community-trust` D2와
같은 원칙). 지난 알림 목록은 이번에 거르지 않는다(T3).

### D7. 차단 진입점은 사람이 보이는 화면마다 두고, 관리는 프로필 한 곳에 모은다

새 진입점은 커뮤니티 글 작성자, 댓글 작성자, 셀러샵이다. 대화방 머리의 기존 차단·해제 토글(사유 입력 포함)은 그대로 둔다.
새 진입점은 앱 공통 확인 모달 한 번으로 끝내고 사유는 묻지 않는다. API는 사유를 받지만(200자), 사유 입력은 신고의 몫이다.
모달에는 "운영자 확인이 필요하면 신고하세요"를 안내한다.

해제는 `/profile/blocks`에서 한다. 커뮤니티·셀러샵에 해제 버튼을 두면 차단 상대의 콘텐츠가 이미 숨겨진 상태라
누를 자리가 없다. 대화방의 기존 해제 토글은 그 방이 이미 열려 있으므로 유지한다.

## 요구사항 (EARS)

- [ ] R1. WHEN 로그인 사용자가 다른 사용자를 차단하면, 시스템은 차단 관계를 저장하고 양쪽 팔로우를 같은 트랜잭션에서 끊어야 한다. 자기 자신 차단은 400 `CHAT_SELF_BLOCK`으로 거부해야 한다(기존 규칙).
- [ ] R2. WHEN 차단 관계가 있으면(방향 무관), 시스템은 배열 피드(`GET /posts`)·공개 페이지 피드·팔로잉 피드·작성자별·세트별 글 목록에서 상대의 글을 페이지를 자르기 전에 제외해야 한다.
- [ ] R3. WHEN 차단 관계인 상대의 글 상세를 열면, 시스템은 403 `COMMUNITY_BLOCKED`를 반환해야 한다.
- [ ] R4. WHEN 댓글 목록을 조회하면, 시스템은 차단 관계인 사용자의 댓글을 제외해야 한다.
- [ ] R5. IF 차단 관계인 상대의 글에 댓글이나 좋아요를 시도하면, 시스템은 403 `COMMUNITY_BLOCKED`로 거부해야 한다.
- [ ] R6. IF 차단 관계인 상대를 팔로우하려 하면, 시스템은 403 `FOLLOW_BLOCKED`로 거부해야 한다.
- [ ] R7. IF 차단 관계인 판매자의 매물에 새 대화방을 열거나 기존 매물 대화방에 메시지를 보내려 하면, 시스템은 403 `CHAT_BLOCKED`로 거부해야 한다. 기존 방의 읽기는 허용해야 한다. (기존 동작. 회귀 테스트로 고정한다)
- [ ] R8. IF 차단 관계인 상대와의 매물 대화방에서 가격 제안을 하려 하면, 시스템은 403 `CHAT_BLOCKED`로 거부해야 한다. (기존 동작. `OfferServiceTest`가 이미 고정한다)
- [ ] R9. 차단 관계는 주문 상태 전이·배송 확인·분쟁 접수를 막지 않아야 한다.
- [ ] R10. WHEN 차단을 해제하면, 시스템은 숨겼던 글·댓글을 다시 보이게 해야 하고 팔로우는 복원하지 않아야 한다.
- [ ] R11. 사용자는 글 작성자·댓글 작성자·셀러샵에서 확인 모달을 거쳐 차단할 수 있어야 하며(대화방은 기존 토글 유지), 본인에게는 차단 버튼이 보이지 않아야 한다.
- [ ] R12. `/profile/blocks`는 내가 건 차단만 닉네임으로 보여 주고 해제할 수 있어야 한다. 상대가 나를 차단한 사실은 노출하지 않아야 한다.
- [ ] R13. 비로그인 공개 조회의 응답은 지금과 같아야 한다.

## 설계

- **`chat`** (차단 소유)
  - `application.port.in.BlockRelationQueryUseCase`: `boolean blockedBetween(String a, String b)`,
    `List<String> blockedCounterparts(String accountId)`.
  - `application.service.BlockRelationQueryService`: 위 포트를 구현한다. 의존은 `ChatBlockRepositoryPort`뿐이다(D1).
  - `SocialChatService.block`: 저장 뒤 새 아웃바운드 포트 `FollowCutPort.cutBoth(a, b)`를 같은 트랜잭션에서 부른다.
    `adapter.out.discovery.DiscoveryFollowCutAdapter`가 `FollowSellerUseCase.unfollow`를 양방향으로 위임한다.
  - `ListingChatRoomService.open`(새 방)과 매물 방 메시지 전송은 이미 `SocialChatService`의 차단 검사를 거친다. 코드는 바꾸지 않고
    `ListingChatRoomServiceTest`에 회귀 테스트만 더한다.
- **`community`**
  - `application.port.out.BlockedAuthorQueryPort` + `adapter.out.chat.ChatBlockedAuthorQueryAdapter` → `BlockRelationQueryUseCase`.
  - `PostRepositoryPort.findPublishedPage`·`findPublishedByAuthorIdsRecentFirst`: `excludedAuthorIds`를 받아
    `authorId $nin`을 건다(D4).
  - `CommunityService`: 상세·댓글 작성·좋아요에서 `blockedBetween` 확인 → 403 `COMMUNITY_BLOCKED`.
    댓글 목록은 `blockedCounterparts`로 거른다.
- **`discovery`**: `FollowSellerUseCase.follow`에 아웃바운드 포트 `FollowBlockCheckPort`
  (`adapter.out.chat` → `BlockRelationQueryUseCase`) 확인 → 403 `FOLLOW_BLOCKED`.
- **`offer`**: 변경 없다. `OfferChatAdapter` → `ListingRoomAccessUseCase.requireSendableListingRoom` → `requireSendable`의
  차단 검사로 이미 막힌다.
- **웹 (FSD)**
  - `features/block-user`: `BlockUserButton`(확인 모달). API는 `@gole/core/chat`의 기존 `blockChatUser`·`unblockChatUser`·
    `fetchBlockedChatUserIds`를 쓴다(새 API 함수 없음).
  - `views/blocked-users` + `app/(main)/profile/blocks/page.tsx`: 목록(`useDisplayNames`)·해제.
  - 진입점: `views/community-post`(작성자·댓글), `views/seller-shop`. 대화방 머리는 기존 토글을 유지한다. 403 `COMMUNITY_BLOCKED` 상세는
    "차단한(또는 차단된) 사용자의 글이라 볼 수 없어요"로 안내하고 원문을 그리지 않는다.
- 새 컬렉션은 없다. `chat_blocks.block_pair_idx`(blockerId, blockedId, unique)를 그대로 쓴다.
  이 인덱스는 `blockerId`로 시작하므로, `blockedCounterparts`의 역방향(`blockedId = 나`) 조회는 지금 인덱스를 못 탄다.
  이제 피드 요청마다 불리므로 `ChatBlockDocument`에 `blockedId` 단일 인덱스를 추가한다.

## 수용 기준 (테스트로 고정할 것)

- A가 B를 차단하면 `chat_blocks`에 1건이 생기고, A→B·B→A 팔로우가 모두 사라진다. 언팔로우가 실패하면 차단도 저장되지 않는다.
- A가 B를 차단한 뒤 B의 글이 12건, 다른 글이 12건일 때, A의 첫 페이지는 B의 글 없이 6건이고 다음 커서로 중복 없이 이어진다.
  B가 A의 피드를 볼 때도 A의 글이 없다(양방향).
- 차단 관계에서 상세 조회·댓글·좋아요는 403 `COMMUNITY_BLOCKED`. 댓글 목록에는 상대 댓글이 없다.
- 차단 관계에서 팔로우는 403 `FOLLOW_BLOCKED`, 새 매물 방·매물 방 메시지·가격 제안은 403 `CHAT_BLOCKED`다.
  기존 매물 방 메시지 조회는 200이다.
- 차단 관계여도 진행 중인 주문의 배송 확인·구매 확정·분쟁 접수는 성공한다.
- 해제 후 숨겼던 글·댓글이 다시 보이고, 팔로우는 0건 그대로다.
- `/profile/blocks`는 A에게 B를 보여 주고, B에게는 아무것도 보여 주지 않는다.
- 로그인 사용자의 배열 피드 `GET /posts`(홈)에도 차단 상대의 글이 없다.
- 비로그인 `GET /posts/page` 응답은 차단 데이터와 무관하게 변경 전과 같다.
- E2E: 글 상세에서 작성자 차단 → 피드에서 사라짐 → `/profile/blocks`에서 해제 → 다시 보임.

## 범위 밖 / 후속

- **T1. 반복 위반자 제재.** 운영자가 거는 제한이며 `community-trust` 후속이다. 사용자 차단과 상태를 섞지 않는다.
- **T2. 차단 수 상한.** `authorId $nin`의 목록이 요청마다 실린다. 계정당 수천 건까지는 문제없다고 보고 상한을
  두지 않는다. 차단 수 분포가 쌓이면 상한(예: 1,000)과 `CHAT_BLOCK_LIMIT`을 정한다.
- **T3. 지난 알림 필터.** 차단 전에 받은 댓글·좋아요 알림은 남는다. 알림 목록 조회에 같은 필터를 걸면 된다.
- **T4. 차단 컨텍스트 이전.** 차단이 채팅 밖에서 더 많이 쓰이면 `account`나 독립 컨텍스트로 옮긴다.
  다른 컨텍스트는 `BlockRelationQueryUseCase`만 보므로 어댑터의 위임 대상만 바뀐다.
- **T5. 입찰 매칭.** `buy-bids`의 자동 매칭 알림(`BID_LISTING_MATCHED`)은 상대를 고르는 행위가 아니어서 이번에는 막지 않는다.
- **T6. 좋아요의 문서 전체 덮어쓰기.** R5는 좋아요 앞에 차단 검사만 더한다. 좋아요가 게시글 문서 전체를 다시 저장하는
  문제(동시 쓰기 유실, 삭제 글 부활 가능성)는 이 스펙에서 고치지 않는다 — [#311](https://github.com/GoLe-by-Colding/GoLe/issues/311).

## 관련

- `chat` — `ChatBlockRepositoryPort`·`MongoChatBlockAdapter`·`SocialChatService.block/ensureNotBlocked`·`SocialChatController`
  `/api/v1/chat/social/blocks`. 탈퇴 정리는 `MongoChatAccountErasureAdapter`.
- `order/adapter/out/listing/ListingReservationAdapter` — 다른 컨텍스트 UseCase로 위임하는 아웃바운드 어댑터의 표준 예시.
  D1의 세 어댑터가 이 모양을 따른다.
- `community-engagement` D5 — 페이지를 자르기 전에 서버에서 거르는 원칙(D4).
- `community-trust` D2·D6 — 지우지 않고 감추는 원칙(D6). 신고는 `features/report-content`를 그대로 쓴다.
- `discovery` — `FollowSellerUseCase`. `community/adapter/out/discovery/DiscoveryFollowingAuthorQueryAdapter`가 팔로잉 피드의 작성자 목록을 준다.
- 외부 근거: [Apple App Review Guidelines 1.2](https://developer.apple.com/app-store/review/guidelines/),
  [Google Play 사용자 제작 콘텐츠 정책](https://support.google.com/googleplay/android-developer/answer/9876937).
