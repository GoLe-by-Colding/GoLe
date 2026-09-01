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

출력된 `public_ip`를 DNS A 레코드에 연결한 다음 HTTPS를 발급한다.

```bash
gcloud compute ssh gole-production --zone asia-northeast3-a -- \
  'cd /app && DOMAIN=gole.co.kr EMAIL=coldingcontact@gmail.com \
  infra/gcp/scripts/issue-certificate.sh'
```

`gole-cert-renew.timer`가 하루 두 번 Compose의 Certbot 컨테이너로 갱신 여부를 확인하고,
갱신된 인증서를 읽도록 Nginx를 무중단 리로드한다.

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
