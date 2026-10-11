# 부족 부품 요청 — Spec

> 레고 차별화 3순위. "이 세트 조립하는데 3062b 검정 4개가 없다"는 레고 사용자의 가장 흔한 문제다.
> 부품 DB 없이 할 수 있는 부분 — 요청 게시와 **그 세트 보유자 매칭** — 을 먼저 연다.

## 왜 필요한가

- 소액·나눔 성격이 강해 결제보다 커뮤니티 리텐션에 기여한다.
- 컬렉션 보유 데이터(`ListSetHoldersUseCase`)가 이미 있어서, 세트를 가진 사람에게 바로 닿는다.

## Requirements (EARS)

- W1. 요청은 `setNumber`(선택), 부품 목록 1~20개 `{partNumber, colorName, quantity}`, `note`(≤500)다.
  `partNumber`는 `^[A-Za-z0-9][A-Za-z0-9.\-]{0,19}$`, `colorName` 1~30자(앞뒤 공백 제거),
  `quantity` 1~999. 위반은 400 `PART_REQUEST_INVALID`.
- W2. `POST /api/v1/part-requests` → 201 `PartRequestResponse`. `setNumber`가 주어졌는데 카탈로그에 없으면
  404 `PART_REQUEST_SET_NOT_FOUND`. 열린 요청이 10건을 넘으면 409 `PART_REQUEST_LIMIT_EXCEEDED`.
  `@RequiresOnboarding`.
- W3. `GET /api/v1/part-requests?setNumber=&status=open|closed|all&limit=` — 공개, 최신순,
  `status` 기본 `open`, `limit` 1~50 기본 20.
- W4. `GET /api/v1/part-requests/mine` — 세션 필요, 내 요청 최신순 최대 50건.
- W5. `GET /api/v1/part-requests/{id}` — 공개. 없으면 404 `PART_REQUEST_NOT_FOUND`.
- W6. `POST /api/v1/part-requests/{id}/close` — 작성자만(403 `PART_REQUEST_ACCESS_DENIED`).
  열린 요청이 아니면 409 `PART_REQUEST_NOT_OPEN`. `CLOSED`로 바꾸고 응답을 돌려준다.
- W7. `DELETE /api/v1/part-requests/{id}` — 작성자만, 삭제 후 204.
- W8. WHEN 세트가 지정된 요청이 생성되면, 시스템은 그 세트 `OWNED` 보유자(작성자 제외, 최대 100명)에게
  `PART_REQUEST_FOR_OWNED_SET`을 보낸다: `보유한 {setNumber} 세트의 부품을 찾는 요청이 있어요`,
  링크 `/parts/{id}`, 키 `part-request:{id}`. best-effort. 수신 설정 `COMMUNITY`를 따른다.
- W9. `PartRequestResponse`: `id, requesterId, setNumber(nullable), items[{partNumber, colorName,
  quantity}], note, status("open"|"closed"), createdAt, closedAt(nullable)`.
- W10. 돕기는 새 기능을 만들지 않는다 — 웹이 기존 1:1 대화(`POST /api/v1/chat/social/rooms/direct`)를
  열고 `/chat?room=`으로 보낸다.
- W11. 계정 삭제 시 그 계정의 요청을 지운다.

## Design

새 컨텍스트 `com.gole.api.parts` — 나중에 부품 카탈로그가 생기면 같은 자리로 모인다.

- domain `PartRequest`, `WantedPart`, `PartRequestStatus(OPEN, CLOSED)`.
- port-in `CreatePartRequestUseCase`, `ListPartRequestsUseCase`, `GetPartRequestUseCase`,
  `ClosePartRequestUseCase`, `DeletePartRequestUseCase`.
- port-out `PartRequestRepositoryPort`, `PartsCatalogPort`(catalog `FindLegoSetUseCase`),
  `PartRequestHolderNotifierPort`(collection `ListSetHoldersUseCase` + notification).
- persistence `part_requests` — 인덱스 `{status, createdAt:-1}`, `{setNumber, status, createdAt:-1}`,
  `{requesterId, createdAt:-1}`.
- `/api/v1/part-requests/mine`은 세션 필요 — `UserAuthInterceptor` 비공개 접두사에 넣었다.

### 프론트

- core `parts` 모듈: 타입, `createPartRequest`, `fetchPartRequests`, `fetchMyPartRequests`,
  `fetchPartRequest`, `closePartRequest`, `deletePartRequest`.
- `/parts` 게시판(세트 번호 필터·열림/마감), `/parts/new`(`?set=` 미리 채움), `/parts/[id]`(돕기 → 1:1 대화,
  작성자 마감·삭제). 세트 상세에 "부품 요청 n건"·"부품 요청하기" 링크, 컬렉션 화면에 진입 링크.
- 알림 링크 허용 목록(`views/notifications`)에 `/parts`, `/parts/{id}`.

## 범위에서 뺀 것 — Part-out 가치

세트를 부품으로 나눠 팔 때의 가치(part-out value)는 **이번에 만들지 않는다.** 부품 인벤토리(세트 → 부품
목록)와 부품별 시세가 모두 없고, 카탈로그 데이터 수급 경로(Rebrickable·BrickLink 라이선스)가
기획 갭 목록 P2로 미정이다. 숫자를 지어내면 시세 화면의 신뢰를 깎는다. 데이터 수급이 정해지면
`parts` 컨텍스트에 부품 카탈로그를 더하고 이어서 만든다. (볼트 `08_개선과제`에 올린다)

그 밖에: 요청 신고·관리자 숨김(기존 신고 컨텍스트 대상 확장), 부품 사진 첨부, 부품 번호 자동완성.

## Tasks

- [x] B1 domain·검증 + 테스트
- [x] B2 port·service·persistence·web
- [x] B3 보유자 알림 어댑터 + 테스트(작성자 제외·상한·장애 흡수)
- [x] B4 계정 삭제 정리
- [x] F1 core, `/parts` 3화면, 세트 상세·컬렉션 진입, 알림 링크 허용
- [ ] V1 로컬 실검증: 보유자 계정에 알림 도착, 돕기 → 1:1 대화 열림
