# Notifications — Spec

> 사용자에게 거래/활동 알림을 제공한다. 새 `notification` 바운디드 컨텍스트(헥사고날) + 헤더 벨(안읽음 수) + 알림 페이지. 첫 트리거: 내 매물에 주문이 들어오면 셀러에게 알림.

## Requirements (EARS)
- N1 WHEN 다른 컨텍스트가 알림 발생을 요청하면(예: 주문 생성), 시스템은 수신자·타입·메시지·링크로 알림을 `읽지 않음` 상태로 저장해야 한다.
- N2 WHEN 사용자가 자신의 알림 목록을 요청하면, 시스템은 최신순으로 반환해야 한다.
- N3 WHEN 사용자가 안읽음 수를 요청하면, 시스템은 읽지 않은 알림 개수를 반환해야 한다.
- N4 WHEN 사용자가 특정 알림을 읽음 처리하면, 시스템은 해당 알림을 `읽음`으로 전이해야 한다(소유자 검증; 타인 알림은 거부/무시).
- N5 WHEN 사용자가 전체 읽음을 요청하면, 시스템은 해당 사용자의 모든 알림을 읽음 처리해야 한다.
- N6 WHEN 구매자가 `ACTIVE` 매물에 주문을 생성하면, 시스템은 셀러에게 `ORDER_PLACED` 알림을 생성해야 한다(주문 상세로 링크). 알림 실패는 주문 생성을 막지 않아야 한다(best-effort).
- N7 프론트는 로그인 시 헤더에 안읽음 수 배지를 표시(주기 폴링)하고, `/notifications`에서 목록·전체읽음을 제공해야 한다.
- N8 WHEN 셀러가 새 매물을 등록하면, 시스템은 그 셀러를 팔로우한 사용자 모두에게
  `NEW_LISTING` 알림과 매물 상세 딥링크를 보내야 한다. 한 수신자의 알림 실패나 팔로워 조회 실패는
  매물 등록 및 다른 수신자의 알림을 막지 않아야 한다.
- N9 WHEN 다른 사용자가 내 커뮤니티 글을 좋아하면 `POST_LIKED` 알림을 보내야 한다. 자기 글 반응은
  알리지 않고, 같은 사용자·게시글 조합은 좋아요 취소와 재등록을 반복해도 알림 한 건만 유지해야 한다.

## Design
- 백엔드 `com.gole.api.notification` (헥사고날):
  - domain `Notification`(id, recipientId, type, message, link?, read, createdAt) + `NotificationType`(ORDER_PLACED, ORDER_PAID, COMMENT, FOLLOW, GENERAL).
  - port-in `NotifyUseCase.notify(NotifyCommand)`, `GetNotificationsUseCase`(list/unreadCount/markRead/markAllRead).
  - port-out `NotificationRepositoryPort`, `NotificationIdGeneratorPort`.
  - service `NotificationService`(둘 다 구현).
  - adapter-out Mongo(`NotificationDocument`/repo/`NotificationPersistenceAdapter`) + `UuidNotificationIdGenerator`.
  - adapter-in `NotificationController` `/api/v1/users/{userId}/notifications`(GET 목록, `POST /read-all`), `.../notifications/{id}/read`(POST), `.../notifications/unread-count`(GET).
- 트리거(order→notification, NFR-3 인바운드 포트 의존):
  - order 아웃바운드 포트 `SellerNotifierPort` + 어댑터 `NotificationSellerNotifierAdapter`(→ `NotifyUseCase`). `OrderService.place`에서 저장 후 호출, 예외는 어댑터가 흡수(best-effort).
- 프론트(FSD):
  - `entities/notification`: 타입 + api(list/unreadCount/markRead/markAllRead).
  - `widgets/notification-bell`: 안읽음 배지(30s 폴링), `/notifications` 링크.
  - `views/notifications` + `/notifications` 라우트.
  - 헤더에 벨 노출(로그인 시).

## Tasks
- [x] B1 domain + ports + service
- [x] B2 Mongo adapter + controller
- [x] B3 order 트리거(SellerNotifierPort + adapter + place 연동)
- [x] B4 NotificationServiceTest
- [x] F1 entities/notification
- [x] F2 features/notification-bell + 헤더
- [x] F3 views/notifications + 라우트
- [x] B5 팔로우한 셀러의 신규 매물 트리거(`NEW_LISTING`, 매물 상세 딥링크, best-effort 격리)
- [x] D1 빌드·배포·스모크
- [x] B6 커뮤니티 좋아요 알림 + 수신자·사건 멱등 키

## 알림 멱등 정책

반복 가능한 사건은 `recipientId + deduplicationKey` partial unique 인덱스로 한 건에 수렴한다. 멱등 키가
없는 주문·배송 등 기존 사건은 종전처럼 각각 저장한다. 커뮤니티 좋아요 키는
`community-like:{postId}:{actorId}`이며, 이미 읽은 알림을 재반응으로 다시 안 읽음 처리하지 않는다.
서비스의 사전 조회와 Mongo unique 인덱스를 함께 사용해 동시 호출 경쟁에서도 중복이 생기지 않게 한다.

2026-09-03 로컬 런타임에서 관리자 사용자가 `user-collector`의 실제 글에 좋아요→취소→재등록→취소를
수행했다. 글의 `likedBy`는 원래 빈 상태로 복원됐고, `POST_LIKED` 알림은 같은 멱등 키로 정확히 1건만
남았다. Mongo의 partial unique 인덱스 생성도 함께 확인했다.

## 보안/후속
- [x] URL 호환성을 위해 path의 `userId`는 유지하지만 컨트롤러는 값을 신뢰하지 않고 `AuthenticatedUser.id(http)`의 세션 계정만 사용한다. 타인 ID를 넣어도 본인 알림만 조회·변경된다.
- 댓글→작성자, 팔로우→셀러, 주문·배송·분쟁, 직거래 확인, 팔로우 셀러 신규 매물까지 구현됨.
- 커뮤니티 좋아요→작성자 알림은 자기 반응 제외와 취소·재등록 중복 방지까지 구현됨.
- 위시리스트 가격 변동은 매물 가격 수정 유스케이스가 도입된 뒤 후속으로 연결한다.
- 실시간(SSE/WebSocket) 후속.
