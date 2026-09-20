# GoLe 배포 가이드

## 배포 기준

| 구분 | 기준 |
|---|---|
| 로컬 개발 | 현재 Mac의 `/Users/kscold/Desktop/GoLe`와 루트 `docker-compose.yml` |
| 실제 운영 | GCP Compute Engine의 단일 VM `gole-production` |
| GCP 프로젝트 | `project-72a52bf1-06aa-4519-b2c` |
| 리전/영역 | `asia-northeast3` / `asia-northeast3-a` |
| 공인 도메인 | `https://gole.co.kr` (`www`는 apex로 영구 이동) |
| 운영 방식 | `infra/gcp/docker-compose.yml` 기반 Docker Compose |
| 자동 배포 | 성공한 `main` CI의 정확한 커밋 SHA만 CD가 배포함 |

예전 `ubuntu-gole` 컨테이너와 `gole.kscold.com`은 개발·운영·DNS·배포 대상이 아니다.
해당 호스트로 SSH하거나 그곳의 컨테이너와 Nginx를 수정하지 않는다. 다른 서비스와 함께 쓰는
Mac의 전역 Nginx도 GoLe 배포를 위해 중지하거나 재시작하지 않는다.

운영 인프라의 상세한 생성·이전·기존 호스트 인수·Secret Sync·복구 절차는
[`infra/gcp/README.md`](../../infra/gcp/README.md)를 단일 기준으로 사용한다.

## 개발과 배포 흐름

운영 수정은 피처 브랜치에서 충분히 검증한 뒤 `main`에 병합한다. 피처 브랜치 push와 PR은
CI만 실행하며 운영 배포를 만들지 않는다. `main` push의 CI가 성공하면 저장소 전용
self-hosted runner가 GCP VM에서 CD를 실행한다.

```bash
cd /Users/kscold/Desktop/GoLe
git switch -c feat/<작업명>

# 로컬 검증 후
git push --force-with-lease origin feat/<작업명>
gh pr create --base main --head feat/<작업명>
```

커밋 제목과 본문은 한국어로 쓴다. 본문은 변경한 일을 `- ...함` 한 줄씩 기록한다.

```text
기능: 운영 출시 흐름을 완성함

- 단일 GCP 배포 경로를 고정함
- 운영 안전 검증을 추가함
```

PR CI가 모두 성공한 뒤 `main`에 병합한다. CD를 우회하는 서버 수동 pull·빌드·재시작은
장애 복구가 아닌 이상 사용하지 않는다. CD는 CI를 통과한 SHA를 직접 checkout하고,
Compose 갱신·내부 readiness·공개 HTTPS 확인까지 성공해야 배포 완료 SHA를 기록한다.

## 로컬 개발

```bash
cd /Users/kscold/Desktop/GoLe
docker compose up -d mongo redis minio minio-init
pnpm install --frozen-lockfile
pnpm --filter web dev

# 별도 터미널
cd apps/api
./gradlew bootRun
```

로컬 주소는 Web `http://localhost:3000`, API `http://localhost:8080`이다. 운영 Secret은
직접 복사하지 않고 `scripts/sync-dev-env.sh`로 필요한 값만 동기화한다. 이 스크립트는
로컬 환경을 강제로 development로 유지하고 운영 동작과 결제를 켜지 않는다.

```bash
cd /Users/kscold/Desktop/GoLe
bash scripts/sync-dev-env.sh
```

## 운영 접속과 확인

운영 SSH는 공개 22번 포트가 아니라 Google IAP만 사용한다.

```bash
gcloud compute ssh gole-production \
  --project project-72a52bf1-06aa-4519-b2c \
  --zone asia-northeast3-a \
  --tunnel-through-iap
```

운영 Compose 상태와 로그는 VM 안에서 아래처럼 확인한다.

```bash
cd /app
sudo docker compose \
  --env-file /etc/gole/infra.env \
  --env-file /etc/gole/gole.env \
  -f infra/gcp/docker-compose.yml ps

sudo docker compose \
  --env-file /etc/gole/infra.env \
  --env-file /etc/gole/gole.env \
  -f infra/gcp/docker-compose.yml logs --tail=100 backend frontend nginx
```

공개 상태는 비밀값이나 쓰기 요청 없이 확인한다.

```bash
curl --fail --silent --show-error https://gole.co.kr/actuator/health
curl --head https://www.gole.co.kr/
```

## DNS와 TLS

Gabia DNS에는 아래 두 A 레코드만 운영 IP로 둔다.

| 타입 | 호스트 | 값 |
|---|---|---|
| A | `@` | `35.216.80.123` |
| A | `www` | `35.216.80.123` |

TLS는 VM의 Google Trust Services 인증서 갱신 작업이 관리한다. 인증서·개인키를 저장소,
GitHub Actions 로그 또는 채팅에 복사하지 않는다. Nginx 변경은 소스 템플릿을 수정해 CD의
검증·원자적 적용·롤백 경로로만 반영한다.

## 환경 변수와 외부 자격증명

실제 비밀값은 GitHub Secrets 또는 GCP Secret Manager에만 저장한다. `.env` 파일과 토큰,
SMTP 앱 비밀번호, Discord webhook URL을 커밋하거나 채팅에 붙여 넣지 않는다.

초기 사용자 모집 기간에는 다음 정책을 동시에 유지한다.

- Frontend 결제 모드는 `disabled`로 유지함
- Backend PortOne 연동은 `false`로 유지함
- 정산 모드는 `DISABLED`로 유지함
- 이메일 인증은 production에서 Gmail SMTP 연결 검증까지 성공해야 함
- 문의 원문은 Discord에 전송하지 않고 서버가 만든 최소 운영 이벤트만 전송함

환경 갱신은 `Secret Sync` workflow와 `gole-hostctl`의 검증·트랜잭션을 통과해야 한다.
서버의 `/etc/gole/gole.env`를 편집기로 직접 고치지 않는다.

### 자격증명은 필요한 만큼만 준다

`/etc/gole/gole.env`는 Compose 스택 전체가 쓰는 **운영 전체 비밀**(DB·SMTP·PortOne·OAuth)이다.
이것을 통째로 주입받는 프로세스를 늘리지 않는다. 값이 몇 개만 필요하면 전용 파일을 만든다.

홍보 초안 에이전트가 그 예다. `/etc/gole/promotion-agent.env`에 모델 키와 봇 전용 ADMIN
계정만 둔다 — 브라우저를 띄우고 외부 모델로 데이터를 보내는 프로세스에 결제·DB 자격증명까지
줄 이유가 없다. 항목은 `apps/support-agent/.env.example`에 있다. 이 파일도 손편집 대상이
아니라 위와 같은 `Secret Sync`·`gole-hostctl` 경로를 거친다.

주입은 Discord 오버레이와 같은 방식이다. 값을 stdin으로만 넘기고, 루트가 검증한 뒤
`root:root:0600`으로 원자적으로 바꿔 끼운다. 값은 argv에도 로그에도 남지 않는다.

```bash
printf '%s\n' \
  'PROMOTION_AGENT_ANTHROPIC_ENABLED=true' \
  "ANTHROPIC_API_KEY=$ANTHROPIC_API_KEY" \
  "PROMOTION_AGENT_ADMIN_EMAIL=$PROMOTION_AGENT_ADMIN_EMAIL" \
  "PROMOTION_AGENT_ADMIN_PASSWORD=$PROMOTION_AGENT_ADMIN_PASSWORD" |
  sudo -n /usr/local/sbin/gole-hostctl promotion-agent-overlay-install
sudo -n /usr/local/sbin/gole-hostctl promotion-agent-overlay-verify
```

받는 키는 `gole-hostctl`의 `validate_promotion_agent_environment`가 고정한 목록뿐이다
(`PROMOTION_AGENT_ANTHROPIC_ENABLED` · `ANTHROPIC_API_KEY` · `PROMOTION_AGENT_MODEL` ·
`PROMOTION_AGENT_ADMIN_EMAIL` · `PROMOTION_AGENT_ADMIN_PASSWORD` ·
`PROMOTION_AGENT_SITE_URL` · `PROMOTION_AGENT_API_URL` · `PROMOTION_AGENT_DRY_RUN`).
이 파일은 루트 `docker compose`의 보간 원본이라 모르는 키는 거부한다.

## 정기 배치 — 홍보 초안 에이전트

`gole-promotion-agent.timer`가 매일 `12:00 UTC`(21:00 KST)에 oneshot 컨테이너를 띄운다.
상시 서비스가 아니라 **떴다 지는 프로세스**인 것이 자원 때문이다: 컨테이너 `mem_limit`
합계가 8 GiB 중 6,784 MB라 여유가 1.4 GB인데 Chromium 하나가 400 MB~1 GB를 쓴다. 상주
서비스는 그 몫을 계속 물고 있을 수 없다.

- compose profile `promotion`으로 분리돼 있어 `compose up`에는 뜨지 않는다.
- **`deploy.sh`의 `SERVICES` 목록에 없으므로 CD가 이 이미지를 빌드하지 않는다.** 유닛의
  `ExecStartPre`가 매 실행 전에 빌드한다(대부분 캐시 적중).
- 상태는 `gole_promotion-agent-state` 볼륨에 남는다. 체크포인트가 재기동을 넘어 살아남아야
  하므로 tmpfs로 바꾸지 않는다.
- 진단은 `journalctl -u gole-promotion-agent`와 볼륨 안의 `session.jsonl`을 본다.

### 호스트에 무엇이 깔리나

**`bootstrap-host.sh`가 유닛을 깐다.** CD는 이 유닛들을 건드리지 않는다 — 저장소에 유닛
파일을 추가하고 `main`에 올리는 것만으로는 운영 호스트에 반영되지 않고, 호스트에서
부트스트랩을 다시 돌려야 한다.

| 무엇 | 어디에 |
|---|---|
| `gole-promotion-agent.service` · `.timer` · `-failure.service` | `/etc/systemd/system/` |
| 빈 `promotion-agent.env` (`root:root:0600`) | `/etc/gole/` |
| 켜는 것 | **타이머만** `enable --now`. 서비스를 enable하면 부팅마다 한 번 더 돈다 |

- **실패는 Discord 운영 채널로 나간다.** `OnFailure=gole-promotion-agent-failure.service`가
  논리 백업이 쓰던 `notify-backup-failure.py`를 `promotion-agent` 인자로 재사용한다.
  문구는 스크립트가 고정한 목록에서만 고른다 — argv의 자유 문자열은 나가지 않는다.
- **오버레이를 채우기 전까지는 매일 밤 실패 알림이 온다.** `PROMOTION_AGENT_ADMIN_EMAIL`이
  비어 있으면 앱이 `_REQUIRED`로 죽기 때문이다. 이건 회귀가 아니라 설계된 신호다 —
  값을 넣기 전에 조용히 띄워 두고 싶으면 `PROMOTION_AGENT_DRY_RUN=true`만 먼저 주입한다.
- **`TimeoutStartSec=75min`은 `ExecStartPre`의 빌드까지 포함한 예산이다.** 앱의 최악 실행
  시간이 후보당 15분 × 최대 3건 = 45분이고(`runtime.py`·`policy.py`), 나머지가 빌드 여유다.
  한쪽을 늘리면 다른 쪽도 같이 본다.
- 상태 볼륨은 이미지가 `/var/lib/gole/promotion-agent`를 `promotion-agent`(uid 10003) 소유로
  미리 만들어 둔 덕에 쓰기가 된다. Dockerfile에서 그 줄을 빼면 Docker가 빈 볼륨을
  `root:root 0755`로 만들어 세션·체크포인트 쓰기가 전부 `EACCES`가 된다.

## DB 인덱스를 바꿀 때

MongoDB는 `auto-index-creation: true`로 **기동할 때** 인덱스를 만든다. 같은 키에 옵션이 다른
인덱스가 이미 있으면 `IndexKeySpecsConflict`(86)(이름이 같으면 `IndexOptionsConflict`(85))를 내고
Spring이 이를 다시 던져 **애플리케이션 기동 자체가 실패한다.** 인덱스 이름을 바꿔도 키 패턴이
같으면 피할 수 없다.

### 2026-09-20 — `promotion_posts.claimedSourceCommitSha` (드롭 작업 없음)

**같은 날 앞서 적은 안내를 정정한다.** `sourceCommitSha`를 `unique + sparse`로 바꾸니 옛
`sourceCommitSha_1`을 드롭하라고 적었는데, 그 설계 자체를 되돌렸다. 반려된 초안이 그 릴리스를
영구히 잠그는 결함이 있어 **점유를 새 필드 `claimedSourceCommitSha`로 분리**했고(promotion-review
D2), unique 인덱스도 그쪽으로 옮겼다. 결과적으로 **인덱스 드롭이 필요 없어졌다.**

- **`sourceCommitSha`는 평범한 `@Indexed`(비-unique)로 되돌아갔다.** `dev`를 받아 API를 띄운
  로컬·개발 DB에 이미 깔린 `sourceCommitSha_1`과 **스펙이 완전히 같으므로** 재생성이 무동작이다.
  드롭할 것이 없다. (mongo:7에서 직접 확인 — 비-unique 재생성은 통과, 옛 설계처럼 같은 키에
  `unique + sparse`를 얹으면 `IndexKeySpecsConflict`(86)로 실패한다.)
- **`claimedSourceCommitSha_1`은 새 필드라 어느 DB에도 없다.** 기동할 때 처음부터
  `unique + sparse`로 만들어진다 — 운영·개발·로컬 모두 사전 조율이 필요 없다.
- **운영(`main`)에는 `sourceCommitSha` 자체가 아직 없다.** 릴리스가 나가면 두 인덱스가 한꺼번에
  처음부터 만들어진다.
- 이미 `dropIndex("sourceCommitSha_1")`을 돌렸어도 문제없다. 다음 기동에 비-unique로 다시 생긴다.
- **다만 옛 초안은 점유가 비어 있다.** `dev`에서 이 변경 이전에 만든 문서에는
  `claimedSourceCommitSha`가 없으므로 `/exists`가 거짓이고, 그 릴리스로 초안을 한 번 더 만들 수
  있다. 운영 데이터가 없고 로컬 테스트 데이터뿐이라 백필하지 않는다 — 신경 쓰이면
  `pnpm infra:reset`으로 비운다.
- CI·E2E는 매번 새 컨테이너라 해당 없음.

## 운영 안전 규칙

- `main` CI를 통과하지 않은 SHA를 운영에 배포하지 않음
- 운영 checkout에 직접 수정하거나 `git pull`로 최신 브랜치를 따라가지 않음
- 운영 데이터 삭제·인프라 삭제·Terraform apply는 dry-run 또는 plan 검토 없이 실행하지 않음
- 비용 정지선·절대 정지 시각·Discord 예산 알림을 비활성화하지 않음
- CORS와 애플리케이션 헤더 제한은 Backend 설정을 단일 기준으로 사용함
- Nginx에는 TLS·라우팅·유한한 전송 버퍼 같은 인프라 안전 상한만 둠
- 성공 확인 전에는 이전 배포 SHA, 환경 파일, Nginx 설정을 복구 가능하게 유지함
