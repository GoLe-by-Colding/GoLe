# GoLe 개발 컨벤션

## 개발 워크플로우 (SDD — Spec-Driven Development)

모든 기능 개발은 **스펙 먼저, 구현 나중** 원칙을 따른다.

```
1. .kiro/specs/<기능명>/requirements.md  작성 (요구사항 정의)
2. .kiro/specs/<기능명>/design.md        작성 (설계: 도메인 모델, API, DB 스키마)
3. .kiro/specs/<기능명>/tasks.md         작성 (구현 태스크 분해)
4. 백엔드 구현 (헥사고날 아키텍처 순서 준수)
5. 프론트엔드 구현 (FSD 레이어 순서 준수)
6. 커밋 & 배포
```

스펙 파일은 `.kiro/specs/<기능명>/` 하위에 보관한다.

---

## 백엔드 — 헥사고날 아키텍처

### 레이어 구조

```
com.gole.api.<컨텍스트>/
├── domain/
│   └── model/          # 순수 도메인 객체 (외부 의존 없음)
├── application/
│   ├── port/
│   │   ├── in/         # 인바운드 포트 (UseCase 인터페이스)
│   │   └── out/        # 아웃바운드 포트 (Repository 인터페이스)
│   └── service/        # 유스케이스 구현체 (포트 의존)
└── adapter/
    ├── in/
    │   └── web/        # REST 컨트롤러 (인바운드 어댑터)
    └── out/
        └── persistence/ # MongoDB 어댑터 (아웃바운드 어댑터)
```

### 구현 순서 (반드시 이 순서)

1. `domain/model/` — 도메인 객체
2. `application/port/in/` — UseCase 인터페이스
3. `application/port/out/` — Repository 인터페이스
4. `application/service/` — 서비스 구현
5. `adapter/out/persistence/` — Document + Repository 어댑터
6. `adapter/in/web/` — Controller + Request/Response DTO

### 컨텍스트 간 연동

- 다른 컨텍스트의 **인바운드 포트(UseCase)에만** 의존한다.
- 다른 컨텍스트의 service나 adapter를 직접 참조하지 않는다.

### MongoDB 주의사항

- `@Id` 필드에 `@Indexed(unique=true)` 추가 금지 (`_id`는 이미 unique).
- MongoDB는 replica set rs0로 실행 중 → 멀티도큐먼트 트랜잭션 사용 가능.
- Document 클래스와 Domain 모델은 반드시 분리 (매핑은 Adapter 책임).

### 빌드

```bash
cd /app/apps/api && ./gradlew bootJar --no-daemon
```

- Java 21 (Temurin), Spring Boot 4.0.6, Gradle 9.3.1
- jar: `apps/api/build/libs/api-0.0.1-SNAPSHOT.jar`
- API prefix: `/api/v1/...`

---

## 프론트엔드 — FSD (Feature-Sliced Design)

### 레이어 계층 (위 → 아래 방향으로만 의존)

```
app       # Next.js App Router, 전역 Provider
pages     # 페이지 조합 레이어
features  # 비즈니스 기능 단위
entities  # 비즈니스 엔티티 (LegoSet, Account 등)
shared    # 공통 UI, lib, 타입
```

### 금지 패턴

```ts
// ❌ cross-feature import (features끼리 직접 참조 금지)
import { something } from '@/features/other-feature'

// ❌ 상위 레이어 참조 금지 (shared에서 features 참조 금지)
import { something } from '@/features/xxx'

// ❌ 공개 API 우회 deep import 금지
import { Btn } from '@/shared/ui/Button/Button'

// ✅ 반드시 index.ts 공개 API 사용
import { Btn } from '@/shared/ui'
```

### 각 슬라이스는 반드시 index.ts 공개 API 보유

```
features/lego-set/
├── ui/           # 컴포넌트
├── model/        # zustand store, hooks
├── api/          # React Query hooks
└── index.ts      # 공개 API (외부에 노출할 것만 export)
```

### 빌드

```bash
cd /app && pnpm --filter web build
```

- Next.js 16.2.7, React 19, Node 22, pnpm 10.30.3
- 로컬 개발은 `NEXT_PUBLIC_API_BASE_URL=http://localhost:8080`을 사용함
- 운영 빌드는 `NEXT_PUBLIC_API_BASE_URL=https://gole.co.kr`을 사용하며 `main` CD에서 생성함
- 실행: `pnpm exec next start -p 3000` (--cwd /app/apps/web)

---

## 문서 · 옵시디언 볼트

**팀 문서 볼트는 별도 저장소 `GoLe-obsidian` 이다.** 기획 배경·아키텍처 해설·개발 로그처럼
코드와 따로 움직이는 글은 그쪽에 쓴다. 이 저장소에는 코드와 함께 버전이 움직여야 하는 것만
둔다 — 기능 스펙은 `.kiro/specs/`, 도구 중립 규약은 `.kiro/steering/`, 돌아가는 시스템의
동작은 `docs/operations/`.

이 저장소를 옵시디언으로 열 수는 있지만 **개인 선택이다.** `.obsidian/` 전체가 gitignore
대상이므로 설정을 공유하지 않는다.

시작점은 [`docs/index.md`](../../docs/index.md)다. 새 문서를 만들기 전에 그 문서의
"문서 작성 규칙"에서 **자리부터 정한다.**

**지식 지도에 내용을 복제하지 않는다.** 복제본은 반드시 원본과 어긋나고, 어긋난 뒤에는 어느 쪽이
맞는지 아무도 모른다. 링크만 건다.

---

## 커밋 컨벤션

**형식은 `AGENTS.md`의 "커밋 / PR" 절이 정본이다.** 여기에 옮겨 적지 않는다 — 두 곳에 두면
갈라진다. 실제로 갈라졌었다(아래 참고).

요약만 남긴다:

```
<type>(<scope>): <한국어 개조식 제목 — …함/…음>

- 무엇을 바꿨는지 한 줄
- 검증: 무엇을 어떻게 확인했는지
```

`<type>`은 `feat` `fix` `refactor` `chore` `docs` `test` `perf` `ci`.

> **폐기된 규칙 (2026-09-12)**
> 이 문서는 예전에 `기능:` `수정:` `개선:` `문서:` `관리:` 라는 한국어 분류표를 두고
> "Conventional Commit 영문 type은 필수로 사용하지 않는다", "영문 커밋 메시지 금지"라고
> 적어 두었다. `AGENTS.md`와 정면으로 어긋났고, **AGENTS.md가 gitignore 대상이라 저장소만
> 본 에이전트는 이쪽을 따랐다.** 그 결과 `main` 최근 60커밋에 형식이 셋 섞였다
> (conventional 30 · 한국어 분류 18 · 어느 쪽도 아닌 것 12). 그래서 걷어낸다.

### 절대 금지

- `Co-Authored-By: Claude` 또는 AI 작성 명시 금지
- `main` 브랜치 force push
- 피처 브랜치에서 `--force` 사용 (`--force-with-lease`만 허용)

---

## PR 워크플로우

흐름은 **작업 브랜치 → `dev` → `main`** 이다. `main`으로 바로 PR을 열지 않는다.

```bash
# 작업 브랜치 — 워크트리를 새로 만들지 않는다 (AGENTS.md "워크트리를 만들지 않는다")
git switch dev && git pull
git switch -c feat/lego-set-wishlist

# 작업 후 PR — base 는 dev
gh pr create --base dev --title "feat(web): 브릭 세트 위시리스트를 추가함" --body "$(cat <<'EOF'
- 즐겨찾기 저장과 해제를 연결함
- 검증: typecheck·build 통과, wishlist.spec.ts 3/3 통과
EOF
)"

# dev 가 쌓이면 dev -> main PR 을 따로 연다. main 푸시가 CD 를 돈다.
# → 배포 절차 (deploy.md 참고)
```

PR 제목은 커밋과 같은 형식이다. 머지된 브랜치는 원격에서도 지운다.

### PR 템플릿은 반드시 채운다

`.github/pull_request_template.md`가 자동으로 붙는다. **항목을 지우지 말고 채운다** —
해당 없으면 "해당 없음"이라고 적는다.

이 저장소의 기여자는 대부분 AI 에이전트다. 에이전트는 코드를 빠르게 만들지만 **검증하지 않은
것을 검증했다고 적기도 쉽다.** 그래서 템플릿은 "무엇을 했는가"보다 다음 두 칸을 중요하게 본다.

- **검증** — 실제로 실행한 게이트만 체크하고 숫자를 남긴다. 스킵 수까지 확인한다.
  스킵은 실패가 아니라 초록으로 보이고, Gradle 테스트는 입력이 안 바뀌면 UP-TO-DATE로
  아예 실행되지 않는다.
- **확인하지 못한 것** — 실기기·실결제·운영 데이터처럼 로컬에서 밟아보지 못한 경로를 적는다.
  "없음"이라고 쓰기 전에 한 번 더 생각한다.

시크릿은 **키 이름만** 적는다. 값은 PR 본문에 절대 넣지 않는다.

---

## 환경 변수

### 백엔드 (`apps/api/src/main/resources/application.yml` 또는 환경변수)

```
SPRING_DATA_MONGODB_URI=mongodb://localhost:27017/gole?replicaSet=rs0
SPRING_DATA_REDIS_HOST=localhost
```

### 프론트엔드

```
NEXT_PUBLIC_API_BASE_URL=http://localhost:8080
```

---

## 로컬 개발

```bash
# 백엔드
cd apps/api && ./gradlew bootRun

# 프론트엔드
cd apps/web && pnpm dev
```

로컬에서는 `http://localhost:8080` (백엔드), `http://localhost:3000` (프론트).
