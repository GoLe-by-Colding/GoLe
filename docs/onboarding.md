# 온보딩

새로 합류한 사람(또는 에이전트)이 **처음부터 첫 기여까지** 따라가는 순서다.
막히면 그 단계에 적힌 문서를 펼친다.

## 0. 이 저장소의 성격을 먼저 안다

- **모노레포다.** `apps/api`(Spring Boot 4 / Java 21, 헥사고날), `apps/web`(Next.js 16, FSD),
  `apps/mobile`(Expo / React Native), `packages/core`(웹·앱 공유).
- **스펙이 먼저다(SDD).** 기능은 `.kiro/specs/<기능>/`에 requirements → design → tasks 를 쓴 뒤 만든다.
- **문서·커밋·주석은 한국어로 쓴다.** PR 도 한국어다.
- 규칙이 서로 어긋나면 [`.kiro/steering/dev-conventions.md`](../.kiro/steering/dev-conventions.md)가 이긴다.

## 1. 필요한 것

| 도구 | 버전 | 비고 |
|---|---|---|
| Node | 22 이상 | `.nvmrc` 참고. CI 도 22 |
| pnpm | 10.30.3 | `packageManager` 필드가 고정한다 |
| JDK | 21 (Temurin) | 배포 컨테이너와 맞춘다 |
| Docker | 실행 중이어야 함 | Mongo·Redis·MinIO |
| Xcode | iOS 앱을 볼 때만 | 시뮬레이터 필요 |

## 2. 첫 실행

```bash
pnpm install
cp .env.example .env            # 값은 비워둬도 로컬은 뜬다
pnpm infra:up                   # mongo(rs0) + redis + minio. 백엔드보다 먼저
pnpm dev:api                    # localhost:8080
pnpm dev:web                    # localhost:3000
pnpm dev:mobile                 # Expo. i 를 누르면 iOS 시뮬레이터
```

**`pnpm dev:api` 를 쓴다.** `cd apps/api && ./gradlew bootRun` 을 직접 쓰면 루트 `.env` 주입과
OS별 Wrapper 선택이 빠진다.

### 포트는 나눠 쓴다

사람과 에이전트가 `8080`·`3000`·`8081`을 공유한다. 띄우기 전에 `lsof -ti:8080 -ti:3000`으로
확인하고, **이미 떠 있으면 그걸 쓴다.** 내가 띄운 것은 작업이 끝나면 내린다.
Docker 는 기본으로 남긴다(`pnpm infra:down` 은 볼륨을 유지, `infra:reset` 은 볼륨을 지운다).

## 3. 품질 게이트 — CI 가 강제하는 것과 같다

```bash
pnpm --filter web format:check && pnpm --filter web lint && \
pnpm --filter web typecheck && pnpm --filter web fsd:lint && pnpm --filter web build

pnpm --filter mobile format:check && pnpm --filter mobile lint && pnpm --filter mobile typecheck

pnpm --filter @gole/core format:check && pnpm --filter @gole/core typecheck && \
pnpm --filter @gole/core check:platform

cd apps/api && ./gradlew spotlessCheck && ./gradlew cleanTest test
```

**초록을 그대로 믿지 않는다.** 스킵과 캐시가 초록으로 보인다 —
[러닝북의 "초록인데 실은 안 돈 것"](operations/runbook.md#초록인데-실은-안-돈-것) 참고.

## 4. 첫 기여

```bash
git checkout -b feat/<하려는-일>
# 스펙이 필요한 크기면 .kiro/specs/<기능>/ 부터 쓴다
# 작업 → 게이트 통과 → 커밋(한국어 제목 + "- ...함" 불릿)
gh pr create        # .github/pull_request_template.md 가 자동으로 붙는다
```

PR 템플릿에서 **"검증"과 "확인하지 못한 것"** 두 칸이 핵심이다. 돌리지 않은 게이트를
체크하지 않는다. 확인하지 못한 것을 "없음"이라고 쓰지 않는다.

`main` 은 선형 히스토리를 강제하므로 **squash 로 머지된다.** 브랜치의 커밋들은 한 개로 합쳐진다.

## 5. 더 읽을 것

- [지식 지도](index.md) — 전체 문서 위치
- [러닝북](operations/runbook.md) — 뭔가 안 될 때
- [외부 서비스 대장](external-services.md) — 계정·식별자
