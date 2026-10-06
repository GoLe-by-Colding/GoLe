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

**2026-09-20 추가 — 반려는 상태만이 아니라 릴리스 점유도 놓아준다.** 되돌리는 것이 상태뿐이면
위 문단의 "고치고 다시 제출하면 된다"가 실제로는 불가능했다. `/exists`(D11 탐색 경계)가 상태를
보지 않아 **반려된 초안도 "이미 있음"** 이라 에이전트는 그 릴리스를 건너뛰고, 사람이 같은 SHA로
다시 만들려 하면 unique 인덱스가 409로 막는데 초안을 고칠 엔드포인트도 없다. 즉 **한 번 반려된
릴리스는 영영 홍보되지 않았다.**

원인은 한 필드가 **출처**와 **점유**를 겸한 것이다. 둘은 다른 개념이므로 나눈다.

| 필드 | 뜻 | 반려 후 |
|---|---|---|
| `sourceCommitSha` | **출처** — 이 초안이 어느 릴리스에서 나왔나. 불변 | 그대로 남는다 |
| `claimedSourceCommitSha` | **점유** — 지금 이 릴리스를 붙잡고 있나. nullable | `null` |

반려된 초안은 그 릴리스를 홍보한 것이 아니므로 점유를 놓는 쪽이 사실에 맞다. 출처가 남으므로
목록 화면의 "원본 릴리스" 표시와 재시도 집계는 그대로다. DB의 `unique + sparse` 인덱스도 출처가
아니라 점유에 건다 — 같은 릴리스로 초안이 여럿 생기되 **동시에 붙잡는 것은 하나**다.

**대신 무한 재시도를 코드로 끊는다.** 점유가 풀리면 D11의 탐색 창(7일) 안에서 에이전트가 매일
같은 릴리스를 다시 집으므로 유료 호출이 쌓인다. 같은 **출처**로 쌓인 초안이 3건이면 생성을
`PROMOTION_POST_RETRY_LIMIT`(409)로 거절한다 — 점유 충돌인
`PROMOTION_POST_DUPLICATE_SOURCE_COMMIT`("지금 붙잡고 있는 것이 있으니 나중에")과 코드를 나눠
호출자가 "이 릴리스는 그만"을 구분하게 한다.

D11의 결과 표는 그대로다 — `submitted` 직후 `/exists`는 참이고 그 실행에서 후보가 아니다.
달라진 것은 그 다음이다. 사람이 반려하면 다시 거짓이 되어, 상한에 닿기 전까지 후보로 돌아온다.

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

> **2026-10-05 — D20이 대체한다.** 실행 위치는 GitHub 러너, 모델 호출은 SSH LLM 게이트웨이다.
> 아래 VM 컨테이너·타이머 파일은 저장소에서 지웠다. 이미 깔린 운영 VM의 유닛은 운영 승인을 받아
> 손으로 걷어낸다(`.kiro/steering/deploy.md`).

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

> **2026-10-05 — "단위는 릴리스"는 유지, 후보 찾기는 D20이 대체한다.** 실행이 CD 성공 이벤트에서
> 오므로 그 릴리스 SHA 하나만 본다. 뒤로 걷기·`manifest.json` 원장·`deferred` 구분은 지웠다.
> 웹 변경이 없거나 `/exists`가 참이면 건너뛰고, 실패한 릴리스는 사람이 `workflow_dispatch`에
> SHA를 넣어 다시 돌린다.

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

**2026-09-18 추가 — 건너뛴 커밋도 원장에 남긴다.** 위 의사코드는 `/exists`(제출된 초안)만
경계로 본다. 그런데 모델이 "홍보할 것이 없다"고 판단해 `skipped` 로 끝내면 초안이 만들어지지
않으므로 `/exists` 는 계속 거짓이고, **그 커밋은 탐색 창에 남아 있는 동안 매 실행마다 유료로
재평가된다.** 실제로 2026-09-18 실비용 실행에서 `2845e32c` 가 `skipped` 로 끝났고 기록은
터미널 한 줄뿐이었다.

새 저장소를 만들지 않고 세션 디렉터리의 `manifest.json`(`done` 필드)을 그대로 원장으로 쓴다.
보존(`RETENTION_DAYS`)과 탐색 창(`MAX_WALK_DAYS`)이 둘 다 7일이라, 원장이 지워질 무렵이면 그
커밋은 이미 창 밖이다 — **두 값은 함께 움직여야 한다.**

**`break` 가 아니라 `continue` 다.** `/exists` 가 참이면 "여기부터는 이미 홍보한 영역"이라
멈추는 것이 맞지만, 건너뛴 커밋은 경계 표지가 아니라 **개별 제외 대상**이다. 여기서 멈추면
그보다 오래된 후보가 통째로 조용히 사라진다. 두 술어를 분리하고 회귀 테스트로 고정한다.

**2026-09-20 추가 — 결과를 넷으로 나눈다. `skipped` 하나로 뭉치면 안 된다.** 위 규율은 건너뛴
커밋만 다뤘고, 그래서 같은 함정이 **실패 경로에 그대로 남아 있었다.** 홍보 경계에서 walk 가
멈추므로, 옛 후보가 실패하고 그보다 새 후보가 성공하면 그 실패는 경계에 가려 **영영 후보가
되지 못한다**(재현 확인). 게다가 20턴을 다 쓴 중단도 `skipped` 로 기록돼 "판단"과 구분되지
않았다.

| 결과 | 뜻 | 원장 | 다음 실행 |
|---|---|---|---|
| `submitted` | 초안을 만들어 검토 요청까지 올렸다 | `/exists` 가 참이 된다 | 후보 아님 |
| `skipped` | 모델이 **홍보할 것이 없다고 판단**했다 | `done: skipped` | 창 안에서는 제외 |
| `deferred` | 턴 예산을 다 써 **결론에 도달하지 못했다** | `done: deferred` | **다시 본다** |
| `failed` | 타임아웃·예외로 중단됐다 | `done: failed` | **다시 본다** |

`deferred`·`failed` 는 경계 너머에 있어도 원장에서 되살린다. 탐색 창 밖으로 밀려난 것은
되살리지 않는다 — 보존과 탐색 창이 같은 7일이라 저절로 정리된다.

### D12. 캡처: 모델이 diff와 공개 라우트 목록을 보고 대상을 선택한다

> **2026-10-05 — 일부를 D21이 대체한다.** 모델이 캡처를 지시하는 툴 루프(`capture`·5종 툴·
> 상호작용·재촬영 캐시)와 "프로덕션을 찍는다"는 폐기했다. 홍보 가능한 공개 라우트를 데모 스택에서
> **전부 한 번씩** 찍고, 모델은 그중에서 고른다. 라우트 허용목록·`PRIVATE_ROUTE`·read-only 가드·
> 세션 토큰 주입·`data-promotion-hide`·빈 상태 규칙은 그대로다.

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

**2026-09-18 정정 — 익명이 아니라 봇 계정으로 로그인한 상태로 찍는다.** 이전 설계는 "익명
브라우저"를 결정으로 적은 적이 없고 암묵 전제로만 두었다. 그 결과 **로그인해야 보이는 기능은
영원히 홍보되지 않았다.** 실비용 실행에서 `2845e32c`(사진 업로드·등록 중복 실행 차단)가
`skipped` 로 끝났는데, 원인은 모델의 오판이 아니라 `/sell` 이 로그인 게이트만 보여준 것이었다.
GoLe 는 `/sell` `/chat` `/collection` `/brick-filter` `/feed` 등 사용자에게 보이는 기능
상당수가 로그인 뒤에 있으므로 이 한계는 에이전트의 가치 대부분을 깎는다.

**로그인 POST 는 하지 않는다.** 백엔드의 `SessionCookie.resolve()` 가 `Authorization: Bearer`
헤더를 쿠키보다 우선하고, 웹앱은 그 헤더를 `localStorage["gole.session"]` 에서 만든다.
따라서 제출 구현(`BackendPublisher`)이 httpx 로 이미 받아 둔 **같은 세션 토큰**을 캡처
컨텍스트의 `add_init_script` 로 심으면 된다. 브라우저에서 나가는 요청은 여전히 전부 GET 이라
**read-only 가드를 그대로 유지한다.** `apps/web/tests-e2e/support/e2e-session.ts` 가 E2E 에서
쓰는 방식과 같다.

**운영자 UI 와 온보딩 배너는 캡처에서 감춘다.** 봇 계정이 ADMIN 이라 헤더에 `관리자` 링크가
뜨고, 온보딩 배너는 모든 화면 위에 걸린다 — 둘 다 홍보물에 나올 이유가 없다. `apps/web` 쪽
해당 요소에 `data-promotion-hide` 속성을 달고 캡처가 CSS 한 줄로 감춘다. 클래스명·DOM 구조에
기대지 않으므로 리팩터링해도 조용히 깨지지 않는다.

**인증 뒤에 새로 필요해진 제외**: `/profile` `/profile/security` `/notifications` `/settings`.
익명일 때는 로그인 게이트만 보여 무해했지만, 로그인하면 봇 계정의 개인 정보가 그대로
렌더링된다. `FORBIDDEN_ROUTE`(로그인·결제·`/admin`)와 별도로 `PRIVATE_ROUTE` 로 막는다.

**빈 상태는 프롬프트로 다룬다.** 봇 계정은 자기 데이터가 없어 `/chat` `/collection` 은
"아직 없어요" 로만 보인다. 코드로 판정하기 어려우므로 시스템 프롬프트에 "너는 데이터가 없는
봇 계정으로 보고 있다, 빈 상태뿐이면 홍보에 쓰지 말고 건너뛰어라"를 명시한다.

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

**2026-09-18 개정 — 토큰이 캡처 브라우저에도 들어간다.** 인증된 캡처(D12)를 하려면 같은 세션
토큰을 브라우저 컨텍스트의 `localStorage` 에 심어야 한다. 경계가 넓어진 것이 맞으므로 숨기지
않고 적어두고, 무엇이 그 범위를 가두는지 함께 적는다.

- **모델 컨텍스트 노출은 여전히 없다.** 모델이 받는 것은 PNG 와 파일 경로 문자열뿐이다.
  토큰은 브라우저 안에만 있고 화면에 렌더링되지 않는다.
- **라우트 허용목록이 사용처를 가둔다.** ADMIN 토큰이 있어도 `/admin` 은 `FORBIDDEN_ROUTE` 로
  막혀 카메라가 관리자 화면으로 이동할 수 없다.
- **컨텍스트는 캡처마다 버려진다.** 토큰이 캡처 사이에 남지 않는다.
- 브라우저에 심는 필드는 `accountId` `sessionToken` `role` `onboardingRequired` 로 한정한다 —
  로그인 응답을 통째로 넘기지 않는다.

남는 위험은 "봇 계정이 ADMIN 이라 토큰이 새면 관리자 API 전체가 열린다"인데, 이는 이 변경으로
새로 생긴 것이 아니라 D14가 처음부터 안고 있던 것이다(완화책은 전용 계정 단독 회전).
홍보 리소스로 스코프를 좁힌 역할은 후속으로 남긴다.

**봇 계정은 "인증 완료" 상태여야 한다.** 로그인만으로는 부족하다 — `/sell` 은 로그인 뒤에도
판매자 본인확인(`phoneVerifiedAt`)을 요구하고, `@RequiresOnboarding` 은 `legacyExempt` 를 본다.
레시피는 `scripts/seed-e2e-accounts.sh` 가 이미 갖고 있다: `status=VERIFIED`,
`phoneNumber`+`phoneVerifiedAt`(계정마다 다른 테스트 전용 번호), `legacyExempt=true`,
실패 카운터 0, 잠금·인증코드 필드 제거. 이 조건이 하나라도 빠지면 **로그인해도 게이트 화면만
찍힌다.**

### D15. 초안 생성 후 검토 요청까지는 자동, 발행은 여전히 사람이 한다

에이전트는 `POST /api/admin/promotion-posts`로 `DRAFT`를 만든 직후 `POST /{id}/submit`까지
자동으로 이어서 호출해 `PENDING_REVIEW`로 올린다(P2). `PUBLISHED`로의 전이(D3)는 건드리지
않는다 — 사람이 검토 큐에서 승인한 뒤 별도로 "발행" 버튼을 눌러야 한다. 에이전트가 로그인에
쓴 봇 전용 관리자 계정이 초안의 작성자이므로 D4(메이커-체커)에 따라 **그 계정은 자기 초안을
승인·반려할 수 없다** — 새 규칙이 아니라 기존 `SelfReviewNotAllowedException` 검사가 그대로
적용되는 것뿐이다.

### D16. 구현을 Python으로 옮기고 모델 호출을 포트 뒤에 둔다 — TS Tool Runner를 정정한다

> **2026-10-05 — D20이 대체한다.** Python·`apps/support-agent` 위치와 "검증은 코드가 한다"는
> 원칙은 유지한다. Anthropic SDK 루프·LangGraph·아래 파일 표(`ports.py`·`brain.py`·`session.py`·
> `checkpoints.py`·`runtime.py`)와 드라이런은 지웠다 — 지금 파일 구성은 D20 표를 본다. 모델 없이
> 돌려보는 수단은 드라이런 대신 `PROMOTION_GATEWAY=local`(이 기기의 CLI)과 가짜 게이트웨이 테스트다.

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

> **2026-10-05 — D20이 대체한다.** 실행 하나가 릴리스 하나만 다루고 모델 호출이 두 번(고르기 1회,
> 다듬기는 고른 장마다 1회)뿐이라 대화 전사가 없다. 체크포인트·재개·3단 제출 체인·세션 볼륨은
> 지웠다. 실패하면 Actions 를 다시 돌린다. 이미지 바이트를 디스크 밖 상태에 싣지 않는다는 규율만
> 남는다 — 캡처는 파일로 두고 게이트웨이 요청에서만 base64 로 실린다.

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

> **2026-10-05 — 캡처 대상은 프로덕션이 아니라 러너 안 데모 스택이다(D21).** 실제 이용자의 매물
> 사진·닉네임은 더 이상 찍히지 않는다. 아래 검토 항목은 그대로 두되, 데모 데이터 자체를 외부
> 홍보물에 써도 되는지(시드 이미지 출처)는 별도 확인이 남아 있다. 생성형 다듬기가 더한 검토
> 항목은 D22를 본다.

캡처 대상이 프로덕션이므로 스크린샷에 **실제 매물 사진(판매자 저작물)·닉네임·가격**이 들어간다.
사이트에 공개돼 있는 것과 그것을 회사 홍보물로 외부 플랫폼에 재게시하는 것은 다른 행위다.
같은 저장소의 `apps/support-agent`가 문의 원문 한 줄도 밖으로 내보내지 않으려 공들인 것에 비해,
홍보 쪽은 이 질문을 한 적이 없었다.

별도 스테이징이 없어 캡처 대상은 프로덕션으로 유지하되, **사람 검토를 이 문제의 게이트로
명시한다.** 검토 화면은 승인 전에 다음을 확인하게 한다.

- 첨부 이미지에 **식별 가능한 닉네임·프로필 사진·연락처**가 보이는가
- **타인의 저작물**(매물 사진)이 주제로 보일 만큼 크게 실렸는가
- 캡션이 특정 이용자나 특정 매물을 지목하는가
- (2026-09-18 추가, 인증된 캡처 이후) **봇 계정으로 로그인한 흔적**이 보이는가 —
  `관리자` 메뉴·온보딩 배너·봇 계정의 이메일이나 전화번호. 코드가 `data-promotion-hide` 로
  감추지만 새로 생긴 운영자 전용 UI에는 표식이 없을 수 있다
- 로그인해야 보이는 화면이 **빈 상태**("아직 대화가 없어요" 등)로만 찍혀 기능을 보여주지
  못하는가 — 봇 계정은 자기 데이터가 없다

하나라도 해당하면 반려하고 다른 화면으로 다시 찍게 한다. 에이전트 쪽에서는 캡션이 내부 용어를
노출하지 않는 것만 코드로 검증할 수 있고(D13), 이미지 판단은 사람이 한다 — 자동 마스킹은
오탐·미탐이 모두 조용해서 이 단계에서 신뢰할 수 없다.

### D20. GitHub 러너에서 돌고, 모델은 SSH LLM 게이트웨이의 CLI로 부른다 — D10·D16·D17을 정정한다

**정정 이유.** 운영 VM 경로는 여유 메모리 900 MB 위에서 Chromium과 장기 대화를 함께 돌려야 했고,
그래서 체크포인트·재개·3단 제출 체인이 필요했다. 모델은 API 키로 불렀는데 크레딧이 비어 있는
동안 실행되지 않았다. 둘 다 "돌려볼 수 없는 설계는 고칠 수 없다"(D16)는 같은 문제였다.

**구성.**

```
GitHub 러너 (ubuntu-latest, environment: production)
  데모 스택 기동 → 홍보 가능한 화면 전부 캡처
  ─ SSH ─▶ 게이트웨이(외부 Ubuntu 서버의 claude·codex CLI)
             claude: 캡처 전부 + diff → 고른 화면·캡션 (JSON 스키마)
             codex : 고른 화면 → 다듬은 이미지
  ◀─ JSON ─ 검증(pydantic) → 운영 API: 업로드·초안 생성·검토 요청
```

- **트리거**: `cd.yml` 성공(`workflow_run`, `main`만) → 기능 홍보. 월·수·금 01:00 UTC
  스케줄 → 서비스 소개. `workflow_dispatch`(SHA 또는 `service=true`)로 다시 돌린다.
- **게이트웨이**: `apps/support-agent/gateway/gole_llm_gateway.py`(표준 라이브러리만). stdin으로
  JSON 하나를 받고 stdout으로 JSON 하나를 돌려준다. 포트를 열지 않고 SSH 세션으로만 부른다.
  - 동시 실행은 파일 잠금으로 하나만 — 서버 메모리가 유일한 제약이다.
  - claude는 `--tools Read`와 입력 폴더 한정 `--allowedTools`, `--permission-mode dontAsk`로
    돈다. codex는 작업 폴더 한정 `workspace-write` 샌드박스다.
  - 실패 응답에는 짧은 문장만 싣고 상세는 서버의 `~/.cache/gole-llm-gateway.log`(0600)에 남긴다.
  - 응답에는 사용량(`usage`: 토큰 수·claude API 환산 금액·모델 이름·소요 시간)을 싣는다. 숫자와 모델
    이름뿐이라 공개 로그에 나가도 된다. 실패한 호출에도 싣는다 — 실패도 토큰을 쓴다.
    공개 저장소라 Actions 로그를 누구나 읽기 때문이다. codex 이미지 요청은 글 응답을 돌려주지
    않는다(홈 폴더를 읽을 수 있는 CLI가 토큰을 글로 내보내는 경로를 막는다).
  - `model`은 영숫자로 시작하는 이름만, `system`은 인자가 아니라 파일로 넘긴다(옵션 주입 차단).
- **SSH**: 호스트 키를 시크릿으로 고정하고 `StrictHostKeyChecking=yes`로 붙는다. 인증은
  `authorized_keys`의 forced command로 묶은 전용 키를 우선하고, 서버가 키 로그인을 막아 두었으면
  `sshpass -e`로 비밀번호를 쓴다(값은 환경변수로만, argv에 싣지 않는다). 비밀번호는 셸 전체
  권한이므로 `production` 환경을 `main` 전용으로 묶는다.
- **자격증명 경계**: 운영 봇 계정 자격증명은 러너의 drafter 프로세스에만 있다. 게이트웨이로
  가는 것은 데모 화면 캡처와 프롬프트뿐이다.
- **유지하는 것**: 검토 대기 5건 이상이면 시작하지 않는다, 최근 이력(반려 포함)을 시스템
  프롬프트에 넣는다(D18 — 대화가 한 번이라 동결은 필요 없다), 캡션 1~450자·고른 장 수 상한
  (`MAX_PICKS = 3`)·중복 없는 번호는 코드로 검증한다.

| 파일 | 책임 |
|---|---|
| `drafter.py` | 실행 하나: 건너뛰기 판정 → 캡처 → 고르기 → 다듬기 → 제출 |
| `drafting.py` | 출력 계약(`DraftOutput`·JSON 스키마)·프롬프트·다듬기 지시문 |
| `gateway_client.py` | SSH·로컬 게이트웨이 호출, PNG 인코딩 |
| `hands.py` | 릴리스 diff, 라우트 열거, Playwright 카메라, 백엔드 HTTP |
| `policy.py` | 라우트 가드·상한·톤 가이드·이력 렌더링 |

### D21. 데모 스택의 화면을 전부 찍고 모델은 고르기만 한다 — D12의 툴 루프를 정정한다

러너 안에 e2e 시드가 든 스택(Mongo·Redis·MinIO·API·web)을 띄우고, 열거된 공개 라우트에서
약관·온보딩 같은 비홍보 화면을 뺀 나머지를 **한 번씩** 찍는다. 시세 화면은 데모 데이터라
막는다(`PROMOTION_AGENT_DATA_SOURCE=DEMO`). 모델은 이 캡처 전부와 릴리스 diff를 한 번에 보고
최대 3장을 고르거나 건너뛴다.

- **툴 루프를 버린 이유**: 탐색이 비결정적이었고(T7), 재개를 위해 캡처를 선언적으로 만들고
  체크포인트를 둬야 했다. 화면이 수십 장 수준이라 다 찍어서 보여 주는 편이 단순하고 재현된다.
- **프로덕션을 찍지 않는 이유**: 실제 이용자의 사진·닉네임이 홍보물에 실리는 문제(D19)가
  사라지고, 아직 배포 안 된 화면을 찍을 위험도 없다 — 스택은 그 릴리스 SHA를 체크아웃해 띄운다.
- **서비스 소개는 스택을 띄우지 않는다.** 마지막 릴리스 실행이 올린 `promotion-captures`
  아티팩트(90일)를 받아 쓴다. 화면은 다음 릴리스 전까지 같기 때문이다. 받을 것이 없을 때만 찍는다.

### D22. 고른 화면은 생성형으로 다듬고, 원본을 나란히 검토한다

캡처 원본은 홍보물로 밋밋하다. codex의 이미지 생성으로 브라우저 목업·브랜드 배경을 입히되,
지시문(`drafting.POLISH_PROMPT`)이 **글자·숫자·버튼을 원본과 같게, 새 글자 금지**를 요구한다.
생성형이라 이 요구는 보장이 아니다. 그래서 사람이 대조한다.

- 다듬은 이미지가 게시 이미지가 되고, 원본은 따로 업로드해 `captures[].originalUrl`에, 지시문은
  `captures[].edit`(1000자 이하)에 남긴다. 둘은 함께 있거나 함께 없어야 한다.
- 다듬기가 실패하면 그 자리는 원본을 그대로 쓴다 — 초안 전체를 실패시키지 않는다.
- 검토 패널은 원본이 있으면 두 장을 나란히 보여 주고 **"원본과 글자·숫자·버튼이 같다"** 확인
  항목을 띄운다. 다른 확인 항목처럼 참고용이며 저장하거나 승인을 막지는 않는다 — 판단은 승인
  버튼을 누르는 사람이 진다. D19 체크리스트에 이 항목을 더한다.

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
- P11 WHEN 운영 배포가 성공하면, 시스템은 그 릴리스 SHA 하나에 대해 실행해야 하며
  `apps/web/src` 변경이 없거나 같은 `sourceCommitSha`의 게시물이 이미 있으면 모델을 부르지 않고
  건너뛰어야 한다(D11·D20).
- P12 WHEN 실행이 화면을 찍으면, 시스템은 그 SHA로 띄운 데모 스택에서 비홍보·로그인·결제·
  `/admin`·개인 라우트를 뺀 공개 라우트를 한 번씩 찍어야 하고, `GET`/`HEAD`/`OPTIONS` 외
  네트워크 요청을 막아야 한다. 데모 데이터면 시세 화면을 찍지 않아야 한다(D12·D21).
- P13 WHEN 게이트웨이가 고른 결과를 돌려주면, 시스템은 캡션 1~450자, 고른 장 수 1~3, 없는
  번호·중복 번호 금지를 코드로 검증하고 통과한 초안만 업로드 → 생성 → 검토요청해야 한다
  (D13·D15·D20).
- P14 WHEN 에이전트가 초안 제출 권한을 얻기 위해 로그인하면, 시스템은 봇 전용 ADMIN 계정의
  일반 로그인 흐름을 사용하고 그 자격증명과 세션 토큰을 게이트웨이로 보내지 않아야 한다.
  해당 계정이 자신이 만든 초안을 승인·반려하려 하면 P5와 동일하게 403으로 거부해야 한다
  (D4·D14·D20).
- P15 WHEN 실행이 시작되면, 시스템은 검토 대기(`PENDING_REVIEW`) 건수를 조회해 5건 이상이면
  캡처도 모델 호출도 하지 않고 종료해야 한다(D18).
- P16 WHEN 모델에 화면 고르기를 요청하면, 시스템은 최근 홍보 게시물 이력(반려 포함)을 시스템
  프롬프트에 넣어야 한다(D18).
- P17 WHEN 고른 화면을 다듬으면, 시스템은 원본과 지시문을 함께 제출해야 하며 다듬기가 실패한
  장은 원본을 게시 이미지로 써야 한다(D22).
- P18 WHEN 게이트웨이가 실패하면, 시스템은 응답과 러너 로그에 서버 경로·주소·CLI 출력 원문을
  싣지 않아야 한다. 상세는 서버 로그에만 남긴다(D20).
- P19 WHEN 서비스 소개 실행이 지난 캡처 아티팩트를 받으면, 시스템은 스택을 띄우지 않고 그
  캡처로 진행해야 한다. 받을 캡처가 없으면 스택을 띄워 찍어야 한다(D21).

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
- 초안 자동 생성 에이전트(D9~D22):
  - `apps/support-agent/src/gole_promotion_agent/`(Python). 파일 분해는 D20 표를 따른다.
    관측 격리는 `gole_agent_runtime.privacy`를 재사용한다. 엔트리포인트는
    `python -m gole_promotion_agent --repo --run-dir (--sha | --service) [--reuse-captures]`.
  - `apps/support-agent/gateway/` — 게이트웨이 `gole_llm_gateway.py`와 PATH를 잡는 `run.sh`.
    외부 서버에 커밋을 고정해 내려받아 설치한다. 줄바꿈은 LF로 고정한다(`.gitattributes`).
  - `apps/support-agent/pyproject.toml` — `[project.optional-dependencies] promotion`에
    Playwright·HTTP 클라이언트를 둔다. 모델 SDK는 없다.
  - `.github/workflows/promotion-agent.yml` — 트리거·데모 스택·SSH 준비·실행·아티팩트(D20·D21).
    시크릿(`production` 환경): `PROMOTION_GATEWAY_TARGET`, `PROMOTION_GATEWAY_KNOWN_HOSTS`,
    `PROMOTION_GATEWAY_KEY` 또는 `PROMOTION_GATEWAY_PASSWORD`, `PROMOTION_AGENT_ADMIN_EMAIL`·
    `PROMOTION_AGENT_ADMIN_PASSWORD`. 변수: `PROMOTION_GATEWAY_PORT`(22가 아닐 때).
  - 운영 봇 계정은 `AdminAccountSeeder`가 `gole.env`의 `PROMOTION_AGENT_ADMIN_*`로 없을 때만
    만든다. 캡처는 데모 스택의 e2e 관리자로 하므로 운영 봇에는 전화 인증이 필요 없다.
  - 백엔드 연동은 기존 REST 계약을 그대로 쓴다 — 로그인, `POST /api/v1/media/images/batch`,
    `POST /api/admin/promotion-posts`(`captures[].originalMediaKey`·`edit` 추가, D22),
    `.../{id}/submit`, `GET .../exists`, `GET .../?status=`.
  - `prompts/caption-tone.md`의 톤 가이드는 `policy.py`가 시스템 프롬프트로 승계한다(D13).
  - 운영 VM의 옛 경로(`Dockerfile.promotion`, compose `promotion-agent` 서비스,
    `infra/gcp/systemd/gole-promotion-agent.*`, `hostctl` 오버레이)는 저장소에서 지웠고,
    `bootstrap-contract.test.sh`가 다시 생기지 않는지 본다. 이미 깔린 VM의 타이머는 남아 있으므로
    운영 승인을 받아 `deploy.md`의 절차로 걷어낸다 — 이 릴리스가 나간 뒤에는 엔트리포인트 인자가
    바뀌어 그 타이머가 실패한다.

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
- 에이전트가 만든 초안은 생성 직후 `PENDING_REVIEW`까지 자동 전이한다(D15/P13).
- 봇 전용 관리자 계정은 자신이 만든 초안을 승인·반려할 수 없다(D14·D15/P14, 기존 D4 검사
  재확인).
- `originalUrl`은 사이트 경로만 받고, `originalUrl`과 `edit`은 함께 있거나 함께 없으며, `edit`은
  1000자를 넘지 않는다(D22).

에이전트 쪽은 `apps/support-agent/tests/promotion/`이 고정한다.

- 웹 변경이 없는 릴리스, 이미 초안이 있는 릴리스, 검토 대기 5건이면 **캡처도 모델 호출도 없이**
  건너뛴다(D11·D18/P11·P15).
- 비홍보 화면을 뺀 모든 후보 화면을 **한 번의 claude 요청**에 싣고, 고른 장만 codex로 다듬는다
  (D21/P12).
- 다듬기가 실패한 장은 원본이 게시 이미지가 되고 원본 대조 필드가 붙지 않는다(D22/P17).
- 없는 번호·중복 번호·캡션 길이 위반은 제출 전에 실패한다(D20/P13).
- 지난 캡처 재사용은 카메라를 부르지 않고 라우트 순서를 지키며, 매니페스트가 없으면
  실패한다(D21/P19).
- `/profile` `/notifications` 는 캡처 허용목록에서 빠지고, 데모 데이터면 시세 화면이 빠진다
  (D12·D21/P12).
- 세션 주입 스크립트는 토큰에 따옴표가 섞여도 깨지지 않고, 허용한 필드만 브라우저에 심으며,
  토큰이 비면 익명으로 조용히 넘어가지 않고 실패한다(D12/D14).
- 게이트웨이는 표준 라이브러리만 쓰고, 잘못된 요청에는 CLI를 띄우지 않으며, 옵션처럼 생긴
  `model`을 거부하고, `system`을 파일로 넘긴다(D20).
- 게이트웨이 실패 응답과 러너의 SSH 실패 메시지에 서버 경로·주소가 없다(D20/P18).
- SSH 호출은 호스트 키를 고정하고, 비밀번호는 argv가 아니라 `SSHPASS` 환경변수로만 넘기며,
  키와 비밀번호가 둘 다 있으면 키를 쓴다(D20).
- 패키지는 모델 SDK를 직접 부르지 않고, 관측 추적 환경변수가 켜져 있으면 기동을 거부한다(D20).

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
- **T7. 캡처 선택의 비결정성.** 2026-10-05 D21로 탐색은 없어졌다 — 찍는 화면은 결정적이다.
  고르기와 다듬기는 여전히 실행마다 달라질 수 있다. 이미지별 연출 지시와 모바일 폭 캡처로 품질을
  올릴지는 첫 운영 결과를 보고 정한다.
- **T8. 동적 라우트 캡처.** `[id]` 세그먼트가 든 라우트가 열거에서 빠져 매물 상세를 찍지
  못한다(D12). 실제 인스턴스를 고르는 규칙과 그 화면의 개인 데이터 취급(D19)을 함께 정해야
  하므로 별도 라운드로 미룬다.
- **T9. 발행 간격 게이트와 Threads 미디어 제약.** 발행 어댑터가 스텁인 동안은 간격 게이트가
  무작용이다(D18). T1에서 실제 연동할 때 간격 규칙과 함께 Threads의 이미지 용량·종횡비 제약,
  대체 텍스트(`PromotionPost`에 필드 없음)를 함께 정한다. 스크린샷이 PNG 원본이라 용량이
  플랫폼 상한을 넘을 수 있어 재인코딩이 필요할지 그때 판단한다.

- **T10. 운영 봇 계정 프로비저닝.** 인증된 캡처(D12)는 봇 계정이 `status=VERIFIED` +
  `phoneVerifiedAt` + `legacyExempt` 를 모두 갖춰야 동작한다(D14). 로컬·평가 DB 는 시드
  스크립트로 만들지만, **운영 계정은 mongosh 손편집으로 만들면 안 된다** — D10·D14 가 정한
  `Secret Sync` + `gole-hostctl` 경로를 따라야 하고, 운영 DB 에 전화번호를 심는 것이
  적절한지(unique 인덱스·실제 이용자 번호와의 충돌) 별도 판단이 필요하다. 이번 라운드는
  로컬 검증까지만 하고 운영 설치는 이 항목으로 남긴다.
  **2026-10-05 — 전화 인증 조건은 풀렸다.** 캡처를 데모 스택의 e2e 관리자로 하므로(D21) 운영 봇은
  초안 제출만 한다. `AdminAccountSeeder`가 `gole.env`의 `PROMOTION_AGENT_ADMIN_*`로 만든 ADMIN
  계정이면 된다. 남은 것은 Secret Manager에 값을 넣고 `Secret Sync`를 돌리는 운영 작업뿐이다.

- **T11. 봇 계정 권한 스코프.** 봇 계정이 완전한 ADMIN 이라 세션 토큰이 새면 홍보 API 가 아니라
  관리자 API 전체가 열린다(D14). 인증된 캡처로 토큰이 브라우저에도 들어가면서 노출면이
  넓어졌으므로, 홍보 리소스로 한정한 역할이나 read-only 관리자 토큰을 검토한다.

> T2(배포/CI 이벤트 기반 초안 자동 생성)는 D9~D17로 반영해 구현한다. D6이 원래 우려했던
> "사람 판단 없이 나간다"는 위험은 승인 게이트(D4)로, "내부 표현 노출"은 캡션을 커밋 메시지가
> 아니라 스크린샷 기반으로 새로 쓰게 하는 것(D9·D13)으로 해소한다.
>
> T3(이미지 첨부)는 D8로 반영해 구현 완료. `media` 업로드 파이프라인을 재사용하며 새 업로드
> 경로는 만들지 않았다.

## 관련

- [초안 품질 평가 v1](eval.md) — 첫 검토 채택률·결함·검토 부담, 페르소나 창작 기준,
  릴리스/일일 홍보 분류별 평가. [건별 기록 양식](eval-record-template.md).

- `admin-console` — 감사 로그(`RecordAdminActionUseCase`), 관리자 권한 경계, `AdminAuthInterceptor`.
- `report` — `SubmitReportUseCase`/`ManageReportsUseCase` 분리 패턴을 그대로 차용.
- `media` — 업로드·`STAGED`→`PUBLIC` 전이(`ManageMediaAssetsUseCase.replaceReferences`)를
  `listing`/`community`와 동일한 방식으로 재사용(D8).
- `.github/workflows/ci.yml`의 E2E 잡 — 데모 스택 기동·시드 절차를 그대로 차용(D21).
- `apps/support-agent` — 홍보 에이전트는 **이 패키지 안에 산다**(D16). 같은 패키지가 이미
  `gole_support_agent`(문의)·`gole_brick_filter`(사진)를 담고 있으므로 디렉터리 이름과 내용의
  불일치는 기존 선례를 따른다. 다만 **실행 패턴은 서로 다르다** — 문의는 상시 기동 gRPC
  서비스로 요청마다 반응하고 외부 LLM을 안 쓰는 반면, 홍보는 GitHub 러너에서 실행마다 한 번
  돌고 외부 서버의 CLI로 LLM을 쓴다(D9·D13·D20). 공유하는 것은 구조 관용구와 관측 격리(`gole_agent_runtime`)이지
  프로세스·이미지·자원 한도가 아니다.
- `agent-worker` — `gole_agent_worker`(AgentJobs, gRPC)에 편입하지 **않기로** 한 근거는 D16에
  있다. 트리거가 관리자 콘솔 발 요청으로 바뀌면 그 판단을 다시 볼 만하다.
