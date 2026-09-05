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

## 운영 안전 규칙

- `main` CI를 통과하지 않은 SHA를 운영에 배포하지 않음
- 운영 checkout에 직접 수정하거나 `git pull`로 최신 브랜치를 따라가지 않음
- 운영 데이터 삭제·인프라 삭제·Terraform apply는 dry-run 또는 plan 검토 없이 실행하지 않음
- 비용 정지선·절대 정지 시각·Discord 예산 알림을 비활성화하지 않음
- CORS와 애플리케이션 헤더 제한은 Backend 설정을 단일 기준으로 사용함
- Nginx에는 TLS·라우팅·유한한 전송 버퍼 같은 인프라 안전 상한만 둠
- 성공 확인 전에는 이전 배포 SHA, 환경 파일, Nginx 설정을 복구 가능하게 유지함
