# 알림 수신 설정 — Spec

> `set-watch-alerts/spec.md`가 "범위에서 뺀 것 — 알림 수신 설정(끄기)"으로 미뤄둔 항목. 알림 종류가
> 단종·관심 세트 새 매물에 이어 가격 인하·가격 제안·입찰·부품 요청으로 늘어나는 시점에 붙인다.

## 왜 필요한가

- 끌 수단이 없으면 사용자는 알림이 귀찮아지는 순간 **앱 알림을 통째로** 끈다. 그러면 주문·배송 같은
  꼭 필요한 알림까지 잃는다.
- 종류 단위 토글은 종류가 늘 때마다 화면이 길어진다. **분류 4개**로 묶는다.

## Requirements (EARS)

- P1. 분류는 `NotificationCategory`: `TRADE`(거래 진행 — 끌 수 없음), `OFFER`(가격 제안·입찰),
  `WATCH`(찜·관심 세트), `COMMUNITY`(커뮤니티·부품 요청). 모든 `NotificationType`은 분류를 하나 갖는다
  (enum 생성자 인자 — 빠뜨리면 컴파일 실패). 기본값은 전부 켜짐.
- P2. `GET /api/v1/users/{userId}/notification-preferences` → `{categories: [{key, label, enabled,
  mandatory}]}`. 순서는 enum 순서. 경로의 `userId`는 무시하고 세션 계정을 쓴다(기존 알림 API와 같다).
- P3. `PUT` 같은 경로, 본문 `{enabled: {"offer": false, "watch": true, ...}}`. 빠진 키는 그대로 둔다.
  모르는 키는 400 `NOTIFICATION_CATEGORY_UNKNOWN`, `trade: false`는 400
  `NOTIFICATION_CATEGORY_MANDATORY`. 응답은 P2와 같다.
- P4. WHEN 수신자가 끈 분류의 알림이 발생하면, 시스템은 **저장도 푸시도 하지 않는다**
  (`NotificationService.notify`에서 `saveOnce` 전에 거른다). 반환값은 `null`.
- P5. 설정 조회가 실패하면 **보낸다**(fail-open, 경고 로그). 알림을 잃는 쪽보다 한 번 더 울리는 쪽이 낫다.
- P6. 관심 테마 알림톡도 `WATCH`를 따른다. 팬아웃에서 쿼터를 쓰기 전에 거르고, 발송 직전 재확인에서
  `RECIPIENT_OPTED_OUT` 사유로 건너뛴다. 마케팅 수신 동의는 그대로 별개 조건이다.
- P7. 계정 삭제 시 설정 문서를 지운다.

## Design

- notification 컨텍스트에 둔다. 집행 지점(`NotificationService.notify`, 알림톡 워커)이 모두 여기 있고,
  account가 `NotificationType`을 알 필요가 없다. 기기 토큰이 여기 있는 것과 같은 이유다.
- domain `NotificationPreferences(accountId, Set<NotificationCategory> disabled, updatedAt)` —
  `allows(NotificationType)`, `update(Map<NotificationCategory,Boolean>)`(필수 분류 거부).
- port-in `ManageNotificationPreferencesUseCase.get(accountId)`·`update(accountId, changes)`.
  port-out `NotificationPreferenceRepositoryPort.find(accountId)`·`save(...)`.
- persistence `notification_preferences` — `_id = accountId`, `disabledCategories: [..]`, `updatedAt`.
  문서가 없으면 기본값(전부 켜짐).
- web `NotificationPreferenceController` — `/api/v1/users/**`는 이미 세션이 필요한 경로다.

### 프론트

- core `notification`: `NotificationPreferenceCategory` 타입, `fetchNotificationPreferences(userId)`,
  `updateNotificationPreferences(userId, enabled)`.
- `/profile/notifications` 화면 — 분류별 스위치, 거래 진행은 켜진 채 잠김 + 이유 한 줄. 프로필 정보 탭과
  `/notifications` 머리에 "알림 설정" 링크. 앱은 웹뷰라 같은 화면을 쓴다.

## 범위에서 뺀 것

- 채널별(앱 내/푸시) 분리 설정, 방해 금지 시간대.
- 종류 단위 세부 설정.

## Tasks

- [ ] B1 `NotificationCategory`·`NotificationType.category()` (공유 계약으로 먼저 커밋됨)
- [ ] B2 domain·port·service·persistence·web + 단위 테스트
- [ ] B3 `NotificationService.notify` 집행 + 알림톡 팬아웃·발송 재확인
- [ ] B4 계정 삭제 정리
- [ ] F1 core API·타입, 설정 화면, 진입 링크
- [ ] V1 로컬 실검증: 커뮤니티 끔 → 댓글 알림 미생성, 거래 진행 끔 요청 400
