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

> **2026-09-18 확인 — 위 우려의 절반은 사실이 아니었다.** 이 경로는 인증 없이 조회된다:
> `UserAuthInterceptor.isPublicRead()`가 GET/HEAD 중 private 목록(`orders`·`collections`·
> `users/`·`chat`·`community/feed/following`·`listings/mine`)에 없는 요청을 모두 통과시키는데
> `/api/v1/media/**`는 그 목록에 없다. 익명 브라우저가 매물 이미지를 보는 것도 같은 이유다.
> 따라서 외부 서버가 `https://gole.co.kr/api/v1/media/<key>`를 직접 가져갈 수 있으며 별도 공개
> CDN이나 재업로드는 필요하지 않다.
>
> 남는 것은 **형식 문제 하나뿐**이다 — 저장값이 절대 URL이 아니라 상대 경로다. 실제 발행
> 어댑터는 `STORAGE_PUBLIC_BASE_URL`을 붙여 절대 URL로 만들어 보내야 한다. 그 밖에 Threads의
> 이미지 용량·종횡비 제약은 T9에서 다룬다.

### D9. 초안 자동 생성 에이전트를 추가한다 — D6/T2를 정정한다

D6은 "배포 이벤트에서 자동으로 초안을 만드는 것은 범위 밖"이라 선언했고, 이유는 두 가지였다.
"홍보할 가치가 있는 변경"인지는 사람 판단이 필요하다는 것과, 커밋 메시지·PR 제목을 그대로
옮기면 내부 전용 표현이 노출될 위험이었다. 이 결정은 그 우려를 없애는 게 아니라 **판단의
위치를 옮긴다** — "초안을 만들지 말지"가 아니라 "만들어진 초안을 승인할지"로. D4(메이커-체커)가
이미 강제하는 승인 게이트가 그대로 안전망이므로, 자동 생성된 초안이 부적절해도 실제로 나가는
경로는 없다.

내부 표현 노출 문제는 소스를 바꿔서 해결한다. 에이전트는 커밋 메시지를 캡션에 복사하지
않는다 — 모델이 diff와 공개 라우트 목록을 보고 실제 화면을 탐색·캡처한 뒤, 그 결과를 바탕으로
캡션을 새로 쓴다(D12·D13). 커밋 메시지는 "무엇이 바뀌었는지" 찾는 신호로만 쓰이고 캡션에
그대로 들어가지 않는다.

### D10. 실행 위치: 운영 VM의 일회성 컨테이너 — 이전 systemd 직접 실행을 정정한다

**정정 이유 1 — 자격증명이 과다 공급됐다.** 이전 유닛은 `EnvironmentFile=/etc/gole/gole.env`로
운영 전체 비밀(DB·SMTP·PortOne·OAuth)을 주입받았다. 에이전트가 실제로 필요한 값은 셋뿐인데,
브라우저를 띄우고 외부 LLM에 데이터를 보내는 프로세스에 운영 비밀 전부를 넘기는 구성이었다.
전용 최소 env 파일(`/etc/gole/promotion-agent.env`)로 좁힌다. 또한 D14가 적었던 "사람이
`/etc/gole/gole.env`에 직접 넣는다"는 `.kiro/steering/deploy.md`의 "서버의
`/etc/gole/gole.env`를 편집기로 직접 고치지 않는다"와 충돌했다 — 프로비저닝은 `Secret Sync`
워크플로와 `gole-hostctl`을 거친다.

**정정 이유 2 — GitHub Actions 언급이 사실과 달랐다.** `.github/workflows/promotion-agent.yml`은
저장소에 존재하지 않는다(롤백 `86816d7d` 때 사라졌다). 그 문단을 삭제한다.

실행은 운영 VM의 **일회성 컨테이너**다. 이유는 메모리다 — 컨테이너 `mem_limit` 합계가
8,192 MB 중 6,784 MB라 여유가 1.4 GB이고 실효로는 900 MB 안팎인데, Chromium 하나가
400 MB~1 GB를 쓴다. **상주 서비스는 그 몫을 계속 물고 있을 수 없고 하루 한 번 떴다 지는
프로세스만 그 여유를 잠깐 빌릴 수 있다.** 그래서 운영에 떠 있는 `support-agent` 컨테이너
(구 동기 서버, 50051, `mem_limit: 192m`)는 **건드리지 않고**, 별도 이미지
(`apps/support-agent/Dockerfile.promotion`)와 compose profile로 분리한 oneshot 서비스를 둔다.
`gole-promotion-agent.timer`가 매일 `12:00 UTC`(21:00 KST)에 그 컨테이너를 실행한다.

운영 checkout `/app`은 read-only로 마운트해 커밋 스캔·라우트 열거에만 쓰고, 세션 산출물은
이름 있는 볼륨에 둔다(체크포인트가 재기동을 넘어 살아남아야 한다 — D17).

### D11. 홍보 단위는 커밋이 아니라 릴리스다 — 이전 시간창 방식을 정정한다

**이전 설계는 구조적으로 후보를 찾을 수 없었다.** 에이전트는 운영 checkout `/app`에서 도는데
`deploy.sh`가 그 경로를 `origin/main`에 `reset --hard`로 고정한다. 그런데 `main`은 squash
전용이라 릴리스가 `chore(release): ...` 한 줄로 들어온다. 후보 필터는 `^feat(...)?: `를
요구했으므로 **일치하는 커밋이 하나도 없다** — `main`에서 `apps/web/src`를 건드린 마지막
`feat(` 커밋은 `90efc41e`(2026-09-04)다. 더 나쁜 것은 후보 0건이 예외가 아니라 정상 종료라
**매일 초록으로 실패했다**는 점이다.

단위를 커밋으로 잡은 것 자체도 저장소 규약과 어긋났다. `AGENTS.md`가 "백엔드/프론트는
레이어별로 커밋을 분리한다"를 요구하므로 기능 하나가 `feat(web):` 커밋 여럿으로 쪼개지고,
각각이 **같은 기능에 대한 별개 초안**이 된다.

그래서 단위를 **릴리스**로 옮긴다. `main`은 `required_linear_history` + squash라 정책 이후
완전히 선형이므로(마지막 머지 커밋 2026-08-30) **커밋 하나 = 릴리스 하나**이고, `/app`의
`HEAD`가 곧 지금 배포된 것이다. 이로써 "아직 안 나간 기능을 홍보한다"는 이 스펙 첫 문단의
위험이 정의상 사라진다 — 읽는 이력과 찍는 사이트가 같은 커밋임이 보장된다.

후보 선정은 시간창이 아니라 **이미 홍보한 지점까지 뒤로 걷기**다.

```
HEAD에서 main 이력을 뒤로 걷는다 (최대 10커밋 / 7일):
  GET /api/admin/promotion-posts/exists?sourceCommitSha=<sha> 가 true면 → 멈춘다
  sha^..sha 에 apps/web/src 변경이 있으면 → 후보
오래된 것부터, 실행당 최대 3건
```

시간창보다 나은 이유: ① **유실이 없다.** 하루에 main 푸시가 여러 번 몰려도 다 걸린다
(2026-09-12에 실제로 12건이 몰렸다). 이전 방식은 실행이 하루 밀리면 조용히 빠뜨렸는데,
D11 스스로 "유실보다 중복이 안전하다"고 적어둔 것과 모순이었다. ② **새 API가 필요 없다** —
이미 있는 `/exists`만 쓴다. ③ **멱등하다.** 새 릴리스가 없으면 첫 조회에서 끝나므로 타이머든
배포 훅이든 같은 코드로 된다. `PROMOTION_AGENT_LOOKBACK_HOURS`(24~27 제약)는 삭제한다.

`PromotionPost.sourceCommitSha`는 의미만 "릴리스 SHA"로 바뀌고 필드·검증(nullable, 값이 있으면
소문자 40자 16진수)은 그대로다. 마이그레이션은 필요하지 않다. 중복 판정을 위해 캡션에 보이지
않는 유니코드 지문을 숨기던 `encodeCommitMarker` 방식은 폐기한다 — 캡션 문자열이 아니라
`sourceCommitSha` 조회로만 판정한다.

**이것이 막지 못하는 것**: 서로 다른 릴리스가 같은 화면을 또 손대면 SHA는 달라도 내용이 거의
같은 글이 나갈 수 있다. 그 의미 중복은 D18이 다룬다.

### D12. 캡처: 모델이 diff와 공개 라우트 목록을 보고 대상을 선택한다

이미지 업로드 파이프라인(`ImageIoImageProcessorAdapter`)이 GIF·APNG를 업로드 단계에서
거부하므로(정지 이미지만 안전하게 재인코딩 가능) 움직이는 캡처는 애초에 불가능하다. 대신
**정지 이미지 시퀀스**로 상호작용을 보여준다. 코드에 `SCENARIOS`나 라우트·영역 매핑 테이블을
두지 않는다. 모델이 커밋 diff와 `apps/web/src/app/**/page.tsx`에서 동적 세그먼트를 제외해
열거한 공개 라우트 목록을 읽고 캡처할 라우트, 상호작용, 스크린샷 수를 판단한다.

**캡처 툴은 선언적이다 — 이전 상태 있는 4종을 정정한다.** 이전 설계는 `browser_goto` →
`browser_click` → `browser_select` → `browser_screenshot`이 "현재 페이지"라는 암묵 상태에
얹혀 있었다. 이러면 두 가지가 깨진다. ① 체크포인트에서 전사를 복원해도 브라우저는 초기
상태이므로 과거 tool_result가 거짓이 되어 **재개가 성립하지 않는다**(D17). ② 클릭이 실패하면
뒤따르는 호출이 알 수 없는 페이지 상태를 보게 된다.

대신 **`capture(route, interactions[], label)` 하나**로 합친다. 호출마다 새 `BrowserContext`를
열어 `이동 → 상호작용 순차 → 스크린샷 → 닫기`를 한 번에 끝내므로 브라우저 상태가 호출 경계를
넘지 않는다. `(route, interactions)` 해시를 키로 같은 화면의 재촬영을 건너뛴다. 상호작용이
하나라도 실패하면 전체 캡처를 실패시키고 부분 성공 화면을 남기지 않는다.

모델에 노출하는 툴은 최종 5종이다 — `list_releases`, `read_release_diff`, `list_routes`,
`capture`, `submit_promotion_draft`.

캡처 툴은 열거된 공개 라우트만 허용하며 로그인·결제·`/admin` 라우트를 거부한다. 모든
브라우저 조작에서 `GET`/`HEAD`/`OPTIONS` 외 네트워크 요청을 코드로 차단하고 각 상호작용은 5초
안에 끝나야 한다. 스크린샷은 현재 후보의 세션 디렉터리에만 기록한다.

**보장 범위를 정확히 적어둔다.** 이전 TS 구현의 네비게이션 가드는 `isNavigationRequest()`인
하드 내비게이션만 차단했으므로, Next App Router의 소프트 내비게이션(RSC GET 페치)은 GET 허용
분기로 통과했다. 즉 "정적 공개 라우트만 허용"이 실제로는 새로고침에만 걸리는 반쪽 보장이었다.
새 구현은 라우트 허용 검사를 `capture` 진입부에서 먼저 수행하고, 네트워크 핸들러는 메서드
차단과 origin 대조를 담당한다 — 두 겹으로 나눠 어느 쪽도 혼자서 보장을 지지 않게 한다.

**알려진 한계**: 동적 세그먼트(`[id]`)가 든 라우트는 열거에서 제외되므로 매물 상세처럼
사용자에게 가장 보여줄 것이 많은 화면을 아직 찍지 못한다. 이번 범위에서 풀지 않고 후속(T8)으로
남긴다 — 실제 인스턴스를 고르는 규칙과 그 화면에 실린 개인 데이터 취급(D19)을 함께 정해야 한다.

캡처 대상은 프로덕션(`https://gole.co.kr`, `e2e.yml`의 `live-smoke` 대상과 동일)이다 — 별도
스테이징 환경이 없으므로 CD가 끝난 뒤의 실제 배포본을 찍는다.

### D13. 캡션 생성: `caption-tone.md`를 세션의 시스템 프롬프트로 승계한다

같은 패키지의 문의 에이전트와 달리 이 에이전트는 처음부터 외부 LLM 사용을 전제로 한다(캡션
재작성이 목적이라 규칙 기반으로 대체할 수 없다). 아래 톤 가이드를 `policy.py`가 세션의 시스템
프롬프트로 승계한다. 캡션만 따로 한 번 더 LLM을 호출하는 단계는 두지 않는다 — 캡션은 캡처
대화의 마지막 산출물이다.

- 반말, 구어체 종결어미 사용(-어/-했어/-거든/-더라/-잖아). **음슴체(-함/-임/-됨) 금지** —
  간결해도 완결된 대화체 문장으로 끝낼 것.
- 이모지는 글당 최대 1개, 감정이 실리는 지점에만. 이모지 없는 글도 섞을 것 — 매번 붙이지 않기.
- "그동안 왜 안 고쳤나 싶다"류 반성형 클리셰 마무리 금지. 고정된 3단 구조(상황→감탄→교훈)
  반복 금지 — 문장 길이·구조를 글마다 다르게 가져갈 것.
- 내부 용어·상태값·스펙 결정 번호·커밋 메시지 원문 노출 금지. 에이전트가 제출하는 캡션은
  1~450자로 제한한다(`PromotionPost` 자체의 500자 상한보다 여유를 둔다).

상세 예시는 `apps/support-agent/src/gole_promotion_agent/prompts/caption-tone.md`에 둔다.
별도 마크다운으로 남기는 이유는 사람도 읽기 때문이다 — 관리자가 캡션을 직접 쓸 때 같은 톤을
맞추는 기준 문서다.

**이 규칙들은 이력 없이는 지킬 수 없다.** "반복 금지"·"섞을 것"류는 지난 글을 봐야 판단할 수
있으므로 D18이 최근 게시물 이력을 함께 넣는다.

### D14. 인증: 봇 전용 ADMIN 계정을 사용한다 — 기존 결정을 정정한다

`AdminAuthInterceptor`가 세션 토큰만 지원한다는 제약은 인증 방식에 관한 것이지 계정 주인에
관한 것이 아니다. 봇 전용 ADMIN 계정도 일반 로그인 흐름으로 세션 토큰을 얻을 수 있으므로,
사람 관리자의 계정을 재사용하지 않는다. 전용 계정의 `authorId`로 자동 생성 초안을 구분할 수
있고, 자격증명 유출 시 해당 계정만 정지·회전할 수 있다.

계정 이메일·비밀번호와 모델 API 키는 **전용 최소 env 파일**(`/etc/gole/promotion-agent.env`)에
둔다 — 운영 전체 비밀이 든 `/etc/gole/gole.env`를 재사용하지 않는다(D10). 프로비저닝은 손편집이
아니라 `Secret Sync` 워크플로와 `gole-hostctl`을 거친다(`.kiro/steering/deploy.md`).
에이전트 프로세스에서는 세션 토큰을 제출 구현 안에만 두어 모델 컨텍스트에 노출하지 않는다.
D4는 그대로 적용되므로 봇 계정은 자신이 만든 초안을 승인·반려할 수 없고, 검토용 사람 ADMIN이
최소 한 명 필요하다.

### D15. 초안 생성 후 검토 요청까지는 자동, 발행은 여전히 사람이 한다

에이전트는 `POST /api/admin/promotion-posts`로 `DRAFT`를 만든 직후 `POST /{id}/submit`까지
자동으로 이어서 호출해 `PENDING_REVIEW`로 올린다(P2). `PUBLISHED`로의 전이(D3)는 건드리지
않는다 — 사람이 검토 큐에서 승인한 뒤 별도로 "발행" 버튼을 눌러야 한다. 에이전트가 로그인에
쓴 봇 전용 관리자 계정이 초안의 작성자이므로 D4(메이커-체커)에 따라 **그 계정은 자기 초안을
승인·반려할 수 없다** — 새 규칙이 아니라 기존 `SelfReviewNotAllowedException` 검사가 그대로
적용되는 것뿐이다.

### D16. 구현을 Python으로 옮기고 모델 호출을 포트 뒤에 둔다 — TS Tool Runner를 정정한다

**정정 이유 — 유료 호출 없이 한 줄도 돌릴 수 없었다.** Tool Runner가 `agent.ts`에 직접 박혀
있어서 모델 없이는 후보 선정도, 라우트 열거도, 제출 검증도 실행할 수 없었다. 크레딧이 $0인
동안 이 에이전트는 **한 번도 실행된 적이 없고**, 그래서 D11이 기술한 치명적 결함이 아무에게도
발견되지 않은 채 여기까지 왔다. 돌려볼 수 없는 설계는 고칠 수 없다.

구현 언어는 **Python**이며 위치는 `apps/support-agent/src/gole_promotion_agent/`다. 별도 Node
앱(`apps/promotion-agent`)을 두는 안과 비교해, 이쪽은 에이전트 런타임 이원화를 끝내고
`gole_agent_runtime.privacy`의 관측 격리를 공유한다. **단 `gole_agent_worker`(AgentJobs, gRPC)
안에는 넣지 않는다** — 그 워커는 운영에 배포된 적이 없고, 필요한 것은 영속성이지 분산 job
leasing이 아니며(D17), egress가 필요한 작업을 "문의 원문은 밖으로 안 나간다"가 설계 중심인
프로세스에 섞으면 그 보장이 약해진다. 구조 템플릿은 같은 패키지의 `gole_brick_filter`다 —
영속 워커와 별개 모듈로 Brain/Hands/Session을 나눠 쓰는 기존 선례다.

| 파일 | 책임 |
|---|---|
| `policy.py` | 상수·pydantic 툴 스키마·톤 가이드. 자유 프롬프트를 받지 않는다 |
| `ports.py` | Protocol만. SDK·환경변수·HTTP 클라이언트를 모른다 |
| `brain.py` | 순환 LangGraph (`think ⇄ act` + publish 체인) |
| `hands.py` | Anthropic 루프, Playwright, 백엔드 HTTP, git, 라우트 열거 |
| `session.py` | 단계 기계. 내용물(캡션·이미지·SHA)을 담지 않는다 |
| `checkpoints.py` | 세션 로컬 saver (D17) |
| `runtime.py` | 후보 루프·데드라인·예외 정규화·보존 정리 |
| `__main__.py` | 일회성 엔트리포인트 |

기본 모델은 `claude-opus-5`다. Python SDK에는 TS의 `toolRunner`에 해당하는 헬퍼가 없으므로
멀티턴 루프를 직접 짠다. `stop_reason: "refusal"`은 응답 `content`를 읽기 전에 처리한다.

**모델 호출은 포트 뒤에 둔다.** 대화 이력을 구현체가 소유하지 않고 **전사의 순수 함수**로
만든다 — `Conversation.advance(transcript) -> Turn`. 이래야 체크포인트에서 되살아난 전사로
동일한 맥락이 복원된다(D17). 실제 구현은 매 호출마다 전사에서 메시지를 재구성하며 저장된
스크린샷 경로를 읽어 이미지 블록으로 되살리고, 뒤에서 최근 4장만 남기는 압축을 적용한다.

**드라이런은 결정이다.** `PROMOTION_AGENT_ANTHROPIC_ENABLED != "true"`면 외부 호출 구현체가
기동을 거부한다(`gole_agent_worker`의 OpenAI 이중 opt-in과 같은 방식). `--dry-run`이면 스크립트
대화 구현과 기록 전용 퍼블리셔를 조립하고 **외부 SDK와 백엔드 클라이언트를 import조차 하지
않는다** — 나가는 경로가 물리적으로 없다. D5가 발행 어댑터에 적용한 것과 같은 논리다.

모델에 노출하는 툴은 D12의 5종으로 한정하며 임의 셸 실행은 허용하지 않는다.
`submit_promotion_draft` 구현은 **프롬프트에 의존하지 않고** 실행당 초안 수(기본 3), 중복
`sourceCommitSha`, 캡션 1~450자와 빈 문자열을 코드로 검증한다.

### D17. 후보별 세션을 격리하고 체크포인트로 재개한다

후보 릴리스 하나마다 대화 세션 하나와 브라우저 하나를 순차 실행하며, 후보 단위로 예외를
가둬 한 건의 실패가 다른 후보를 중단시키지 않게 한다. 동시에 여러 브라우저를 띄우지 않는다.

**체크포인트를 둔다.** 재개 가능한 것과 아닌 것을 갈라 보면 이렇다.

| | 재개 | 왜 |
|---|---|---|
| 살아 있는 Chromium 페이지 | 불가 | 직렬화되지 않는다 |
| 모델 대화 전사 | **가능·필요** | 돈이 든 부분이다. 여유 메모리가 900 MB뿐이라 OOM 중단은 가정이 아니라 예상 시나리오인데, 18턴째에 죽어도 처음부터 다시 지불하게 된다 |
| 이미 찍은 스크린샷 | **가능·필요** | 이미 디스크에 파일로 있다 |
| 제출 진행 상태 | **가능·필요** | 업로드 후 초안 생성 전에 죽으면 STAGED 이미지가 고아로 남는다 — 커밋 `07a0354c`에서 실제로 났던 버그다 |

전사 재개가 성립하려면 캡처 툴이 선언적이어야 한다(D12). 그래서 두 결정은 한 쌍이다.

**영속 워커의 lease·fencing은 쓰지 않는다.** `gole_agent_worker`의 `FencedSaver`는 여러 워커가
같은 job을 두고 경쟁할 때 필요한 장치인데, 여기는 일회성 프로세스 하나다. 필요한 것은
영속성이지 분산 조정이 아니다. `langgraph-checkpoint`는 이미 설치돼 있으므로(langgraph 전이
의존) `BaseCheckpointSaver`를 직접 구현한 **세션 로컬 saver**를 둔다.

```
<세션 볼륨>/<릴리스 SHA>/
  checkpoints.jsonl  writes.jsonl  manifest.json  screens/<key>.png  session.jsonl
```

**이미지 바이트는 그래프 상태와 체크포인트에 넣지 않는다 — 경로만 넣는다.** 픽셀을 상태에
실으면 슈퍼스텝마다 복사돼 메모리가 터지고, 체크포인트로 새어 나가면 화면 내용이 디스크에
영속된다(`gole_brick_filter`가 `source: b""`로 지우는 규율과 같은 이유). saver가 직렬화 **전에**
채널 값을 재귀 순회해 바이트열을 만나면 거부한다.

**제출은 3단 체인으로 쪼개 멱등하게 만든다.** `submit_promotion_draft` 툴은 검증만 하고
`업로드 → 생성 → 검토요청` 각 노드가 이미 기록된 결과가 있으면 건너뛴다. 업로드 직후 죽어도
재실행이 재업로드 없이 생성부터 잇는다 — 위 고아 STAGED 버그가 구조적으로 사라진다.

세션 이벤트 로그는 append-only로 기록하되 base64 이미지 대신 파일 경로만 남기고, 7일이 지난
세션을 시작 시 삭제한다. 모델 입력에는 최근 4장 이미지만 유지하고 이전 이미지는 텍스트 요약으로
바꾼다. 기본 출력 경로는 `/var/lib/gole/promotion-agent`이며 **이름 있는 볼륨**이어야 한다
(tmpfs면 재기동 시 체크포인트가 사라져 위 설계가 무의미해진다).

자원 제한은 컨테이너 쪽에 둔다(D10) — `mem_limit: 1g`, Chromium에 `--disable-dev-shm-usage`,
브라우저 동시 실행 1개. 이전 유닛의 `MemoryMax=1G`는 가용량(~900 MB)보다 큰 값이라 운영
컨테이너를 보호하지 못했다. 한도를 가용량 아래로 두는 것이 목적이지 에이전트가 죽는 지점을
정하는 것이 목적이 아니다.

### D18. 발행 이력은 프롬프트로 주고, 페이스는 코드로 막는다

**정정 이유 — 톤 가이드의 핵심 규칙이 실행 불가능했다.** `caption-tone.md`는 "고정된 3단 구조
반복 금지", "이모지 없는 글도 섞을 것", "반성형 클리셰를 습관적으로 붙이지 않는다"를 요구한다.
그런데 매 실행이 무상태라 **모델은 지난 글을 한 편도 보지 못한다.** 무엇을 반복했는지 모르는
채로 "반복하지 마"를 지킬 수는 없다. 가장 중요한 규칙 셋이 지킬 방법 없이 적혀 있었다.

이력은 **백엔드가 정본**이다(`PromotionPost`의 caption·status). 체크포인트나 세션에 복제하지
않는다 — 둘 다 후보 하나에 묶인 저장소라 범위가 맞지 않고, 정본의 사본은 어긋났을 때 증상이
"조용히 같은 글을 또 쓴다"라서 아무도 알아채지 못한다. 실행 시작에 조회해 **시스템 프롬프트**로
넣는다(안정 접두사라 프롬프트 캐싱이 걸린다. user 턴에 끼우면 매 턴 새로 계산된다).

범위는 **반려된 것까지 전부**다. 발행 어댑터가 아직 스텁이라 `PUBLISHED`가 계속 0건이므로
`PENDING_REVIEW`·`APPROVED`·반려 이력이 사실상 유일한 실제 이력이고, 반려 사유를 함께 보여주면
같은 실수를 반복하지 않는다. 이 재료로 의미 중복(D11이 못 막는 것)과 스크린샷 중복도 모델이
피할 수 있다.

조회한 이력은 **초기 상태에 동결해 체크포인트에 남긴다.** 정본으로서가 아니라 재현용 입력
기록으로서다 — 크래시와 재개 사이에 누가 글을 발행하면 같은 대화 도중에 시스템 프롬프트가
바뀌어 전사를 재생해도 맥락이 달라진다.

**페이스는 프롬프트로 부탁하지 않고 코드로 막는다.** 20턴짜리 루프에서 모델은 초반 지시를
잊는다. D16이 `submit_promotion_draft`에 세운 원칙("프롬프트에 의존하지 않고 코드로 검증한다")을
그대로 적용한다 — **검토 대기(`PENDING_REVIEW`)가 5건 이상이면 새 초안을 만들지 않는다.**
사람 검토가 병목인 설계인데 그 병목을 보는 곳이 없어 큐가 무한히 자랄 수 있었다. 발행 간격
게이트는 발행이 스텁인 동안 무작용이므로 T1 연동 때 추가한다.

### D19. 프로덕션을 찍되 검토 규칙을 명문화한다

캡처 대상이 프로덕션이므로 스크린샷에 **실제 매물 사진(판매자 저작물)·닉네임·가격**이 들어간다.
사이트에 공개돼 있는 것과 그것을 회사 홍보물로 외부 플랫폼에 재게시하는 것은 다른 행위다.
같은 저장소의 `apps/support-agent`가 문의 원문 한 줄도 밖으로 내보내지 않으려 공들인 것에 비해,
홍보 쪽은 이 질문을 한 적이 없었다.

별도 스테이징이 없어 캡처 대상은 프로덕션으로 유지하되, **사람 검토를 이 문제의 게이트로
명시한다.** 검토 화면은 승인 전에 다음을 확인하게 한다.

- 첨부 이미지에 **식별 가능한 닉네임·프로필 사진·연락처**가 보이는가
- **타인의 저작물**(매물 사진)이 주제로 보일 만큼 크게 실렸는가
- 캡션이 특정 이용자나 특정 매물을 지목하는가

하나라도 해당하면 반려하고 다른 화면으로 다시 찍게 한다. 에이전트 쪽에서는 캡션이 내부 용어를
노출하지 않는 것만 코드로 검증할 수 있고(D13), 이미지 판단은 사람이 한다 — 자동 마스킹은
오탐·미탐이 모두 조용해서 이 단계에서 신뢰할 수 없다.

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
- P11 WHEN 정기 실행이 후보를 찾으면, 시스템은 `main` 이력을 `HEAD`부터 뒤로 걸으며 이미
  홍보한 `sourceCommitSha`를 만나면 멈춰야 하고(최대 10커밋·7일), 그 사이에서 `apps/web/src`
  변경이 있는 릴리스를 오래된 것부터 후보로 삼아 격리된 세션을 순차 실행해야 한다. 동일한
  `sourceCommitSha`의 게시물이 이미 있으면 초안 제출을 거부해야 한다(D11·D17).
- P12 WHEN 후보 세션이 릴리스 diff와 공개 라우트 목록을 조회하면, 시스템은 모델이 캡처 라우트와
  상호작용을 선택하게 해야 하며, `capture` 툴은 허용목록 밖·로그인·결제·`/admin` 라우트와
  `GET`/`HEAD`/`OPTIONS` 외 네트워크 요청을 거부해야 한다. 같은 `(route, interactions)`를 다시
  요청하면 재촬영하지 않고 기존 결과를 돌려줘야 한다(D12).
- P13 WHEN 모델이 `submit_promotion_draft`를 호출하면, 시스템은 실행당 초안 수 한도,
  `sourceCommitSha` 중복, 1~450자 비어 있지 않은 캡션을 툴 구현에서 검증하고, 통과한 초안을
  업로드 → 생성 → 검토요청 순으로 처리하되 각 단계가 이미 완료됐으면 건너뛰어야 한다
  (D13·D15·D16·D17).
- P14 WHEN 에이전트가 초안 제출 권한을 얻기 위해 로그인하면, 시스템은 봇 전용 ADMIN 계정의
  일반 로그인 흐름을 사용하고 발급된 세션 토큰을 모델 컨텍스트에 노출하지 않아야 한다.
  해당 계정이 자신이 만든 초안을 승인·반려하려 하면 P5와 동일하게 403으로 거부해야 한다
  (D4·D14).
- P15 WHEN 실행이 시작되면, 시스템은 검토 대기(`PENDING_REVIEW`) 건수를 조회해 5건 이상이면
  새 초안을 만들지 않고 종료해야 한다(D18).
- P16 WHEN 후보 세션이 시작되면, 시스템은 최근 홍보 게시물 이력(반려 포함)을 시스템 프롬프트에
  넣고 그 스냅샷을 초기 상태에 동결해야 한다. 재개된 세션은 동결된 스냅샷을 사용해야 한다(D18).
- P17 WHEN 세션이 중단된 뒤 같은 릴리스로 다시 실행되면, 시스템은 저장된 전사와 진행 상태에서
  이어가야 하며 이미 업로드한 이미지를 다시 업로드하지 않아야 한다(D17).
- P18 그래프 상태와 체크포인트에는 이미지 바이트가 들어가서는 안 되며, 저장 시도는 거부해야
  한다(D17).

## 설계

- 백엔드 `com.gole.api.promotion` (헥사고날):
  - `domain.model`: `PromotionPost`(id, channel, caption, mediaUrls, sourceCommitSha?, status, authorId,
    createdAt, submittedAt, reviewerId?, reviewedAt?, rejectionReason?, publishedAt?, externalPostId?),
    `PromotionPostStatus`, `PromotionChannel`(`THREADS`만 우선 정의 — 확장 가능하게 enum으로).
  - `domain.exception`: `PromotionPostNotFoundException`(404),
    `InvalidPromotionPostStateException`(409), `SelfReviewNotAllowedException`(403).
  - `application.port.in`: `CreatePromotionPostUseCase`, `SubmitPromotionPostForReviewUseCase`,
    `ManagePromotionPostsUseCase`(list/get/approve/reject/publish) — `report` 컨텍스트의
    `SubmitReportUseCase`/`ManageReportsUseCase` 분리를 그대로 따른다.
  - `application.port.out`: `PromotionPostRepositoryPort`(`existsBySourceCommitSha` 포함),
    `PromotionPostIdGeneratorPort`, `SocialPublishPort`.
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
    - `GET "/exists?sourceCommitSha="` 커밋 SHA 중복 조회
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
- 초안 자동 생성 에이전트(D9~D19):
  - `apps/support-agent/src/gole_promotion_agent/`(Python). 파일 분해는 D16 표를 따르며 구조
    템플릿은 같은 패키지의 `gole_brick_filter`다. 관측 격리는 `gole_agent_runtime.privacy`의
    `reject_external_tracing()` / `@private_execution`을 재사용한다.
  - `apps/support-agent/Dockerfile.promotion` — 브라우저를 포함한 **별도 이미지**. 운영에 떠 있는
    50051 서비스 이미지(`apps/support-agent/Dockerfile`, `mem_limit: 192m`)는 건드리지 않는다.
  - `apps/support-agent/pyproject.toml` — `[project.optional-dependencies] promotion`에
    Anthropic SDK·Playwright·HTTP 클라이언트·pydantic을 둔다. 기본 설치에는 들어가지 않는다.
  - `infra/gcp/docker-compose.yml` — compose profile로 분리한 oneshot 서비스. `/app`을 read-only
    마운트, 세션 산출물은 이름 있는 볼륨, `mem_limit: 1g`(D10·D17).
  - `infra/gcp/systemd/gole-promotion-agent.service`/`.timer` — 매일 `12:00 UTC`에 위 컨테이너를
    실행하고 `/etc/gole/promotion-agent.env`(전용 최소 env)를 주입한다(D10).
  - 백엔드 연동은 기존 REST 계약을 그대로 쓴다 — 로그인, `POST /api/v1/media/images/batch`,
    `POST /api/admin/promotion-posts`, `.../{id}/submit`, `GET .../exists`, `GET .../?status=`.
    세션 토큰은 모델 컨텍스트에 노출하지 않는다.
  - `prompts/caption-tone.md`의 톤 가이드는 `policy.py`가 시스템 프롬프트로 승계한다(D13).

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
- `sourceCommitSha`는 null 또는 소문자 40자 16진수만 허용되고, 동일 SHA로는 초안이 중복
  생성되지 않는다(D11/P11).
- 허용목록 밖 라우트와 로그인·결제·`/admin` 이동, 변경 네트워크 요청은 `capture` 툴이
  거부한다(D12/P12).
- 실행당 초안 수 한도를 넘거나 캡션이 비어 있거나 450자를 넘으면 제출 툴이 거부한다(D16/P13).
- 에이전트가 만든 초안은 생성 직후 `PENDING_REVIEW`까지 자동 전이한다(D15/P13).
- 봇 전용 관리자 계정은 자신이 만든 초안을 승인·반려할 수 없다(D14·D15/P14, 기존 D4 검사
  재확인).
- **후보 선정이 실제로 후보를 반환한다**(D11/P11). `/exists`가 세 번째 SHA에서만 참인 이력을
  주면 두 건을 반환하고, `HEAD`가 이미 홍보됐으면 0건이며, `chore(release):` 제목도 후보가
  된다 — 옛 `feat(` 필터가 영구 0건을 내던 실패를 직접 겨냥한 회귀다.
- 같은 `(route, interactions)`를 두 번 요청하면 실제 촬영은 한 번만 일어난다(D12/P12).
- 업로드 직후 중단된 세션을 재실행하면 이미지를 다시 업로드하지 않고 생성 단계부터
  이어가며, 최종 게시물이 하나만 만들어진다(D17/P17).
- 그래프 상태나 체크포인트에 이미지 바이트를 넣으려 하면 거부되고, 저장된 체크포인트 파일에
  PNG 시그니처가 없다(D17/P18).
- 검토 대기가 5건이면 새 초안을 만들지 않는다(D18/P15).
- 최근 게시물 이력이 시스템 프롬프트에 포함되고 초기 상태에 동결된다(D18/P16).
- 드라이런은 외부 SDK와 백엔드 클라이언트를 인스턴스화하지 않는다(D16).
- 관측 추적 환경변수가 켜져 있으면 엔트리포인트가 기동을 거부한다(D16).

## 범위 밖 / 후속

- **T1. 실제 Threads Graph API 연동.** 자격증명(앱 ID·시크릿·장기 액세스 토큰)이 준비되면
  `SocialPublishPort` 구현체를 `StubThreadsPublishAdapter`에서 실제 어댑터로 교체한다. 도메인·
  컨트롤러·프론트는 변경 불필요. `GoLe-obsidian/08_개선과제/알려진 개선 과제.md`에 P1로
  기록.
- **T4. 예약 발행.** 지금은 관리자가 명시적으로 "발행" 버튼을 눌러야 한다. 시각 예약은 범위
  밖.
- **T5. 영상 첨부.** `media` 컨텍스트는 정지 이미지(JPEG/PNG)만 지원한다(`ImageIoImageProcessorAdapter`).
  캡처 영상을 첨부하려면 새 자산 타입·저장·검토 화면 재생 지원이 필요하다 — 이번 라운드는
  정지 이미지 시퀀스(D12)로 대체하고 영상은 후속으로 미룬다.
- **T6. 모바일 관리자 화면.** `apps/mobile`에는 admin 화면 자체가 없고 이미지 선택 라이브러리도
  없다(`uploadImage`/`uploadImages`는 플랫폼 중립으로 설계돼 있으나 연결된 화면이 없음). 홍보
  게시 검토를 모바일에서도 하려면 별도 스펙이 필요하다.
- **T7. 캡처 선택의 비결정성.** 모델이 diff와 라우트 목록을 보고 탐색하므로 같은 릴리스에서도
  선택한 화면과 캡션이 달라질 수 있다. 초기 운영 결과를 보고 툴 가드나 프롬프트를 좁힐지는
  후속으로 판단한다.
- **T8. 동적 라우트 캡처.** `[id]` 세그먼트가 든 라우트가 열거에서 빠져 매물 상세를 찍지
  못한다(D12). 실제 인스턴스를 고르는 규칙과 그 화면의 개인 데이터 취급(D19)을 함께 정해야
  하므로 별도 라운드로 미룬다.
- **T9. 발행 간격 게이트와 Threads 미디어 제약.** 발행 어댑터가 스텁인 동안은 간격 게이트가
  무작용이다(D18). T1에서 실제 연동할 때 간격 규칙과 함께 Threads의 이미지 용량·종횡비 제약,
  대체 텍스트(`PromotionPost`에 필드 없음)를 함께 정한다. 스크린샷이 PNG 원본이라 용량이
  플랫폼 상한을 넘을 수 있어 재인코딩이 필요할지 그때 판단한다.

> T2(배포/CI 이벤트 기반 초안 자동 생성)는 D9~D17로 반영해 구현한다. D6이 원래 우려했던
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
- `infra/gcp/systemd/gole-data-backup.service`·`gole-cert-renew.timer` — oneshot 서비스의
  리소스 우선순위와 정기 실행 패턴을 차용(D10·D17).
- `apps/support-agent` — 홍보 에이전트는 **이 패키지 안에 산다**(D16). 같은 패키지가 이미
  `gole_support_agent`(문의)·`gole_brick_filter`(사진)를 담고 있으므로 디렉터리 이름과 내용의
  불일치는 기존 선례를 따른다. 다만 **실행 패턴은 서로 다르다** — 문의는 상시 기동 gRPC
  서비스로 요청마다 반응하고 외부 LLM을 안 쓰는 반면, 홍보는 하루 한 번 도는 oneshot이고
  처음부터 LLM을 쓴다(D9·D13). 공유하는 것은 구조 관용구와 관측 격리(`gole_agent_runtime`)이지
  프로세스·이미지·자원 한도가 아니다.
- `agent-worker` — `gole_agent_worker`(AgentJobs, gRPC)에 편입하지 **않기로** 한 근거는 D16에
  있다. 트리거가 관리자 콘솔 발 요청으로 바뀌면 그 판단을 다시 볼 만하다.
