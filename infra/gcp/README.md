# GoLe GCP production

GoLe 운영 서버를 새 GCP 프로젝트로 재현하거나 다른 프로젝트로 이전하기 위한 코드다.
Terraform은 VM, 고정 IP, 방화벽을 만들고 시작 스크립트는 Docker Engine만 설치한다.
Frontend, Backend, MongoDB, Redis, MinIO, Nginx, Certbot은 모두 Docker Compose로 실행한다.

실제 비밀값은 저장소에 커밋하지 않는다. 서버의 `/etc/gole/infra.env`와
`/etc/gole/gole.env`는 권한 `0600`으로 유지한다.

## 새 프로젝트 생성

```bash
cd infra/gcp/terraform
cp terraform.tfvars.example terraform.tfvars
# project_id, domain, region/zone을 수정
terraform init
terraform apply
```

출력된 `public_ip`를 DNS의 `@`와 `www` A 레코드에 연결하고, 80/443 포트가
VM에 도달하는 것을 확인한 다음 HTTPS를 발급한다. 기본 발급자는 무료인
Google Trust Services Public CA이며 Load Balancer는 필요하지 않다.

최초 발급을 실행하는 VM에는 Google Cloud CLI가 설치되고 인증되어 있어야 한다.
Terraform 실행 주체에는 Public CA API를 켤 수 있는 `serviceusage.services.enable`
권한이 필요하다. 첫 발급 때만 `grant_gts_eab_creator=true`로 적용해 VM 전용 계정에
`roles/publicca.externalAccountKeyCreator`를 임시 부여하고, 발급 직후 다시 `false`로
적용해 회수한다. `GCP_PROJECT_ID`는 인증서를 연결할 결제 사용 설정 프로젝트를
명시한다. 자세한 계정 요구사항은
[Google Public CA 공식 절차](https://cloud.google.com/certificate-manager/docs/public-ca-tutorial)를
따른다. 이 권한들은 최초 계정 등록 후 갱신 작업에는 필요하지 않다.

```bash
gcloud compute ssh gole-production --zone asia-northeast3-a -- \
  'cd /app && GCP_PROJECT_ID=YOUR_PROJECT_ID \
  DOMAIN=gole.co.kr EMAIL=coldingcontact@gmail.com \
  infra/gcp/scripts/issue-certificate.sh'
```

Terraform이 `publicca.googleapis.com`을 먼저 활성화한다. 발급 스크립트는 서비스
활성화 권한을 요구하지 않고 Google Public CA의 일회용 EAB 값을 `0600` 임시 파일에
받아 Certbot 컨테이너에 읽기 전용으로 전달한다.
EAB 값은 출력하거나 영구 볼륨에 저장하지 않으며 계정 등록 직후 삭제한다. 이미
GTS 계정이 있으면 새 EAB를 요청하지 않는다. 기존 Let's Encrypt lineage가 있으면
같은 `gole.co.kr` lineage를 GTS로 한 번 강제 재발급한 뒤 Nginx를 재생성한다.

이후 EAB는 필요 없다. Certbot renewal 설정에 GTS ACME 서버가 저장되므로
`gole-cert-renew.timer`가 하루 두 번 Compose의 Certbot 컨테이너로 갱신 여부를
확인하고, 갱신된 인증서를 읽도록 Nginx를 무중단 리로드한다.

발급자와 SAN은 다음처럼 확인한다.

```bash
printf '' | openssl s_client -connect gole.co.kr:443 -servername gole.co.kr 2>/dev/null \
  | openssl x509 -noout -issuer -dates -ext subjectAltName
```

## 크레딧 비용 가드

Cloud Billing Budget은 비용을 강제로 차단하지 않고 실제비용 보고도 지연될 수 있다.
따라서 Budget은 보조 입력으로만 쓰고, VM 내부의 독립 스레드가 호스트 경과시간과
`ens4` 송신 바이트를 10초 주기로 계산한다. Budget `costAmount`는 게시된 세금이
포함될 수 있는 값으로 취급하고, 세전 SKU 기반 로컬 모델에만 VAT를 한 번 더한다.

2026-09-01 Cloud Billing KRW SKU 기준 현재 고정비는 다음과 같다.

- e2-custom-4-8192 CPU·RAM: `206.2740원/시간` 세전
- 100 GiB pd-balanced disk: `24.9759원/시간` 세전
- 합계: `231.249894200원/시간` 세전, `254.374883620원/시간` VAT 포함
- 외부 송신: 목적지와 관계없이 최고 단가 `318.154399937원/GiB` 세전으로 계산
- VM 정지 후 디스크·미사용 고정 IP: `45.725088879원/시간` 세전으로 계산

현재 크레딧 `395,600.60원`에 대해 다음 다중 상한을 사용한다.

| 항목 | 경고 | 위험 | 자동 정지 |
|---|---:|---:|---:|
| VAT·정지 후 비용 포함 총액 | 330,000원 | 340,000원 | 350,000원 |
| 누적 호스트 송신량 | 15 GiB | 25 GiB | 30 GiB |
| VM 생성 후 경과시간 | 1,200시간 | 1,296시간 | 1,320시간 |
| 유효한 현재 Budget 실제비용 | - | - | 320,000원 |

절대 종료 시각은 `2026-10-26 19:50 KST`다. VM 생성 시각부터 이때까지의
고정비, 송신 30 GiB, `2026-10-28 23:59:59 KST`까지의 정지 자원 비용과 VAT를
모두 합친 보수 최악값은 약 `348,868원`이고, 크레딧 여유는 약 `46,733원`이다.
VM이 정지해도 디스크와 미사용 고정 IP 비용은 계속 발생하므로 이전 완료 후 별도
삭제가 필요하다.

비용 가드는 계측 파일을 읽지 못하거나 어느 정지선이든 넘으면 Discord보다 먼저
Compute Stop API를 호출한다. 정지 상태는 영구 볼륨에 남아 수동 재시작도 다시 막는다.
전용 키 없는 서비스 계정에는 해당 subscription 소비와 해당 VM 정지 권한만 준다.
Compose heartbeat가 35초 넘게 끊기면 컨테이너가 `unhealthy`가 되고, 호스트의
`gole-cost-guard-watchdog.timer`가 30초 간격으로 이를 확인해 두 번 연속 비정상이면
VM을 직접 종료한다. 전체 배포가 실패했을 때도 기존 비용 가드가 건강하지 않으면
배포 스크립트가 Discord에 알린 뒤 같은 fail-closed 정지를 수행한다.

현재 프로젝트 또는 새 프로젝트에서 다음 스크립트로 topic, subscription, custom role,
85%·95%를 포함한 Budget 임계치를 멱등 구성한다. 출력된 실제 Budget ID를 반드시
GitHub variable에 사용한다.

```bash
PROJECT_ID=YOUR_PROJECT_ID \
BUDGET_AMOUNT_KRW=370000 \
BUDGET_START_DATE=2026-09-01 \
BUDGET_END_DATE=2026-10-28 \
infra/gcp/scripts/setup-budget-alerts.sh
```

계정을 옮길 때 GitHub repository variables의 다음 값을 함께 갱신한다.

- `GCP_PROJECT_ID`, `GCP_INSTANCE_ZONE`, `GCP_INSTANCE_NAME`
- `GCP_CREDIT_AMOUNT_KRW`, `GCP_CREDIT_DEADLINE`, `GCP_FIXED_HOURLY_COST_KRW`
- `GCP_HARD_STOP_BUDGET_ID`, `GCP_HARD_STOP_BILLING_ACCOUNT_ID`
- `GCP_HARD_STOP_PERIOD_START`, `GCP_VM_COST_START`, `GCP_HARD_STOP_AT`
- 이전과 다른 `GCP_HARD_STOP_ARM_ID`
- `GCP_COST_GUARD_*`, 네트워크·가동시간 정지선, VAT·송신·정지자원 단가
- `GCP_COST_GUARD_INTERVAL_SECONDS`, `GCP_HARD_STOP_RETRY_SECONDS`

relay 로그는 다음으로 확인한다.

```bash
docker compose -f infra/gcp/docker-compose.yml logs -f budget-relay
```

## 프로젝트 이전

기존 서버에서 백업을 만들고 새 서버에 복원한다. 백업에는 계정과 거래 데이터가 포함되므로
암호화된 안전한 경로로만 전송하고 완료 후 임시 파일을 지운다.

```bash
sudo /app/infra/gcp/scripts/backup-data.sh /var/backups/gole
sudo /app/infra/gcp/scripts/restore-data.sh /path/to/backup-directory
```

DNS를 새 IP로 바꾸기 전 `Host` 헤더로 새 서버를 검증한다.

```bash
curl -H 'Host: gole.co.kr' http://NEW_IP/actuator/health/readiness
curl -H 'Host: gole.co.kr' http://NEW_IP/
```

## 운영 원칙

### Secret Manager 환경 변수 배포

`gole-production-env`는 `/etc/gole/gole.env` 전체를 버전 단위로 보관한다.
`Secret Sync` workflow에는 평문이 아니라 숫자 버전과 control 요청 ID만 전달한다.
러너는 VM 연결 서비스 계정으로 정확한 버전을 받아 Compose 구성을 검증하고,
기존 파일을 호스트에 백업한 후 backend를 교체한다. readiness 실패 시 기존 파일로
자동 롤백한다. 시크릿 값은 workflow 입력, 명령 인자, stdout에 넣지 않는다.

control API에서 새 버전을 만들기 전에는 control PostgreSQL의 `secret_backups`
테이블에 기존 평문을 AES-256-GCM 암호문으로 먼저 저장한다. 이 DB 백업이 실패하면
Secret Manager와 GCP 서버는 수정하지 않는다.

- MongoDB, Redis, MinIO 포트는 루프백에만 바인딩한다.
- Nginx는 TLS와 라우팅만 담당한다. CORS, 헤더/본문 크기 제한, 보안 정책은 앱에서 관리한다.
- 운영 로그는 `docker compose -f infra/gcp/docker-compose.yml logs -f backend`로 본다.
- IAP SSH 허용은 우선순위 800, 외부 SSH/RDP 차단은 우선순위 900으로 적용한다.
  default VPC의 전역 허용 규칙이 남아 있어도 GoLe VM 관리 포트는 공개되지 않는다.
  필요할 때만 차단보다 높은 우선순위로 관리 IP `/32` 규칙을 임시 추가한다.
- 데이터 디스크와 인스턴스 스냅샷 정책은 프로젝트의 보존 정책에 맞게 별도 설정한다.
- PortOne API/Webhook secret과 SMTP가 준비되기 전에는 `GOLE_ENVIRONMENT=staging`,
  `PORTONE_ENABLED=false`를 유지한다.
