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
권한이 필요하고, VM의 서비스 계정에는 `roles/publicca.externalAccountKeyCreator`
역할만 필요하다. `GCP_PROJECT_ID`는 인증서를 연결할 결제 사용 설정 프로젝트를
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

## 크레딧 비용 경보

2026-09-01 Cloud Billing KRW Custom SKU 기준 e2-custom-4-8192(4 vCPU, 8 GiB)는
시간당 약 `206.2740원`, 100 GiB pd-balanced disk는 시간당 약 `24.9759원`으로
고정비 합계는 `231.2499원/시간`이다. 57일 연속 실행 예상 고정비는 약
`316,350원`이며, 기준 잔여 크레딧 `395,600.60원` 대비 약 `79,251원`을
네트워크 전송량과 변동 비용 여유로 남긴다.
계산에는 Custom Core `F10F-0364-8D62`, Custom RAM `B5E6-7318-DBF9`,
pd-balanced `5666-EFB4-5C79` SKU 단가를 사용한다.
외부 IPv4는 결제 계정별 월 720 IP-hour 무료 범위에 단일 VM만 있으면 증분 비용이 없다.

실제 사용비용은 `370,000원` custom-period Cloud Billing Budget으로 막판 여유분을
남겨 감시한다. Budget은 비용을 강제로 차단하지 않으므로 50%·75%·90%·100% 실제
비용과 매일 1회 현황을 Pub/Sub pull relay가 기존 GoLe 운영 Discord 웹훅에 보낸다.
relay는 VM 기본 서비스 계정만 사용하고 서비스 계정 키나 Discord 웹훅을 로그에 남기지 않는다.

현재 프로젝트 또는 새 3개월 프로젝트에서 다음 스크립트로 topic, subscription, 최소 IAM,
Budget 연결을 멱등 구성한다.

```bash
PROJECT_ID=YOUR_PROJECT_ID \
BUDGET_AMOUNT_KRW=370000 \
BUDGET_START_DATE=2026-09-01 \
BUDGET_END_DATE=2026-10-28 \
infra/gcp/scripts/setup-budget-alerts.sh
```

계정을 옮길 때 GitHub repository variables의 `GCP_PROJECT_ID`,
`GCP_CREDIT_AMOUNT_KRW`, `GCP_CREDIT_DEADLINE`, `GCP_FIXED_HOURLY_COST_KRW`,
`GCP_BUDGET_PUBSUB_SUBSCRIPTION`도 함께 갱신한다. relay 로그는 다음으로 확인한다.

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

- MongoDB, Redis, MinIO 포트는 루프백에만 바인딩한다.
- Nginx는 TLS와 라우팅만 담당한다. CORS, 헤더/본문 크기 제한, 보안 정책은 앱에서 관리한다.
- 운영 로그는 `docker compose -f infra/gcp/docker-compose.yml logs -f backend`로 본다.
- SSH는 IAP 대역만 허용한다. 필요할 때만 관리 IP `/32` 규칙을 임시 추가한다.
- 데이터 디스크와 인스턴스 스냅샷 정책은 프로젝트의 보존 정책에 맞게 별도 설정한다.
- PortOne API/Webhook secret과 SMTP가 준비되기 전에는 `GOLE_ENVIRONMENT=staging`,
  `PORTONE_ENABLED=false`를 유지한다.
