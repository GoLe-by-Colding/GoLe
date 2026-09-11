# 소셜 홍보 게시 검토 (promotion-review)

## 왜 필요한가

새 기능을 출시하거나 눈에 띄는 변경을 했을 때 인스타그램 Threads 계정으로 소식을 알리는 홍보
활동이 생겼다. 지금은 이 흐름에 아무 안전장치가 없다 — 누구든 계정에 접근할 수 있으면 바로
외부에 올라간다. 오탈자, 아직 출시 안 된 기능 언급, 사실과 다른 설명이 그대로 나가는 사고를
막으려면 **작성자 본인이 아닌 다른 관리자의 승인**을 거친 뒤에만 실제 업로드로 이어지게 해야
한다.

이 스펙은 그 승인 게이트만 다룬다. Threads 계정 자체(자격증명 발급, 실제 Graph API 연동)는
범위 밖이며 D5·후속에서 명시한다.

## 결정

### D1. 새 바운디드 컨텍스트 `promotion`

`account`의 `Social*`(OAuth 로그인)과 이름이 겹치지 않도록, 그리고 이 흐름이 회원 인증과
무관한 별개 도메인이므로 `com.gole.api.promotion`을 새로 둔다. 애그리거트는 `PromotionPost`
하나다.

### D2. 상태 기계: `DRAFT → PENDING_REVIEW → APPROVED → PUBLISHED`, 반려는 `DRAFT`로 되돌림

| 상태 | 의미 |
|---|---|
| `DRAFT` | 작성 중. 아직 아무도 검토 요청받지 않음 |
| `PENDING_REVIEW` | 검토 대기 — 관리자 콘솔 큐에 노출 |
| `APPROVED` | 승인됨. 아직 실제 업로드는 안 됨 |
| `PUBLISHED` | 실제(또는 모의) 업로드 완료 |

반려는 별도 `REJECTED` 종결 상태를 두지 않고 `DRAFT`로 되돌린다 — 반려 사유(`rejectionReason`)와
반려자·반려 시각은 필드로 남기므로 이력은 보존되고, 작성자는 고치고 다시 제출하면 된다. 종결
상태를 늘리면 "반려된 걸 고쳐서 다시 낼 때 상태를 어떻게 되돌리나"라는 별도 전이 규칙이 또
필요해진다.

### D3. 승인(`APPROVED`)과 발행(`PUBLISHED`)은 분리된 별도 조치다

검토 승인을 누른다고 즉시 Threads에 올라가지 않는다. `PUBLISHED`로의 전이는 별도
`발행` 조치가 있어야 한다. 지금은 실제 Threads 자격증명이 없어 발행 어댑터가 스텁이므로(D5),
승인과 발행을 한 클릭으로 묶으면 "검토 승인 = 실제로 외부에 나감"이라는 오해가 생긴다. 두
단계로 나누면 자격증명이 준비되기 전까지는 몇 번을 승인해도 실제로 나가는 경로가 물리적으로
없다.

### D4. 메이커-체커: 작성자 본인은 자신의 초안을 승인·반려할 수 없다

`approve`/`reject` 호출 시 `reviewerId == authorId`이면 `SelfReviewNotAllowedException`(403)을
던진다. "검토를 한 번 받는다"는 요구사항의 핵심은 다른 시선이 한 번 더 보는 것이고, 작성자
스스로의 승인은 그 요구를 충족하지 못한다.

### D5. 발행은 아웃바운드 포트 뒤에 숨긴다 — 지금 구현은 스텁이다

`SocialPublishPort.publish(PromotionPost)`를 새로 두고, 지금 유일한 구현체
`StubThreadsPublishAdapter`는 **실제 Threads Graph API를 호출하지 않는다.** 로그만 남기고
`stub-<uuid>`형 가짜 `externalPostId`를 반환해 `PUBLISHED` 전이를 완결시킨다.

저장소에 Threads 개발자 앱 자격증명이 없는 상태에서 실제 외부 발행 경로를 만들면, 검토 없이
실행되는 코드 경로(테스트, 시드, 우발적 재실행)가 실제 계정에 글을 올릴 위험이 있다. 포트로
분리해두면 자격증명이 준비된 뒤 어댑터 구현체 하나만 교체하면 되고, 도메인·승인 흐름·감사
로그는 전혀 바뀌지 않는다.

### D6. 초안 생성은 수동이다 — 배포 이벤트에서 자동 생성하지 않는다

"새 기능을 추가했을 때"를 CI/배포 이벤트에서 자동 감지해 초안을 만드는 것은 이 스펙의 범위
밖이다. 무엇이 "홍보할 가치가 있는 변경"인지는 사람의 판단이 필요하고, 커밋 메시지나 PR
제목을 그대로 옮기면 내부 전용 표현이 그대로 노출될 위험이 있다. 대신 관리자 콘솔에 초안
작성 폼을 두고, 담당자가 무엇을 알릴지 직접 요약해 초안을 만든다. 배포 파이프라인 연동은
후속(범위 밖 절 참고).

### D7. 감사 로그는 승인·반려·발행에만 남긴다

`AdminActionType`에 `PROMOTION_POST_APPROVE`/`PROMOTION_POST_REJECT`/`PROMOTION_POST_PUBLISH`
세 개만 추가한다. 초안 생성·제출은 상태를 바꾸는 "조치"가 아니라 통상적인 작성 행위이므로
(`report` 접수와 동일한 취급) 감사 대상에서 뺀다 — `AdminActionType` 주석의 기존 원칙("상태를
바꾸는 조치만 열거한다")을 그대로 따른다.

### D8. 이미지 첨부는 `media` 컨텍스트 업로드·수명주기 파이프라인을 그대로 재사용한다 (구 T3)

새 업로드 경로는 만들지 않는다. 관리자가 `POST /api/v1/media/images(/batch)`로 먼저 업로드하면
`STAGED`(업로더 본인만 조회 가능, 기본 24시간 뒤 자동 폐기·삭제) 상태의 자산이 생긴다. 그
응답의 `key`(URL이 아니다)를 초안 등록 요청(`mediaKeys`)에 담아 보내면, `PromotionPostService`가
`media`의 인바운드 포트 `ManageMediaAssetsUseCase.replaceReferences(authorId,
PROMOTION_POST, id, mediaKeys, true)`를 호출해 `PUBLIC`으로 전이시키고 이 게시물에 연결한다 —
`ListingService`/`CommunityService`가 사진을 첨부할 때 쓰는 것과 동일한 패턴이다. 이 연결을
빠뜨리면 검토자(작성자 아닌 다른 관리자)가 큐에서 이미지를 못 보고, 승인·발행 이후에도 24시간
뒤 원본이 지워진다 — 실제로 첫 구현에서 이 연결을 빠뜨렸다가 리뷰 중 발견해 바로잡았다.

`PromotionPost.mediaUrls`에는 `key`가 아니라 `MediaKey.publicPath(key)`로 만든 same-origin 공개
경로(`/api/v1/media/<key>`)를 저장한다 — 리뷰 화면에서 프런트 `MediaImage` 컴포넌트가 이 상대
경로에 API 베이스 URL을 붙여 바로 렌더링한다. 개수 상한(`MAX_MEDIA_COUNT = 10`)은
`MediaController`의 배치 업로드 상한과 맞춰, 한 번의 배치 업로드 결과를 그대로 다 첨부할 수
있게 한다.

> T1(실제 Threads Graph API) 연동 시 재검토 필요: 이 공개 경로는 same-origin API를 통해서만
> 접근 가능하다 — Threads 서버가 외부에서 직접 fetch할 수 있는 URL이 아니므로, 실제 발행
> 어댑터를 붙일 때는 별도 공개 CDN URL을 노출하거나 이미지를 직접 업로드하는 방식으로
> 바꿔야 한다.

### D9. 초안 자동 생성 에이전트를 추가한다 — D6/T2를 정정한다

D6은 "배포 이벤트에서 자동으로 초안을 만드는 것은 범위 밖"이라 선언했고, 이유는 두 가지였다.
"홍보할 가치가 있는 변경"인지는 사람 판단이 필요하다는 것과, 커밋 메시지·PR 제목을 그대로
옮기면 내부 전용 표현이 노출될 위험이었다. 이 결정은 그 우려를 없애는 게 아니라 **판단의
위치를 옮긴다** — "초안을 만들지 말지"가 아니라 "만들어진 초안을 승인할지"로. D4(메이커-체커)가
이미 강제하는 승인 게이트가 그대로 안전망이므로, 자동 생성된 초안이 부적절해도 실제로 나가는
경로는 없다.

내부 표현 노출 문제는 소스를 바꿔서 해결한다. 에이전트는 커밋 메시지를 캡션에 복사하지
않는다 — 실제로 동작하는 화면을 스크린샷으로 찍고, 그 스크린샷과 변경 요약만 LLM에 건네
캡션을 새로 쓰게 한다(D12·D13). 커밋 메시지는 "무엇이 바뀌었는지" 찾는 신호로만 쓰이고
캡션에 그대로 들어가지 않는다.

### D10. 실행 위치: GitHub Actions 스케줄 잡 — 기존 스케줄 워크플로우와 동일한 러너

`.github/workflows/promotion-agent.yml`을 새로 둔다. `e2e.yml`(`schedule: cron: "0 18 * * *"`,
`runs-on: ubuntu-latest`)과 `production-health.yml`(실패 시 `secrets.DISCORD_OPERATIONS_WEBHOOK_URL`
알림)의 패턴을 그대로 따른다 — CD 전용 self-hosted 러너(`ubuntu-gole`)는 쓰지 않는다. 매일
저녁(KST) 1회 `schedule:`과 수동 실행용 `workflow_dispatch:`를 함께 둔다.
`concurrency: { group: promotion-agent, cancel-in-progress: true }`로 전날 실행과 겹치지 않게
한다.

### D11. 대상 선정: `apps/web` 변경분이 있는 `feat` 커밋만 스캔한다

전체 저장소가 아니라 프론트엔드(`apps/web/src/**`)에 변경이 있는 `feat(...)` 커밋만 후보로
본다 — 사용자가 화면으로 확인할 수 있는 변경만 캡처 대상이 될 수 있고, 백엔드 전용 변경은
보여줄 UI가 없기 때문이다. 스캔 범위는 직전 실행 이후가 아니라 **최근 24~27시간**의 `git log`로
고정한다(러너 간 상태를 따로 저장하지 않기 위해 — 실행이 하루 밀려도 다음 날 다시 스캔 범위에
걸리므로 유실보다 중복이 안전한 실패 모드다). 중복 후보는 캡션 텍스트 유사도가 아니라 **이미
그 커밋 SHA로 만들어진 초안이 있는지**로 걸러 스킵한다.

### D12. 캡처: Playwright로 배포된 앱을 직접 조작한다 — 매칭되는 E2E 테스트의 상호작용 재생이 1순위

이미지 업로드 파이프라인(`ImageIoImageProcessorAdapter`)이 GIF·APNG를 업로드 단계에서
거부하므로(정지 이미지만 안전하게 재인코딩 가능) 움직이는 캡처는 애초에 불가능하다. 대신
**정지 이미지 2~4장의 시퀀스**로 상호작용을 보여준다.

- 1순위: 변경된 라우트에 대응하는 `apps/web/tests-e2e/*.spec.ts`(또는 컴포넌트 테스트)가
  있으면, 그 테스트의 클릭·입력 스텝을 그대로 재생하면서 상태가 바뀔 때마다 스크린샷을 찍는다.
  사람이 이미 짜둔 상호작용 시나리오를 재사용하는 것이므로 LLM이 매번 화면을 자유 탐색하다
  엉뚱한 곳을 클릭하는 위험이 없다.
- 2순위(폴백): 매칭되는 테스트가 없으면 변경된 라우트를 정적 스크린샷 1장만 찍는다. 상호작용을
  못 보여주는 초안은 검토자가 반려하면 그만이므로 안전하다.

캡처 대상은 프로덕션(`https://gole.co.kr`, `e2e.yml`의 `live-smoke` 대상과 동일)이다 — 별도
스테이징 환경이 없으므로 CD가 끝난 뒤의 실제 배포본을 찍는다.

### D13. 캡션 생성: LLM 호출 + 고정 톤 가이드

`apps/support-agent`와 달리 이 에이전트는 처음부터 외부 LLM 사용을 전제로 한다(캡션
재작성이 목적이라 규칙 기반으로 대체할 수 없다). 시스템 프롬프트에 아래 톤 가이드를 고정으로
박아 넣는다:

- 반말, 구어체 종결어미 사용(-어/-했어/-거든/-더라/-잖아). **음슴체(-함/-임/-됨) 금지** —
  간결해도 완결된 대화체 문장으로 끝낼 것.
- 이모지는 글당 최대 1개, 감정이 실리는 지점에만. 이모지 없는 글도 섞을 것 — 매번 붙이지 않기.
- "그동안 왜 안 고쳤나 싶다"류 반성형 클리셰 마무리 금지. 고정된 3단 구조(상황→감탄→교훈)
  반복 금지 — 문장 길이·구조를 글마다 다르게 가져갈 것.
- 내부 용어·상태값·스펙 결정 번호·커밋 메시지 원문 노출 금지. 500자 이내
  (`PromotionPost` 캡션 상한과 동일).

상세 예시는 `apps/web/scripts/promotion-agent/prompts/caption-tone.md`에 둔다.

### D14. 인증: 기존 관리자 계정을 재사용한다 — 봇 전용 계정을 따로 만들지 않는다

새 계정을 발급하지 않는다. `AdminAuthInterceptor`가 세션 토큰 외의 인증 수단(서비스 계정·API
키)을 지원하지 않으므로, 에이전트는 실행마다 **이미 존재하는 관리자 중 한 명의 계정**으로
일반 로그인 흐름을 그대로 타 세션 토큰을 받는다. 계정 자격증명은 GitHub Actions secrets
(`PROMOTION_AGENT_ADMIN_EMAIL`/`PROMOTION_AGENT_ADMIN_PASSWORD`)로 관리한다.

별도 봇 계정을 두지 않으므로 감사 로그의 `authorId`는 그 관리자 본인의 것으로 남는다 —
"이 초안이 자동 생성됐는지"는 `authorId`만으로 구분되지 않는다. 구분이 필요하면 D9·D13이
캡션 끝에 심는 보이지 않는 커밋 SHA 지문(`publish-draft.ts`의 `encodeCommitMarker`)을 본다.

> 이 결정 때문에 생기는 제약: D4(메이커-체커)에 따라 에이전트 로그인에 쓰인 그 관리자 계정은
> 자신이(=에이전트가) 만든 초안을 승인·반려할 수 없다(D15). 관리자가 한 명뿐이면 에이전트가
> 만든 초안은 아무도 승인할 수 없는 채로 막히므로, 에이전트 계정으로 쓸 관리자와 실제로 검토를
> 맡을 다른 관리자가 최소 한 명씩은 있어야 한다 — 이 전제는 팀에 이미 성립한다(승인 게이트
> 자체가 애초에 다른 시선을 요구하므로 관리자가 1명뿐이면 사람이 작성한 초안도 검토할 수 없다).

### D15. 초안 생성 후 검토 요청까지는 자동, 발행은 여전히 사람이 한다

에이전트는 `POST /api/admin/promotion-posts`로 `DRAFT`를 만든 직후 `POST /{id}/submit`까지
자동으로 이어서 호출해 `PENDING_REVIEW`로 올린다(P2). `PUBLISHED`로의 전이(D3)는 건드리지
않는다 — 사람이 검토 큐에서 승인한 뒤 별도로 "발행" 버튼을 눌러야 한다. 에이전트가 로그인에
쓴 그 관리자 계정이 초안의 작성자이므로 D4(메이커-체커)에 따라 **그 계정은 자기 초안을 승인할
수 없다** — 새 규칙이 아니라 기존 `SelfReviewNotAllowedException` 검사가 그대로 적용되는
것뿐이다.

## 요구사항 (EARS)

- P1 WHEN 관리자가 채널·캡션(500자 이하)·미디어 업로드 키(선택, `mediaKeys`)로 초안을 등록하면,
  시스템은 `DRAFT` 상태로 저장하고 작성자를 요청한 관리자로 고정해야 한다.
- P2 WHEN 작성자가 `DRAFT` 상태의 초안을 검토 요청하면, 시스템은 `PENDING_REVIEW`로 전이하고
  제출 시각을 기록해야 한다. `DRAFT`가 아닌 상태에서의 제출은 거부해야 한다.
- P3 WHEN 관리자가 `PENDING_REVIEW` 큐를 조회하면, 시스템은 대기 중인 게시물을 최신순으로
  반환해야 한다.
- P4 WHEN 작성자가 아닌 관리자가 `PENDING_REVIEW` 게시물을 승인하면, 시스템은 `APPROVED`로
  전이하고 검토자·검토 시각을 기록해야 한다.
- P5 WHEN 작성자 본인이 자신의 게시물을 승인·반려하려 하면, 시스템은 403으로 거부해야 한다.
- P6 WHEN 관리자가 `PENDING_REVIEW` 게시물을 사유와 함께 반려하면, 시스템은 `DRAFT`로 되돌리고
  반려 사유·반려자·반려 시각을 기록해야 한다.
- P7 WHEN 관리자가 `APPROVED` 게시물의 발행을 실행하면, 시스템은 `SocialPublishPort`를 호출해
  결과를 `externalPostId`로 저장하고 `PUBLISHED`로 전이해야 한다. `APPROVED`가 아닌 상태에서의
  발행은 거부해야 한다.
- P8 승인·반려·발행 조치는 감사 로그(`RecordAdminActionUseCase`)에 남아야 한다.
- P9 `PENDING_REVIEW`가 아닌 게시물에 대한 승인·반려 시도는 거부해야 한다(상태 전이 규칙 위반).
- P10 WHEN 초안에 `mediaKeys`를 담아 등록하면, 시스템은 10개 초과이거나 빈 문자열이 섞인 경우
  거부해야 하고, 그 외에는 각 키를 `PUBLIC`으로 전이·연결한 뒤 공개 경로를 `mediaUrls`에
  저장해야 한다. 검토 큐·상세에서는 첨부한 이미지를 그대로 노출해야 한다(D8).
- P11 WHEN 스케줄된 실행에서 `apps/web` 변경분이 있는 `feat` 커밋을 찾으면, 시스템은 그
  커밋 SHA로 이미 만들어진 초안이 있는지 먼저 확인하고, 없을 때만 새 초안을 생성해야 한다
  (D11, 중복 방지).
- P12 WHEN 변경된 라우트에 매칭되는 E2E/컴포넌트 테스트가 없으면, 시스템은 캡처를 정적
  스크린샷 1장으로 대체해야 한다 — 상호작용 재생을 강제하지 않는다(D12).
- P13 WHEN 에이전트가 초안 생성에 성공하면, 시스템은 같은 실행 안에서 검토 요청(`submit`)까지
  이어서 호출해 `PENDING_REVIEW`로 올려야 한다(D15).
- P14 WHEN 에이전트 로그인에 쓰인 관리자 계정이 자신이 만든 초안을 승인·반려하려 하면, 시스템은
  다른 관리자와 동일하게 403으로 거부해야 한다(D4·D15 재확인 — 새 예외가 아니다).

## 설계

- 백엔드 `com.gole.api.promotion` (헥사고날):
  - `domain.model`: `PromotionPost`(id, channel, caption, mediaUrls, status, authorId, createdAt,
    submittedAt, reviewerId?, reviewedAt?, rejectionReason?, publishedAt?, externalPostId?),
    `PromotionPostStatus`, `PromotionChannel`(`THREADS`만 우선 정의 — 확장 가능하게 enum으로).
  - `domain.exception`: `PromotionPostNotFoundException`(404),
    `InvalidPromotionPostStateException`(409), `SelfReviewNotAllowedException`(403).
  - `application.port.in`: `CreatePromotionPostUseCase`, `SubmitPromotionPostForReviewUseCase`,
    `ManagePromotionPostsUseCase`(list/get/approve/reject/publish) — `report` 컨텍스트의
    `SubmitReportUseCase`/`ManageReportsUseCase` 분리를 그대로 따른다.
  - `application.port.out`: `PromotionPostRepositoryPort`, `PromotionPostIdGeneratorPort`,
    `SocialPublishPort`.
  - `application.service.PromotionPostService`가 위 in-port 3개를 모두 구현. `create()`는
    `media` 컨텍스트의 인바운드 포트 `ManageMediaAssetsUseCase`도 의존해 `mediaKeys`를
    `PROMOTION_POST` 타깃으로 붙인다(D8).
  - `adapter.out.persistence`: `PromotionPostDocument`/`PromotionPostMongoRepository`/
    `PromotionPostPersistenceAdapter` (컬렉션 `promotion_posts`).
  - `adapter.out.id.PromotionPostIdGenerator`(UUID, `ReportIdGenerator`와 동일 패턴).
  - `adapter.out.social.StubThreadsPublishAdapter`(D5).
  - `adapter.in.web.AdminPromotionPostController` `/api/admin/promotion-posts`
    (`AdminAuthInterceptor`가 이미 `/api/admin/**`를 보호하므로 별도 가드 불필요):
    - `POST ""` 초안 등록
    - `POST "/{id}/submit"` 검토 요청
    - `GET "?status=&limit="` 목록
    - `GET "/{id}"` 단건
    - `POST "/{id}/approve"` 승인
    - `POST "/{id}/reject"` 반려(`{reason}`)
    - `POST "/{id}/publish"` 발행
  - `AdminActionType`에 3개 추가(D7), `AdminTargetType`에 `PROMOTION_POST` 추가.
- 프론트(FSD):
  - 공유 클라이언트는 `packages/core/src/admin/api/admin-api.ts`에 다른 관리자 리소스와
    함께 둔다(이미 report/settlement/support 등이 한 파일에 있는 기존 관례를 따름).
  - `views/admin/ui/promotion-posts-view.tsx` + `/admin/promotion` 라우트.
  - `widgets/admin-shell`의 좌측 내비에 "홍보 게시" 항목 추가.
- 초안 자동 생성 에이전트(D9~D15):
  - `.github/workflows/promotion-agent.yml` — `schedule:`(매일 저녁 KST) + `workflow_dispatch:`,
    `runs-on: ubuntu-latest`, `concurrency: { group: promotion-agent, cancel-in-progress: true }`.
    실패 시 `production-health.yml`과 동일하게 `secrets.DISCORD_OPERATIONS_WEBHOOK_URL`로 알림.
  - `apps/web/scripts/promotion-agent/`(Node/TS, `pnpm --filter web exec tsx` 로 실행):
    - `scan.ts` — 최근 24~27h `git log` 중 `apps/web/src/**` 변경분이 있는 `feat` 커밋 후보 추출.
    - `capture.ts` — Playwright로 `https://gole.co.kr` 조작. 매칭되는
      `apps/web/tests-e2e/*.spec.ts` 스텝 재생 → 다중 스크린샷, 없으면 라우트 단일 스크린샷.
    - `caption.ts` — LLM 호출, `prompts/caption-tone.md`(D13 톤 가이드)를 시스템 프롬프트로 사용.
    - `publish-draft.ts` — 기존 관리자 계정 로그인(D14) → `@gole/core`의 `uploadImages`/
      `createAdminPromotionPost`/`submitAdminPromotionPost`(`packages/core/src/admin/api/admin-api.ts`)를
      그대로 호출. 웹 admin 화면과 동일한 계약을 타므로 별도 HTTP 클라이언트를 새로 만들지 않는다.
  - 새 백엔드 변경 없음 — 기존 `POST /api/admin/promotion-posts`·`/submit` 엔드포인트의 새
    호출자가 하나 늘어나는 것뿐이다.

## 수용 기준 (테스트로 고정할 것)

- 초안 생성 → 제출 → 승인까지 정상 경로가 상태를 순서대로 전이시킨다.
- 작성자 본인이 승인/반려를 시도하면 `SelfReviewNotAllowedException`(403).
- `DRAFT`가 아닌 상태에서 제출, `PENDING_REVIEW`가 아닌 상태에서 승인/반려,
  `APPROVED`가 아닌 상태에서 발행을 시도하면 `InvalidPromotionPostStateException`(409).
- 반려 시 상태가 `DRAFT`로 돌아가고 `rejectionReason`이 저장된다.
- 발행 성공 시 `SocialPublishPort`가 반환한 `externalPostId`가 저장되고 상태가 `PUBLISHED`가
  된다.
- 승인·반려·발행 각각 감사 로그가 1건씩 남는다.
- `mediaUrls` 11개 이상 또는 빈 문자열 포함 시 `IllegalArgumentException`(400).
- 동일 커밋 SHA로는 초안이 중복 생성되지 않는다(D11/P11).
- 매칭되는 E2E 테스트가 없는 후보는 정적 스크린샷 1장짜리 초안으로 대체되고, 실행 자체가
  실패하지 않는다(D12/P12).
- 에이전트가 만든 초안은 생성 직후 `PENDING_REVIEW`까지 자동 전이한다(D15/P13).
- 에이전트 로그인에 쓰인 관리자 계정은 자신이 만든 초안을 승인·반려할 수 없다(D15/P14, 기존
  D4 검사 재확인).

## 범위 밖 / 후속

- **T1. 실제 Threads Graph API 연동.** 자격증명(앱 ID·시크릿·장기 액세스 토큰)이 준비되면
  `SocialPublishPort` 구현체를 `StubThreadsPublishAdapter`에서 실제 어댑터로 교체한다. 도메인·
  컨트롤러·프론트는 변경 불필요. `GoLe-obsidian/08_Improvements/알려진 개선 과제.md`에 P1로
  기록.
- **T4. 예약 발행.** 지금은 관리자가 명시적으로 "발행" 버튼을 눌러야 한다. 시각 예약은 범위
  밖.
- **T5. 영상 첨부.** `media` 컨텍스트는 정지 이미지(JPEG/PNG)만 지원한다(`ImageIoImageProcessorAdapter`).
  캡처 영상을 첨부하려면 새 자산 타입·저장·검토 화면 재생 지원이 필요하다 — 이번 라운드는
  정지 이미지 시퀀스(D12)로 대체하고 영상은 후속으로 미룬다.
- **T6. 모바일 관리자 화면.** `apps/mobile`에는 admin 화면 자체가 없고 이미지 선택 라이브러리도
  없다(`uploadImage`/`uploadImages`는 플랫폼 중립으로 설계돼 있으나 연결된 화면이 없음). 홍보
  게시 검토를 모바일에서도 하려면 별도 스펙이 필요하다.
- **T7. 캡처 폴백 품질.** D12의 2순위 폴백(테스트 없을 때 라우트 단일 스크린샷)은 어떤 상태의
  화면이 찍힐지 보장하지 않는다(로딩 중·빈 상태일 수 있음). 초기 운영 데이터를 보고 필요하면
  더 나은 폴백을 후속으로 설계한다.

> T2(배포/CI 이벤트 기반 초안 자동 생성)는 D9~D15로 반영해 구현한다. D6이 원래 우려했던
> "사람 판단 없이 나간다"는 위험은 승인 게이트(D4)로, "내부 표현 노출"은 캡션을 커밋 메시지가
> 아니라 스크린샷 기반으로 새로 쓰게 하는 것(D9·D13)으로 해소한다.
>
> T3(이미지 첨부)는 D8로 반영해 구현 완료. `media` 업로드 파이프라인을 재사용하며 새 업로드
> 경로는 만들지 않았다.

## 관련

- `admin-console` — 감사 로그(`RecordAdminActionUseCase`), 관리자 권한 경계, `AdminAuthInterceptor`.
- `report` — `SubmitReportUseCase`/`ManageReportsUseCase` 분리 패턴을 그대로 차용.
- `media` — 업로드·`STAGED`→`PUBLIC` 전이(`ManageMediaAssetsUseCase.replaceReferences`)를
  `listing`/`community`와 동일한 방식으로 재사용(D8).
- `.github/workflows/e2e.yml`·`production-health.yml` — 초안 자동 생성 에이전트의 스케줄·러너·
  실패 알림 패턴을 그대로 차용(D10).
- `apps/support-agent` — "별도 자동화 에이전트" 전례이지만 패턴은 다르다: support-agent는
  상시 기동 gRPC 서비스로 요청마다 실시간 반응하고 외부 LLM을 안 쓴다. 이 에이전트는 스케줄
  잡으로 하루 한 번 돌고 처음부터 LLM을 쓴다(D9·D13) — 혼동하지 않도록 관련 문서에 남겨둔다.
