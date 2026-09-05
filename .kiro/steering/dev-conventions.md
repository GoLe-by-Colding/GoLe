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

저장소 루트가 옵시디언 볼트다. 팀 전원이 옵시디언을 쓰므로 `.obsidian/` 설정(제외 필터·그래프
색상)을 공유하고, 개인 UI 상태와 설치한 플러그인은 gitignore 대상이다.

시작점은 [`docs/index.md`](../../docs/index.md)다. 새 문서를 만들기 전에 그 문서의
"볼트 사용 규칙"에서 **자리부터 정한다** — 기능 스펙은 `.kiro/specs/`, 도구 중립 규약은
`.kiro/steering/`, 돌아가는 시스템의 동작은 `docs/operations/`.

**지식 지도에 내용을 복제하지 않는다.** 복제본은 반드시 원본과 어긋나고, 어긋난 뒤에는 어느 쪽이
맞는지 아무도 모른다. 링크만 건다.

---

## 커밋 컨벤션

### 형식

```
<한국어 제목>

- <변경한 일>함
- <검증한 일>함
```

제목과 본문을 모두 한국어로 쓰고, 본문은 `- ...함` 형태의 한 줄 항목으로 실제 변경과
검증을 기록한다. Conventional Commit 영문 type은 필수로 사용하지 않는다.

### 제목 분류

| 분류 | 용도 |
|---|---|
| `기능` | 새 기능 |
| `수정` | 버그·보안 수정 |
| `개선` | 리팩터링·운영 개선 |
| `문서` | 문서 변경 |
| `관리` | 빌드·설정 변경 |

### 예시

```text
기능: 브릭 세트 즐겨찾기를 추가함

- 즐겨찾기 저장과 해제를 연결함
- 사용자 흐름과 회귀 테스트를 검증함
```

### 절대 금지

- `Co-Authored-By: Claude` 또는 AI 작성 명시 금지
- 영문 커밋 메시지 (특별한 이유 없는 한)
- `main` 브랜치 force push
- 피처 브랜치에서 `--force` 사용 (`--force-with-lease`만 허용)

---

## PR 워크플로우

```bash
# feature 브랜치 생성
git checkout -b feat/lego-set-wishlist

# 작업 후 PR 생성
gh pr create --title "기능: 브릭 세트 위시리스트를 추가함" --body "$(cat <<'EOF'
- 즐겨찾기 저장과 해제를 연결함
- 전체 검증을 통과함
EOF
)"

# main 머지 후 배포
git checkout main && git pull
# → 배포 절차 (deploy.md 참고)
```

PR 제목과 본문도 커밋과 같은 한국어 `- ...함` 형식으로 작성한다.

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
