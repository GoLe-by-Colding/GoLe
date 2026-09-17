# AGENTS.md

이 저장소에서 작업하는 코딩 에이전트를 위한 가이드다. 도구에 중립적이며, 이 파일이 **정본**이다.
도구별 진입 파일(`CLAUDE.md` 등)은 이 파일을 가리키기만 한다 — 규칙을 고칠 때는 여기를 고친다.

**충돌하면 이 파일이 이긴다.** `.kiro/`·`docs/`·`README.md`와 어긋나는 내용이 있으면 이 파일을
따르고, 저쪽이 옳다고 판단되면 **이 파일을 고친다.** 양쪽에 서로 다른 규칙을 남겨 두지 않는다.

**세부는 여기 옮기지 않는다.** 이 파일은 매 세션 통째로 컨텍스트에 실리므로 짧게 유지한다.
아래 문서는 해당 작업을 할 때만 펼쳐 읽는다.

| 언제 | 무엇을 읽는다 |
|---|---|
| 배포·서버·pm2·CD를 건드릴 때 | `.kiro/steering/deploy.md` |
| 커밋 형식·레이어 분리·SDD 절차의 세부가 필요할 때 | `.kiro/steering/dev-conventions.md` |
| 색·타이포·톤 등 UI를 새로 만들 때 | `.kiro/steering/brand-identity.md` |
| 이미지 업로드·버킷·공개 URL을 만질 때 | `.kiro/steering/minio.md` |
| 기능 하나의 요구사항·설계 근거를 확인할 때 | `.kiro/specs/<기능>/` |
| 작업 결과를 팀 문서에 남길 때 | `GoLe-obsidian` 볼트 (아래 "기록") |
| 훅·권한 등 에이전트 하네스를 볼 때 | `.claude/settings.json` · `.claude/hooks/` |

## 프로젝트

GoLe — 레고 중고거래 마켓플레이스 모노레포. `apps/api`(Spring Boot 4 / Java 21, 헥사고날) +
`apps/web`(Next.js 16 / React 19, FSD). MongoDB(replica set `rs0`) 주 저장소, Redis 캐시/랭킹,
MinIO 이미지 저장. 문서·커밋·코드 주석은 한국어로 쓴다.

## 명령어

```bash
pnpm install                 # 최초 1회
pnpm infra:up                # mongo(rs0) + redis + minio 기동. 백엔드 실행 전 필수
pnpm dev:api                 # 백엔드 (localhost:8080, SPRING_PROFILES_ACTIVE=local 자동)
pnpm dev:web                 # 프론트 (localhost:3000)
pnpm infra:down              # 컨테이너만 내린다 (볼륨 유지)
pnpm infra:reset             # 볼륨까지 날리고 재기동
```

`pnpm dev:api`는 `scripts/gradle.mjs`를 거친다. 루트 `.env`를 읽고 OS에 맞는 Gradle Wrapper를
고르므로, `cd apps/api && ./gradlew bootRun`을 직접 쓰면 그 환경 주입이 빠진다.

### 팀은 전원 Orca를 쓴다

3명 모두 Orca CLI로 개발한다. 아래 규칙은 그 전제 위에 있다. 명령 목록은
**`orca skills get orca-cli`** 로 받는다 — 버전에 맞는 가이드가 나오므로 플래그를 기억이나
이 문서에서 추측하지 않는다.

### 워크트리를 만들지 않는다

**기본값은 "브랜치 하나, 체크아웃 하나"다.** `orca worktree create`를 스스로 부르지 않는다
(`.claude/settings.json`이 막아둔다). 새 작업은 `git switch -c <type>/<이름>`으로 판다.

모노레포라 새 체크아웃이 비싸고, 비싼 만큼 조용히 고장 난다:

- `.env`·`apps/web/.env.development.local`이 gitignore라 **새 체크아웃은 백엔드를 못 띄운다.**
- `node_modules`·Gradle 캐시를 새로 깔아야 한다.
- 포트가 `8080`·`3000` 하나씩이라 **두 체크아웃이 동시에 dev 서버를 못 띄운다.**
- `main` 같은 브랜치가 워크트리에 묶이면 원래 체크아웃에서 체크아웃이 막힌다
  (`fatal: 'main' is already used by worktree at ...`). 2026-09-11에 실제로 그렇게 막혔다.

사람이 명시적으로 요청할 때만 만든다. 만들었으면 그 턴 안에 정리까지 합의한다.

### 새 세션은 지금 체크아웃에 띄운다

일을 나눠 돌리고 싶으면 **새 워크트리가 아니라 새 터미널**이다.

```bash
orca terminal create --worktree active --command "claude" --json
orca terminal wait   --terminal <handle> --for tui-idle --timeout-ms 60000 --json
orca terminal send   --terminal <handle> --text "<작업 브리핑>" --enter --json
```

`wait` 결과의 `satisfied: true`를 보고 나서 보낸다. 시작 중인 TUI에 던진 프롬프트는 사라진다.
`worktree create --agent`는 새 체크아웃을 만드므로 쓰지 않는다.

**같은 체크아웃을 공유한다는 뜻이다.** 그래서 나누는 기준은 "백엔드/프론트"가 아니라 **경로**다:

- 한 체크아웃은 **한 브랜치**다. 서로 다른 브랜치가 필요한 작업은 세션을 나누지 말고 순서대로 한다.
- 같은 브랜치 안에서 나눌 때는 **만지는 경로를 겹치지 않게** 브리핑에 적는다
  (예: 한쪽 `apps/api/**`, 다른 쪽 `apps/web/**`). 겹치면 서로의 편집을 덮어쓴다.
- `pnpm install`·`git commit`·브랜치 전환처럼 **저장소 전체에 영향을 주는 명령은 한 세션만** 한다.
- dev 서버는 세션당 하나가 아니라 **머신당 하나**다. 이미 떠 있으면 그걸 본다.

### 로컬 서버 — 켠 쪽이 끄고 끝낸다

에이전트와 사람이 같은 포트(`8080`·`3000`)를 나눠 쓴다. 양쪽이 각자 띄우면 두 번째가 조용히
죽거나, 더 나쁘게는 Next가 `3001`로 비켜 떠서 엉뚱한 화면을 검증하게 된다. 그래서 규칙을 둔다.

- **켜기 전에 확인한다.** `lsof -ti:8080 -ti:3000`으로 이미 떠 있는지 본다. 떠 있으면 그걸
  쓴다 — 두 번째 인스턴스를 띄우지 않는다.
- **남이 띄운 것은 끄지 않는다.** 이 턴에서 내가 시작하지 않은 프로세스는 확인 없이 죽이지
  않는다. 정리가 필요하면 물어본다.
- **Orca 터미널에 띄운다.** 아래 "서버 로그는 터미널 2개로 본다" 참고. 사람이 탭에서 로그를
  직접 보므로, Orca 터미널에 띄운 dev 서버는 보고 후 남겨도 된다.
- **백그라운드 셸로 띄웠으면 마무리 전에 끈다.** 사람 눈에 안 보이는 프로세스는 이 턴에서
  시작한 것을 전부 내린다. "다음에 정리하겠습니다"로 넘기지 않는다.
- **Docker는 기본으로 남긴다.** 사람이 이어서 쓸 수 있다. 내려야 하면 `pnpm infra:down`을
  쓴다 — `infra:reset`은 볼륨을 지우므로 시드 데이터가 날아간다.
- **보고에 상태를 한 줄 남긴다.** 무엇을 띄웠고 무엇이 남아 있는지 적는다.

이 규칙은 문서만이 아니라 **훅으로 강제된다**: `.claude/hooks/dev-server-mark.sh`(PreToolUse)가
에이전트가 띄운 서버를 표시하고, `dev-server-guard.sh`(Stop)가 안 끄고 끝내려 하면 턴을 막는다.
Orca 터미널에 띄운 것은 표시하지 않으므로 오탐이 나지 않는다.

### 서버 로그는 터미널 2개로 본다

**살아 있는 프로세스(dev 서버·워처·에뮬레이터)는 에이전트 백그라운드 셸이 아니라 Orca
터미널에 띄운다** — 사람이 로그를 눈으로 봐야 하기 때문이다. 단발성 명령(`git status`,
`curl`, 테스트 1회)은 그냥 셸로 돌린다.

```bash
orca terminal create --worktree active --title "API :8080" --command "pnpm dev:api" --json
orca terminal create --worktree active --title "WEB :3000" --command "pnpm dev:web" --json

orca terminal list  --worktree active --json
orca terminal read  --terminal <handle> --json   # 로그는 result.terminal.tail 에 담겨 온다
orca terminal send  --terminal <handle> --text "r" --enter --json
orca terminal close --terminal <handle> --json
```

`--worktree active`가 "지금 이 체크아웃"이다. 인프라(`pnpm infra:up`)는 단발성이므로 셸로 돌린다.

웹 화면은 Orca 내장 브라우저로 본다.

```bash
orca tab create --url "http://localhost:3000" --json
orca snapshot --json      # 접근성 트리 — 창 포커스 없이도 동작한다
orca screenshot --json    # 탭이 화면에 보여야 한다. 아니면 browser_error 로 실패
```

`orca screenshot`이 포커스 때문에 실패하면 `orca snapshot`으로 내용을 확인한다. 이미지가 꼭
필요하면 Playwright로 따로 찍는다 — `apps/web`에는 `@playwright/test`만 있으므로
`import { chromium } from "@playwright/test"` 로 가져온다(`playwright` 패키지는 없다).

모바일은 `orca emulator attach "iPhone 17 Pro" --json`.

작업 상태는 워크스페이스 카드에 남긴다 — `orca worktree set --worktree active --comment "..."`.

### 품질 게이트 (CI가 강제하는 것과 동일)

```bash
# 프론트 — 이 순서 그대로 CI가 돈다
pnpm --filter web format:check   # prettier
pnpm --filter web lint           # eslint + boundaries (레이어 의존 방향)
pnpm --filter web typecheck      # tsc --noEmit
pnpm --filter web fsd:lint       # steiger (FSD 구조)
pnpm --filter web build

# 워크플로 — CI 의 Infra 잡이 같은 이미지로 돌린다. 고치기 전에 여기서 먼저 본다.
docker run --rm -v "$PWD:/repo" -w /repo \
  rhysd/actionlint@sha256:b1934ee5f1c509618f2508e6eb47ee0d3520686341fec936f3b79331f9315667

# 백엔드
cd apps/api && ./gradlew spotlessCheck   # palantir-java-format. 실패 시 spotlessApply
cd apps/api && ./gradlew test            # 단위 테스트 (*IntegrationTest 제외)
cd apps/api && ./gradlew integrationTest # Testcontainers. Docker 필요
```

### 단일 테스트

```bash
# 백엔드 — 클래스/메서드 단위
cd apps/api && ./gradlew test --tests 'com.gole.api.order.*.OrderServiceTest'
cd apps/api && ./gradlew test --tests '*.OrderServiceTest.refund_neverCreatesSettlement'

# E2E — 파일 단위 / 제목 매칭
pnpm --filter web e2e tests-e2e/purchase.spec.ts
pnpm --filter web e2e -g "결제"
pnpm --filter web e2e:ui
```

**메서드명은 영문(`place_rejectsSelfPurchase`), 설명은 한국어 `@DisplayName`이다.** `--tests`가
받는 건 메서드명이므로 한국어를 넣으면 `No tests found for given includes`로 **빌드가 실패한다**
(조용히 넘어가지는 않는다). E2E의 `-g`는 반대로 제목을 보므로 한국어가 맞다.

⚠️ **`.github/workflows/*.yml`을 고쳤으면 actionlint를 돌린다.** CI 의 `Infra` 잡이
`GitHub Actions contract` 단계에서 같은 다이제스트의 이미지로 검사하고, **shellcheck 까지
돌린다** — `run:` 블록의 따옴표 누락(SC2046) 같은 것이 여기서 걸린다. YAML 파싱만 통과했다고
초록이 아니다.

⚠️ **Gradle 테스트는 입력이 안 바뀌면 UP-TO-DATE로 스킵된다.** 통과했다고 판단하기 전에
`--rerun-tasks`를 붙이거나 `cleanTest test`로 실제 실행 여부를 확인한다.

### E2E 사전 조건

`tests-e2e/`(Playwright). web dev 서버는 Playwright가 자동 기동하지만 **인프라와 API 서버는
직접 띄워야** 한다.

**`pnpm dev:api`로 띄우면 안 된다.** 그건 `local` 프로필이라 `gole` DB를 보는데, 시더는
`gole_e2e`에 심는다. 그 상태로 돌리면 **15건이 실패하는데 원인이 코드가 아니라 환경이다.**
`ci.yml`의 E2E 잡과 같은 환경을 맞춰야 한다:

```bash
pnpm infra:up

# API — e2e 프로필 + CI 와 같은 저장소·스토리지 설정 (Orca 터미널에 띄운다)
MONGODB_URI='mongodb://localhost:27017/gole_e2e?replicaSet=rs0' MONGODB_DATABASE=gole_e2e \
STORAGE_S3_ENDPOINT=http://localhost:9000 STORAGE_S3_BUCKET=gole-e2e \
STORAGE_PUBLIC_BASE_URL=http://localhost:8080 MANAGEMENT_HEALTH_MAIL_ENABLED=false \
pnpm dev:api:e2e

MONGO_DB=gole_e2e REDIS_DATABASE=15 pnpm e2e:seed   # 멱등

# 테스트 — NEXT_PUBLIC_* 는 Playwright 가 띄우는 web dev 서버가 읽는다
E2E_WITH_BACKEND=1 \
NEXT_PUBLIC_API_BASE_URL=http://localhost:8080 \
NEXT_PUBLIC_PAYMENT_MODE=portone-test \
NEXT_PUBLIC_PORTONE_STORE_ID=store-test \
NEXT_PUBLIC_PORTONE_CHANNEL_KEY=channel-kakaopay-test \
NEXT_PUBLIC_PORTONE_CARD_CHANNEL_KEY=channel-card-test \
pnpm --filter web e2e
```

기대치는 **205 passed / 13 skipped** 근처다(2026-09-12 확인). 숫자가 크게 다르면 코드가 아니라
환경을 먼저 의심한다.

- 쓰기 플로우(`create-listing`, `purchase`)는 서버가 검증하는 실제 세션이 필요하므로
  `pnpm e2e:seed`를 한 번 돌린다(멱등).
- `E2E_WITH_BACKEND=1`이 없으면 관리자 API 가드 테스트가 **조용히 skip**된다.
- `NEXT_PUBLIC_PORTONE_*`·`NEXT_PUBLIC_PAYMENT_MODE`가 없으면 `portone-request.spec.ts` 4건이
  실패한다. 결제 계약 테스트가 그 값을 읽기 때문이다(실제 키가 아니라 테스트 채널 값이다).
- `E2E_BASE_URL`을 주면 배포 대상 읽기전용 검증이 되고 쓰기 플로우는 자동 skip.
- 브라우저 테스트는 항상 `NEXT_PUBLIC_PAYMENT_MODE=stub`으로 강제된다(실제 결제창은 자동화 불가).
  `portone-request.spec.ts`만 Node 프로세스에서 돌아 실제 모드 값을 읽는다.

## 아키텍처

### 백엔드 — 헥사고날, 컨텍스트당 한 세트

`com.gole.api.<컨텍스트>/`에 `domain/` → `application/port/{in,out}` + `application/service/` →
`adapter/{in/web, out/...}`. 컨텍스트: account, admin, catalog, chat, collection, community,
discovery, listing, media, notification, order, pricing, report, review.

**새 기능은 반드시 이 순서로 만든다**: domain/model → port/in → port/out → service →
adapter/out/persistence → adapter/in/web.

**컨텍스트 간 연동은 상대의 인바운드 포트(UseCase)에만 의존한다.** 상대 service·adapter를 직접
참조하지 않는다. 구현 형태는 "내 아웃바운드 포트를 상대 UseCase로 위임하는 어댑터"다 —
`order/adapter/out/listing/ListingReservationAdapter.java`가 표준 예시(`ListingReservationPort`를
listing의 `ReserveListingUseCase` 등으로 구현하고, 상대 도메인 객체를 내 컨텍스트가 필요한
최소 데이터로 환원해 결합을 끊는다). order는 이 방식으로 listing·pricing·payment·settlement·
notification과 붙는다.

`common/`은 컨텍스트가 아니라 횡단 관심사다: `aop/`(유스케이스 로깅·운영 신호),
`exception/`(DomainException 계열) + `web/GlobalExceptionHandler`(→ `{code, message}` 응답),
`config/`(Mongo 트랜잭션, 스케줄링, CORS, 운영 설정 가드), `operations/`(Discord 알림 발행).

MongoDB 규칙: `@Id`에 `@Indexed(unique=true)` 금지, Document 클래스와 도메인 모델은 분리하고
매핑은 어댑터 책임, rs0라서 멀티도큐먼트 트랜잭션 사용 가능.

인증은 불투명 세션 토큰을 `Authorization: Bearer` 헤더로 보낸다(프론트는 localStorage
`gole.session`에 보관 — `shared/api/session-auth.ts`). 관리자 경로는 `AdminAuthInterceptor`가 가드.
API 프리픽스는 `/api/v1/...`, 관리자만 `/api/admin`.

### 프론트 — FSD

`app → views → widgets → features → entities → shared` 단방향. FSD의 "pages" 레이어는 Next의
Pages Router와 혼동을 피하려고 **`views`**로 명명했다. 슬라이스 내부는
`ui/ model/ api/ lib/ config/ + index.ts`.

- 상위 → 하위만 import. 같은 레이어의 다른 슬라이스 직접 참조(cross-import) 금지 — 필요하면 상위에서 조합.
- 슬라이스 외부에서는 `index.ts` 공개 API로만 접근. deep import 금지(`@/shared/ui/Button/Button` ❌).
- eslint-plugin-boundaries + steiger가 이 규칙을 CI에서 강제한다. 우회하지 말고 구조를 고친다.
- tsconfig strict 풀세트(`noUncheckedIndexedAccess`, `exactOptionalPropertyTypes`).

## 개발 워크플로우 (SDD)

스펙 먼저, 구현 나중. 기능 단위로 `.kiro/specs/<기능>/`에 `requirements.md` → `design.md` →
`tasks.md`를 쓴 뒤 백엔드 → 프론트 순으로 구현한다.

`.kiro/`라는 이름은 이 방식을 도입할 때 쓰던 Kiro(AWS의 스펙 주도 IDE)에서 왔다. 디렉터리
구조와 `specs`/`steering` 구분이 그 도구의 규약이다. **Kiro 자체는 더 이상 쓰지 않는다** —
지금은 스펙·문서 보관 디렉터리일 뿐이라 마크다운만 들어 있다. Kiro 런타임 설정
(`settings.json`, `agents/`)은 2026-08-29에 지웠다 — 다시 만들지 않는다.

## 기록 — 옵시디언 볼트에 남긴다

팀 공용 문서는 코드 옆이 아니라 **`gole-project/GoLe-obsidian`**(별도 private 저장소)에 있다.
`.kiro/`가 "이 기능을 왜 이렇게 설계했나"라면, 볼트는 "우리가 무엇을 왜 결정했나"다.

**의미 있는 작업을 끝내면 볼트에 한 편 남긴다.** 코드만 올리고 끝내지 않는다.

| 무엇을 했나 | 어디에 쓴다 |
|---|---|
| 기능·수정을 하나 끝냈다 | `06_개발로그/YYYY-MM-DD_제목.md` (새 파일) |
| 아키텍처·도메인 구조가 바뀌었다 | `02_아키텍처/` · `03_백엔드/02_도메인/` 해당 문서 갱신 |
| 화면·디자인 시스템이 바뀌었다 | `04_프론트엔드/` 해당 문서 갱신 |
| 결정이 미뤄졌거나 뒤집혔다 | `01_기획/기획 갭 목록.md` |
| 운영에서 문제가 났다 | `07_이슈기록/` |

- 파일 앞에 `tags` · `created` · `updated` 프런트매터를 넣는다. 기존 문서를 열어 형식을 맞춘다.
- 문서끼리는 `[[위키링크]]`로 잇는다. 고아 문서를 만들지 않는다.
- **추측으로 쓴 문단은 그렇게 표시한다.** 볼트 PR 템플릿이 게이트 대신 근거를 묻는 이유다 —
  어느 코드 경로·커밋·화면에서 확인했는지 적는다.
- 볼트는 **별개 저장소**다. 커밋·푸시를 거기서 따로 한다(`dev` → `main`).

## CI / 배포

| 워크플로 | 트리거 | 내용 |
|---|---|---|
| `ci.yml` | `main` 푸시 · PR | Frontend / Backend / E2E 3잡. 위 품질 게이트와 같다 |
| `cd.yml` | `ci.yml` **성공** + `main` **푸시**일 때만 | self-hosted 러너에서 `deploy.sh all` |
| `e2e.yml` | 매일 03:00 KST | 배포 사이트 읽기전용 스모크(`E2E_BASE_URL`) |
| `production-health.yml` | 수동 실행만 | 운영 헬스체크 |
| `release-tag.yml` | `ci.yml` **성공** + `main` **푸시** | CalVer 태그 + 릴리스 노트 |
| `pr-automation.yml` | PR 열림·갱신 | 라벨·담당자 자동 지정 |
| `pr-convention.yml` | PR 열림·제목 수정 | 제목·브랜치·대상 규약 검사 |
| `sync-dev.yml` | `ci.yml` **성공** + `main` **푸시** · 수동 | `main` → `dev` 자동 역병합 |

- **CI가 깨지면 CD는 아예 안 돈다.** PR에서 초록이어도 배포되지 않는다 — `main` 푸시여야 한다.
  반대로 문서만 고친 커밋도 `main`에 올리면 배포가 한 번 돈다.
- **배포 대상은 `https://gole.co.kr`다**(`www`는 apex로 영구 이동). `cd.yml`이
  `GOLE_ENVIRONMENT: production`을 넘긴다. DNS는 GCP VM `gole-production`의 고정 IP를 가리킨다.
  `gole.kscold.com`은 **은퇴한 예전 호스트**이고 배포·DNS·CD 대상이 아니다 — 그쪽이 주는
  `410 Gone`은 장애 신호가 아니라 의도적으로 닫아둔 응답이므로 운영 상태 판단에 쓰지 않는다.
  **"배포됐다"와 "기능이 열렸다"는 다르다** — 프론트가 `NEXT_PUBLIC_PORTONE_*` 없이 빌드돼
  결제 버튼은 disabled다(회귀가 아니라 의도된 구성).
- **CI가 조용히 건너뛰는 것이 있다.** `GOLE_ADMIN_EMAIL`/`GOLE_ADMIN_PASSWORD`가 `ci.yml`에
  없어서 관리자 권한 경계 E2E가 항상 skip된다. 실패가 아니라 skip이라 초록으로 보인다 —
  "CI 통과 = 권한 검증됨"이 아니다.

## 커밋 / PR

형식은 **제목 한 줄 + 불릿**으로 고정한다. 산문 단락을 쓰지 않는다.

```
<type>(<scope>): <한국어 개조식 제목 — …함/…음>

- 무엇을 바꿨는지 한 줄
- 무엇을 바꿨는지 한 줄
- 검증: 무엇을 어떻게 확인했는지
```

- `<type>`은 conventional commit 타입을 쓴다 — `feat` `fix` `refactor` `chore` `docs` `test` `perf` `ci`.
- `<scope>`는 바꾼 곳 — `api` `web` `mobile` `infra` `brand` 또는 컨텍스트명. 여럿이면 쉼표로 잇는다.
- 제목은 한 줄, 한국어 개조식으로 끝낸다: `fix(web): 모바일 히어로에서 두 문장이 붙던 것을 고침`.
- 본문은 **불릿(`- `)만** 쓴다. 한 불릿은 되도록 한 줄, 길어도 두 줄을 넘기지 않는다.
- **마지막 불릿은 검증**이다: `- 검증: typecheck·build 통과, home.spec.ts 4/4 통과`.
- `증상:` `원인:` `수정:` 같은 소제목을 달고 문단으로 설명하지 않는다. 배경이 길면 PR 본문이나 이슈에 적는다.

**`기능:` `수정:` `개선:` `문서:` `관리:` 같은 한국어 분류 제목은 폐기했다.** 저장소 이력에
셋 이상의 형식이 섞여 있는데(`인프라:` `보안:` 처럼 어느 문서에도 없는 것까지), 앞으로는 위
conventional 형식 하나만 쓴다. `.kiro/steering/dev-conventions.md`의 옛 분류표는 걷어냈다.

브랜치는 `<type>/<kebab-case>` — `feat/card-payment`, `fix/profile-stuck-loading`.
`temp`·`tmp`·도구가 자동 생성한 이름(`<계정>/...`)을 쓰지 않는다. 머지되면 원격 브랜치도 지운다.

### 흐름은 하나뿐이다

```
<type>/<이름>  ──PR──>  dev  ──릴리스 PR──>  main  ──> 태그 + CD
   작업 브랜치            통합             운영 기준선
```

- **작업 브랜치는 `dev`로 보낸다.** `main`으로 바로 PR을 열지 않는다.
- **`main`은 `dev → main` 릴리스 PR로만 연다.** dependabot도 예외가 아니다 —
  `dependabot.yml`의 `target-branch: dev`로 봇 PR도 `dev`로 온다(2026-09-12 변경).
- 머지는 **squash**다. `main`의 커밋 하나 = PR 하나. `dev`는 그 squash들과
  `chore(sync)` 역병합이 섞인다.
- **`main` 푸시가 릴리스다.** CI 성공 시 `release-tag.yml`이 CalVer 태그(`v2026.09.12-1`)와
  릴리스 노트를 만들고, `cd.yml`이 운영 배포를 시도한다.
  릴리스 노트는 `git log`가 아니라 **릴리스 PR에 담긴 커밋 목록**에서 뽑는다 — `main`은
  squash로만 움직여서 커밋 범위에 릴리스 커밋 하나밖에 없기 때문이다.
- 릴리스 후 **`main` → `dev` 역병합은 `sync-dev.yml`이 자동으로 한다.** squash 때문에 두
  브랜치의 이력이 갈라지므로 빼먹으면 다음 릴리스 PR에 충돌이 쌓이는데, 사람이 기억할 일이
  아니라 판단했다. **충돌이 나면 자동으로 풀지 않고 이슈를 열고 멈춘다** — 잘못 푼 역병합은
  CI를 통과하면서 코드를 조용히 되돌리기 때문이다.
  충돌이 **두 번 연속** 나면 설계 문제로 보고 `required_linear_history`를 끄는 쪽을 검토한다.

### PR을 열면 자동으로 붙는 것

`pr-automation.yml`이 라벨과 담당자를 채운다. **손으로 붙이지 않아도 된다.**

| 무엇 | 어디서 읽나 |
|---|---|
| 종류 (`기능`·`버그수정`·`리팩터링`·`설정`·`문서`·`테스트`·`성능`·`CI`) | 제목의 type, 없으면 브랜치 접두사 |
| 영역 (`백엔드`·`프론트`·`모바일`·`공유코어`·`인프라`) | 바뀐 경로 |
| `배포영향` | base가 `main`일 때 |
| 담당자 | PR 작성자 (이미 지정돼 있으면 건드리지 않는다) |

`pr-convention.yml`이 제목·브랜치 이름·PR 대상을 검사한다. **필수 체크가 아니라 머지를
막지는 않지만**, 어긋나면 이유를 남긴다. 라벨은 전부 한국어다 — 영어 라벨은 2026-09-12에
이름을 바꿨다(기존 PR 연결은 유지).

dependabot PR은 `GITHUB_TOKEN`이 읽기 전용이라 이 워크플로가 라벨을 못 붙인다.
대신 `dependabot.yml`의 `labels:`가 봇 쪽에서 직접 붙인다.

- **`Co-Authored-By: Claude` 등 AI 작성 표기를 붙이지 않는다.**
- 백엔드/프론트는 레이어별로 커밋을 분리한다.
- main 브랜치에 `--amend` 후 force push 금지.
### PR

PR 제목은 커밋과 같은 형식이다. 본문은 `.github/pull_request_template.md`를 **지우지 말고 채운다**.

- **실행한 게이트만 체크한다.** 돌리지 않은 줄은 비워 두고 "확인하지 못한 것"에 적는다.
  숫자를 함께 남긴다 — `백엔드 999건 0실패 0스킵`.
- **"확인하지 못한 것"을 "없음"으로 비우지 않는다.** 실기기·실결제·운영 데이터·타 플랫폼처럼
  로컬에서 재현 못 한 것, 코드는 넣었지만 실행 경로를 밟지 않은 것을 적는다.
- **스킵 수를 확인한다.** 스킵은 실패가 아니라 초록으로 보인다. 관리자 E2E는 `GOLE_ADMIN_EMAIL`/
  `PASSWORD`가 없으면 조용히 skip되고, Gradle 테스트는 입력이 안 바뀌면 UP-TO-DATE로 실행조차 안 된다.
- **시크릿은 키 이름만** 적는다. 값은 어떤 칸에도 넣지 않는다.
- `.kiro/specs/<기능>/` 경로를 "관련 스펙"에 적는다. 스펙 없이 만들었으면 그 이유를 적는다.

문서 볼트(`GoLe-obsidian`)에도 별도 템플릿이 있다. 거기서는 게이트 대신 **근거**를 묻는다 —
어느 코드 경로·커밋·화면에서 확인했는지, 추측으로 쓴 문단은 어디인지.

## 알아둘 함정

- **결제는 기본이 스텁이다.** `PORTONE_ENABLED`가 없으면 `StubPaymentGatewayAdapter`가 모든 결제를
  무료 승인한다(주문·정산 흐름은 진짜로 돈다). 운영에서 스텁 기동은 `PaymentConfigurationGuard`가 막는다.
- **`NEXT_PUBLIC_*`은 빌드 타임에 번들로 인라인된다.** pm2 `--update-env`로는 반영되지 않으므로
  결제 키를 바꾸면 프론트를 재빌드해야 한다. 소셜 로그인 키(백엔드 런타임)와 다르게 동작하는 유일한 항목.
- **카드 결제는 TEST 채널만 열려 있다.** `PORTONE_CARD_CHANNEL_KEY`가 가리키는 `kscold-kg`는
  이니시스 공용 테스트 MID(`INIpayTest`)라 실계약이 없다. `PORTONE_CHANNEL_TYPE`은 채널별이
  아니라 전역 하나이므로, 카드만 LIVE로 열 수 없다 — 카카오페이도 함께 전환해야 한다.
- 결제는 verify-on-server다. 서버가 포트원에 직접 재조회해 `status=PAID`·금액·상점·채널·결제수단
  (`EASY_PAY`/`KAKAOPAY`)을 모두 확인한 뒤 자금 보유로 전이한다. 불일치는 자동 실패시키지 않고
  `PAYMENT_REVIEW`로 보존한다. 결제수단을 넓히려면 어댑터의 그 검증도 함께 넓혀야 한다.
- `./gradlew build`의 test는 `*IntegrationTest`를 제외한다. 통합 테스트는 CI와 명시적 실행에서만 돈다.
- `spring.mail.host`가 정의되면 Spring이 MailHealthIndicator를 자동 등록하고, SMTP가 없는 환경에서는
  그 지표 하나가 `/actuator/health` 전체를 503으로 만든다 → `MANAGEMENT_HEALTH_MAIL_ENABLED=false`.
- 이미지 공개 주소는 MinIO가 아니라 API 원점이다(`STORAGE_PUBLIC_BASE_URL`). MinIO 주소를 넣으면
  브라우저 CSP `img-src`가 막는다.
- 개인 환경 파일은 `.env`(Docker Compose + 백엔드), `apps/web/.env.development.local`(Next dev 전용).
  둘 다 gitignore 대상이고 각각 `.env.example`을 복사해 쓴다.
