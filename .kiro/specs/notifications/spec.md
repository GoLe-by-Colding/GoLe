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
- [ ] B1 domain + ports + service
- [ ] B2 Mongo adapter + controller
- [ ] B3 order 트리거(SellerNotifierPort + adapter + place 연동)
- [ ] B4 NotificationServiceTest
- [ ] F1 entities/notification
- [ ] F2 widgets/notification-bell + 헤더
- [ ] F3 views/notifications + 라우트
- [ ] D1 빌드·배포·스모크

## 보안/후속
- 현재 사용자 식별은 path의 userId(기존 컨텍스트와 동일 관례). 후속: 세션 토큰 기반 본인 검증으로 강화.
- 추가 트리거(댓글→작성자, 팔로우→셀러, 결제완료/환불) 및 실시간(SSE/WebSocket) 후속.
