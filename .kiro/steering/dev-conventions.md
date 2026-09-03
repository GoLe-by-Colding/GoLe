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
- `NEXT_PUBLIC_API_BASE_URL=https://gole.kscold.com` (이미 빌드됨)
- 실행: `pnpm exec next start -p 3000` (--cwd /app/apps/web)

---

## 커밋 컨벤션

### 형식

```
<type>(<scope>): <한국어 설명>
```

### type

| type | 용도 |
|---|---|
| `feat` | 새 기능 |
| `fix` | 버그 수정 |
| `refactor` | 리팩토링 (기능 변경 없음) |
| `docs` | 문서 |
| `chore` | 빌드/설정 변경 |

### scope

| scope | 용도 |
|---|---|
| `(backend)` | Spring Boot 백엔드 |
| `(frontend)` | Next.js 프론트엔드 |
| `(mobile)` | React Native(Expo) 앱 |
| `(core)` | packages/core — 웹·앱 공유 코어 |
| `(infra)` | Docker, nginx, 배포 |
| `(spec)` | .kiro 스펙 문서 |

### 예시

```
feat(backend): 레고 세트 즐겨찾기 기능 추가
fix(frontend): 카탈로그 무한 스크롤 끝 감지 오류 수정
refactor(backend): LegoSetService 포트 분리 개선
feat(backend): 계정 인증 JWT 토큰 발급 구현
fix(infra): nginx gole.kscold.com HTTPS 설정 수정
```

### 절대 금지

- `Co-Authored-By: Claude` 또는 AI 작성 명시 금지
- 영문 커밋 메시지 (특별한 이유 없는 한)
- `git commit --amend` 후 force push (main 브랜치)

---

## PR 워크플로우

```bash
# feature 브랜치 생성
git checkout -b feat/lego-set-wishlist

# 작업 후 PR 생성
gh pr create --title "feat: 레고 세트 위시리스트 기능" --body "..."

# main 머지 후 배포
git checkout main && git pull
# → 배포 절차 (deploy.md 참고)
```

PR body는 영어로 작성한다 (오픈소스 기여자 프로그램 대응).

---

## 환경 변수

### 백엔드 (`apps/api/src/main/resources/application.yml` 또는 환경변수)

```
SPRING_DATA_MONGODB_URI=mongodb://localhost:27017/gole?replicaSet=rs0
SPRING_DATA_REDIS_HOST=localhost
```

### 프론트엔드

```
NEXT_PUBLIC_API_BASE_URL=https://gole.kscold.com
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
