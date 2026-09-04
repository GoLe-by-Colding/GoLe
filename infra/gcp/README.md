# GoLe GCP 운영 인프라

이 디렉터리는 재해복구나 정상적인 계정·프로젝트 이전 시에도 단일 운영 서버를
재현하기 위한 코드다. 운영 대상은 GCP의 `gole-production` 한 대뿐이다. Mac은 localhost
개발 환경이고, 예전 Ubuntu whale 및 `gole.kscold.com`은 배포 대상이 아니다.

Terraform은 VM, 고정 IP, 방화벽, 전용 runtime 서비스 계정, payload 없는 Secret Manager
container, Pub/Sub/Budget 권한, 삭제 보호와 3일 snapshot schedule을 선언한다. 앱은
`infra/gcp/docker-compose.yml`로만 실행한다. 외부 이미지와 Dockerfile base는 digest로,
Ubuntu boot image는 현재 운영과 같은 exact image
`ubuntu-2404-noble-amd64-v20260826`(source image ID `5563818848645508791`, X86_64)로
고정한다.

## 보안 경계

- GitHub Actions runner는 사람 OS Login 계정과 분리된 로컬 `goledeploy`다.
- `goledeploy`는 `docker`, `lxd`, `google-sudoers` 등 supplemental group이 없다.
- `/etc/gole/infra.env`, `/etc/gole/gole.env`, `/etc/gole/discord.env`는 `root:root 0600`이다. runner도 읽지 못한다.
- `sudo -n true`, raw `install`/`systemctl`, Docker socket 접근은 실패해야 한다.
- privileged 작업은 root-owned `/usr/local/sbin/gole-hostctl`의 검증된 subcommand만 쓴다.
- Discord webhook은 GitHub Actions가 고정 형식 stdin으로 `discord-overlay-install`에만 전달한다.
  root helper가 URI를 검증해 `/etc/gole/discord.env`에 원자 설치하고 Compose는 runner 환경이
  아니라 이 overlay만 읽는다. operations/account/payment는 필수이며 deploy/support가 비어 있으면
  같은 GoLe Discord room의 operations 경로를 사용한다.
- startup metadata, Terraform state, user-data에 runner token, Secret version/payload를 넣지 않는다.
- startup은 최초 한 번 reviewed full SHA를 exact fetch한다. 완료 후 재부팅은 runner가 쓸 수
  있는 `/app`의 스크립트를 root로 실행하지 않는다.
- 인증서 timer도 `/app`을 실행하지 않고 `gole-hostctl certificate-renew`를 호출한다.

운영 env validator는 `GOLE_ENVIRONMENT=production`, 모든 seed/demo/legacy false, secure
cookie, verification-code logging false, 결제·정산 disabled, Gmail SMTP AUTH/STARTTLS/
hostname 검증, CORS, OAuth callback, 약관·개인정보·제3자 제공 버전, support-agent target,
공개 인증 rate limit을 exact 값으로 강제한다. SMTP 앱 비밀번호는 공백 없는 16자리 ASCII
영숫자만 허용한다.

MongoDB, Redis, MinIO는 host port를 publish하지 않고 internal `data` network에만 붙는다.
Nginx/frontend/budget-relay는 `edge`, backend만 `edge`와 `data`를 연결한다. 데이터 서비스
인증은 기존 volume의 무중단 2단계 migration이 필요한 후속 작업이다. 지금 임의로 인증을
켜면 기존 앱 접근을 끊을 수 있으므로 별도 검증 없이 변경하지 않는다.

## Terraform 원격 state 선행 구성

Terraform state는 local 파일이나 git에 보관하지 않고 partial GCS backend를 사용한다. bucket은
backend 자신의 state로 만들 수 없으므로 아래 별도 bootstrap을 먼저 실행한다. bucket 이름과
prefix는 계정에 종속되지 않게 실행 시 주입하고, 자격증명은 `-backend-config`에 넣지 않고 gcloud
ADC를 사용한다. GCS backend는 state locking을 지원한다.

```bash
PROJECT_ID=project-72a52bf1-06aa-4519-b2c
STATE_BUCKET=REPLACE_WITH_GLOBALLY_UNIQUE_STATE_BUCKET
STATE_PREFIX=gole/production
TERRAFORM_PRINCIPAL=user:coldingcontact@gmail.com

# 기본은 read-only dry-run이다. 이름·project·location을 확인한 후에만 명시적으로 생성/수렴한다.
bash infra/gcp/scripts/bootstrap-terraform-state.sh \
  --project "$PROJECT_ID" --bucket "$STATE_BUCKET" \
  --terraform-principal "$TERRAFORM_PRINCIPAL"
bash infra/gcp/scripts/bootstrap-terraform-state.sh \
  --project "$PROJECT_ID" --bucket "$STATE_BUCKET" \
  --terraform-principal "$TERRAFORM_PRINCIPAL" --apply
```

bootstrap은 bucket이 다른 project/location이면 변경 전에 거부한다. Standard regional storage,
PAP enforced, UBLA, versioning, 7일 soft delete와 noncurrent version 최소 10개·14일 lifecycle,
bucket-scoped `roles/storage.objectAdmin`을 검증한다. state는 작아도 active/noncurrent/soft-deleted
bytes와 Class A/B operation 비용이 실제 청구되며 0원으로 간주하지 않는다. 실제 Billing gross
guard가 이를 포함한다. lifecycle 삭제 뒤에도 soft delete로 최대 7일 추가 과금될 수 있다.
[HashiCorp GCS backend](https://developer.hashicorp.com/terraform/language/backend/gcs)와
[Cloud Storage bucket options](https://cloud.google.com/storage/docs/creating-buckets)를 기준으로
가격·정책 변경 시 다시 검토한다.

기존 local state가 있다면 먼저 `0600` backup을 만들고, bucket 보안 검증이 성공한 뒤에만
migrate한다. backup 경로는 git/worktree 밖이어야 한다. state가 없다면 migrate할 대상이 없으므로
`-reconfigure`로 backend만 초기화한 뒤 아래 import를 진행한다.

```bash
cd infra/gcp/terraform
umask 077
STATE_BACKUP="$(mktemp "${TMPDIR:-/tmp}/gole-terraform-state.XXXXXX")"
if test -s terraform.tfstate; then
  cp terraform.tfstate "$STATE_BACKUP"
  terraform init -migrate-state \
    -backend-config="bucket=$STATE_BUCKET" -backend-config="prefix=$STATE_PREFIX"
else
  printf '기존 local state 없음: import 전 remote backend를 새로 초기화함\n' >&2
  terraform init -reconfigure \
    -backend-config="bucket=$STATE_BUCKET" -backend-config="prefix=$STATE_PREFIX"
fi
```

`terraform state pull`이나 plan JSON을 stdout/Discord에 게시하지 않는다. 이 구성은 Secret
container와 resource-level IAM만 state에 넣고 Secret version/payload는 선언·import하지 않는다.
오류 시 `-force-copy`, `state push -force`를 쓰지 말고 작업을 중단해 local backup과 remote
generation/lock을 검토한다.

## 새 프로젝트 생성

```bash
cd infra/gcp/terraform
cp terraform.tfvars.example terraform.tfvars
# project_id와 reviewed main의 40자리 bootstrap_source_sha를 설정
terraform init -reconfigure \
  -backend-config="bucket=$STATE_BUCKET" -backend-config="prefix=$STATE_PREFIX"
# CI 정적 검증만 -backend=false를 쓴다.
terraform fmt -check
terraform validate
terraform plan -out=gole-production.tfplan
terraform show -no-color gole-production.tfplan
# destroy/replace와 뜻밖의 IAM 변경이 0건일 때만 별도 승인 후 실행
terraform apply gole-production.tfplan
```

`bootstrap_source_sha`는 full lowercase SHA만 허용한다. startup은 runner를 먼저 멈춘 뒤
그 commit과 configured origin 및 clean tree를 package/account/policy 변경 전에 재검증한다. `/etc/gole/host-bootstrap.complete`
가 생성된 뒤 재부팅에서는 `/app`을 root로 실행하지 않는다. hostctl/sudoers/systemd를
업데이트할 때는 reviewed SHA의 `bootstrap-host.sh`를 관리자가 IAP에서 명시적으로 한 번
실행한다.

Terraform은 `gole-production-env` container와 resource-level Accessor만 관리한다.
`google_secret_manager_secret_version`이나 `secret_data`는 선언하지 않는다. 신규 환경
payload는 안전한 로컬 `0600` 파일로 `gcloud secrets versions add --data-file=...` 한 뒤
숫자 version만 아래 one-shot 절차의 stdin으로 전달한다.

## 기존 프로젝트 import와 plan

기존 프로젝트는 create 경로로 apply하면 안 된다. 모든 동명 자원을 read-only describe로
project/region/zone/name까지 확인한 뒤 대응 Terraform address에 먼저 import한다.

```bash
PROJECT_ID=project-72a52bf1-06aa-4519-b2c
REGION=asia-northeast3
ZONE=asia-northeast3-a
INSTANCE=gole-production
STATIC_IP_NAME=he-testbed-feedback-ip
STATIC_IP_ADDRESS=35.216.80.123
BILLING_ACCOUNT_ID=01B490-1BC53A-33E611
BUDGET_ID=b645c912-d766-43fc-8923-bff70ecfe8d8
RUNTIME_EMAIL="gole-production-runtime@${PROJECT_ID}.iam.gserviceaccount.com"
set -Eeuo pipefail

# Saved plan의 startup script를 별도 0600 파일로 꺼내 사람이 먼저 읽은 뒤,
# 그 exact SHA-256까지 전체 address/action/field allowlist에 함께 고정한다.
review_and_verify_existing_plan() {
  plan_file="$1"
  plan_json="$(mktemp "${TMPDIR:-/tmp}/gole-plan.XXXXXX")"
  startup_review="$(mktemp "${TMPDIR:-/tmp}/gole-startup-review.XXXXXX")"
  chmod 0600 "$plan_json" "$startup_review"
  terraform show -json "$plan_file" > "$plan_json"
  python3 - "$plan_json" "$startup_review" <<'PY'
import json
import pathlib
import sys

plan = json.loads(pathlib.Path(sys.argv[1]).read_text(encoding="utf-8"))
matches = [
    item for item in plan.get("resource_changes", [])
    if item.get("address") == "google_compute_instance.gole"
]
if len(matches) != 1:
    raise SystemExit("production instance plan entry is missing or duplicated")
after = matches[0].get("change", {}).get("after", {})
script = after.get("metadata", {}).get("startup-script")
if not isinstance(script, str) or not script.startswith("#!/usr/bin/env bash\n"):
    raise SystemExit("reviewable startup script is missing")
pathlib.Path(sys.argv[2]).write_text(script, encoding="utf-8")
PY
  less "$startup_review"
  printf '위 startup script가 reviewed root bootstrap만 포함하는지 확인했으면 Enter: ' >&2
  IFS= read -r _reviewed
  expected_startup_sha256="$(python3 - "$startup_review" <<'PY'
import hashlib
import pathlib
import sys
print(hashlib.sha256(pathlib.Path(sys.argv[1]).read_bytes()).hexdigest())
PY
)"
  python3 ../scripts/verify-terraform-plan.py --existing \
    --expected-static-ip-name "$STATIC_IP_NAME" \
    --expected-static-ip "$STATIC_IP_ADDRESS" \
    --expected-project-id "$PROJECT_ID" \
    --expected-startup-script-sha256 "$expected_startup_sha256" < "$plan_json"
  rm -f -- "$plan_json" "$startup_review"
  unset expected_startup_sha256 _reviewed
}

gcloud compute instances describe "$INSTANCE" --project "$PROJECT_ID" --zone "$ZONE" \
  --format='yaml(name,zone,machineType,deletionProtection,networkInterfaces[0].accessConfigs[0].natIP,disks,resourcePolicies)'
gcloud compute disks describe "$INSTANCE" --project "$PROJECT_ID" --zone "$ZONE" \
  --format='yaml(name,sizeGb,type,sourceImage,resourcePolicies)'
gcloud compute addresses list --project "$PROJECT_ID" \
  --filter="region:($REGION) AND name:($STATIC_IP_NAME) AND address:($STATIC_IP_ADDRESS)" \
  --format='table(name,address,status,region)'

cd infra/gcp/terraform
# 위 원격 state 구성이 완료되고 현재 backend가 해당 bucket/prefix인지 먼저 확인한다.
terraform init -reconfigure \
  -backend-config="bucket=$STATE_BUCKET" -backend-config="prefix=$STATE_PREFIX"

# read-only describe/get-iam-policy로 아래 이름과 binding이 실제 존재함을 먼저 확인한다.
# 이미 state에 있는 address는 건너뛰며 Secret version/payload는 목록에 없다.
import_if_missing() {
  if terraform state show "$1" >/dev/null 2>&1; then
    return
  fi
  terraform import "$1" "$2"
}
while read -r terraform_name service_name; do
  import_if_missing "google_project_service.${terraform_name}" \
    "${PROJECT_ID}/${service_name}.googleapis.com"
done <<'SERVICES'
compute compute
pubsub pubsub
billing_budgets billingbudgets
public_ca publicca
iam iam
secret_manager secretmanager
SERVICES
import_if_missing google_service_account.production_runtime \
  "projects/${PROJECT_ID}/serviceAccounts/${RUNTIME_EMAIL}"
import_if_missing google_secret_manager_secret.production_env \
  "projects/${PROJECT_ID}/secrets/gole-production-env"
import_if_missing google_secret_manager_secret_iam_member.production_env_accessor \
  "projects/${PROJECT_ID}/secrets/gole-production-env roles/secretmanager.secretAccessor serviceAccount:${RUNTIME_EMAIL}"
import_if_missing google_project_iam_custom_role.budget_subscription_consumer \
  "projects/${PROJECT_ID}/roles/goleBudgetSubscriptionConsumer"
import_if_missing google_compute_address.gole \
  "projects/${PROJECT_ID}/regions/${REGION}/addresses/${STATIC_IP_NAME}"
for firewall in gole-web gole-ssh-iap gole-deny-public-admin; do
  terraform_address="google_compute_firewall.${firewall#gole-}"
  test "$firewall" != gole-deny-public-admin || terraform_address=google_compute_firewall.deny_public_admin
  test "$firewall" != gole-ssh-iap || terraform_address=google_compute_firewall.ssh_iap
  import_if_missing "$terraform_address" \
    "projects/${PROJECT_ID}/global/firewalls/${firewall}"
done
import_if_missing google_compute_instance.gole \
  "projects/${PROJECT_ID}/zones/${ZONE}/instances/${INSTANCE}"
import_if_missing google_pubsub_topic.billing_budget \
  "projects/${PROJECT_ID}/topics/gole-billing-budget"
import_if_missing google_pubsub_topic_iam_member.billing_budget_publisher \
  "projects/${PROJECT_ID}/topics/gole-billing-budget roles/pubsub.publisher serviceAccount:billing-budget-alert@system.gserviceaccount.com"
import_if_missing google_pubsub_subscription.billing_budget_discord \
  "projects/${PROJECT_ID}/subscriptions/gole-billing-budget-discord"
import_if_missing google_pubsub_subscription_iam_member.budget_relay_subscriber \
  "projects/${PROJECT_ID}/subscriptions/gole-billing-budget-discord projects/${PROJECT_ID}/roles/goleBudgetSubscriptionConsumer serviceAccount:${RUNTIME_EMAIL}"
import_if_missing 'google_billing_budget.gole_credit_guard[0]' \
  "billingAccounts/${BILLING_ACCOUNT_ID}/budgets/${BUDGET_ID}"
unset -f import_if_missing
terraform plan -out=existing-project.tfplan
review_and_verify_existing_plan existing-project.tfplan
```

위 목록은 2026-09-04 read-only 조회로 실제 존재가 확인된 관리 대상의 exact Terraform
address와 resource ID다. `terraform.tfvars`의 `billing_account_id`도 위 계정과 맞아야 한다.
snapshot policy/attachment는 아직 없으므로 import하지 않고 reviewed plan에서만 create한다.
검증기는 모든 destroy, VM/address create·replace, address 이름/IP 변경과 VM NAT IP 변경을
거부한다. 이미 import한 VM 이외 자원은 `no-op`만 허용하므로 방화벽/IAM/Secret/Pub/Sub의
drift가 하나라도 있으면 한꺼번에 고치지 않고 별도 migration 리뷰로 분리한다. apply 이후
`deletion_protection=true`, OS Login `TRUE`, `e2-standard-2`, 정확한
3일 snapshot policy/boot-disk attachment, instance schedule 미부착까지 요구한다. 즉 VM·boot
disk·IP destroy/replace가 0건이어야 한다. 실제 reserved address의
resource 이름은 `he-testbed-feedback-ip`이고 IP는 `35.216.80.123`이다. 이름을 새로
`gole-production-ip`로 만들거나 기존 IP를 release/recreate하지 않는다. address와 VM
accessConfig의 `networkTier=STANDARD`도 before/after 동일해야 하며 PREMIUM 전환 plan은
검증기가 거부한다. 현재 실제
`deletionProtection=false`의
`false → true`는 in-place, 새 snapshot policy와 disk attachment는 create여야 한다. 이와
다르면 apply하지 않는다.

현재 resource policy 목록의 `he-testbed-office-hours`는 과거 자원이다. 삭제하지 말고
종류와 production 미부착만 확인한다.

```bash
gcloud compute resource-policies describe he-testbed-office-hours \
  --project "$PROJECT_ID" --region "$REGION" \
  --format='yaml(name,instanceSchedulePolicy,snapshotSchedulePolicy)'
gcloud compute instances describe gole-production --project "$PROJECT_ID" --zone "$ZONE" \
  --format='yaml(resourcePolicies)'
gcloud compute disks describe gole-production --project "$PROJECT_ID" --zone "$ZONE" \
  --format='yaml(resourcePolicies)'
```

적용 후 `verify-snapshot-policy.sh`는 production VM에 instance schedule이 없고 boot disk에는
코드의 snapshot schedule 하나만 붙었는지, 삭제 보호, 3일/05:00 KST/`guestFlush=false` 계약을
검사한다. stale policy가 VM이나 disk에 붙으면 실패한다.

현재 live 값은 deletion protection false, OS Login false, snapshot attachment 없음이므로
이 세 계약과 2 vCPU resize를 검증·적용하기 전에는 main 배포를 승인하지 않는다. 다만 resize는
VM stop/start를 수반하므로 runner 분리와 아래 migrate-and-adopt로 root bootstrap completion
marker를 먼저 설치한다. 그 전에는 plan/import까지만 하고 apply/reboot하지 않는다.

## GitHub Actions runner 2단계 등록

Terraform/bootstrap에는 token을 넣지 않는다. 저장소 관리 권한이 있는 로컬 `gh`가 만든
1시간짜리 registration token을 stdin 파이프로만 전송한다.

```bash
gh auth status
gh api --method POST \
  repos/GoLe-by-Colding/GoLe/actions/runners/registration-token --jq .token \
  | gcloud compute ssh gole-production \
      --project "$PROJECT_ID" --zone "$ZONE" --tunnel-through-iap \
      --command='sudo -n /usr/local/sbin/gole-register-github-runner --token-stdin'

gcloud compute ssh gole-production \
  --project "$PROJECT_ID" --zone "$ZONE" --tunnel-through-iap \
  --command='sudo -n /usr/local/sbin/gole-verify-host-bootstrap --require-runner'
```

runner archive `2.337.0`의 SHA-256을 검증하고 `/opt/actions-runner`에 설치한다. 기본 라벨과
`gole-gcp-production`을 사용한다. old human-account runner service가 있으면 경로, service
marker, ExecStart를 검증한 뒤 unit만 제거하고 directory는 복구용으로 남긴다.

CD 최종 read-only 검증 명령은 다음 하나다.

```bash
sudo -n /usr/local/sbin/gole-hostctl deployment-verify-runtime FULL_40_HEX_SHA
```

이는 deployed marker/clean HEAD, production env/Compose policy, backend/frontend/budget/
support-agent/Nginx health, readiness, HTTP·HTTPS www→apex 단일 301, apex HSTS와 watchdog을
민감정보 출력 없이 검사한다.

## 빈 호스트 최초 env 설치

빈 VM만 사용한다. env, version, deployed/pending/transaction marker 중 하나라도 있으면
fail-closed로 거부한다.

```bash
printf '초기 Secret Manager 숫자 version: ' >&2
IFS= read -r GOLE_INITIAL_SECRET_VERSION
printf '%s\n' "$GOLE_INITIAL_SECRET_VERSION" | \
  gcloud compute ssh gole-production --project "$PROJECT_ID" --zone "$ZONE" \
    --tunnel-through-iap --command="sudo -n env -i \
      HOME=/root PATH=/usr/sbin:/usr/bin:/sbin:/bin \
      /usr/local/sbin/gole-bootstrap-production-env \
      --sha '$REVIEWED_MAIN_SHA' --version-stdin --dry-run"

printf '%s\n' "$GOLE_INITIAL_SECRET_VERSION" | \
  gcloud compute ssh gole-production --project "$PROJECT_ID" --zone "$ZONE" \
    --tunnel-through-iap --command="sudo -n env -i \
      HOME=/root PATH=/usr/sbin:/usr/bin:/sbin:/bin \
      /usr/local/sbin/gole-bootstrap-production-env \
      --sha '$REVIEWED_MAIN_SHA' --version-stdin"
unset GOLE_INITIAL_SECRET_VERSION
```

exact version은 `/tmp` 0600 후보로만 내려받고 trap으로 삭제한다. env validator와 Compose
validator 성공 뒤 env/version/`initial-deploy.pending`을 원자 생성한다. 최초 main CD가
모든 smoke를 통과한 뒤에만 deployed SHA를 쓰고 pending marker를 폐기한다. 실패 시 DNS를
전환하지 말고 같은 env로 CD를 재시도한다. marker를 임의 삭제해 우회하지 않는다.

## 현재 운영 VM의 1회 migrate-and-adopt

2026-09-04 read-only 확인 상태는 `/app` clean HEAD
`8913e5718ac2026ba754083a30e2f4408b726941`, backend/frontend/budget healthy, env/version 5,
hostctl/deployed marker 없음이다. version 5는 새 정책 키와 유효 SMTP_PASSWORD가 부족하므로
그대로 채택하지 않는다. 아래는 코드 리뷰 뒤 한 번만 실행한다. `set -x`는 금지한다.

새 host policy와 runner를 설치하기 전에는 구 runner가 새 workflow를 실행하지 않도록, 보호된
main에 PR을 병합하기 **전에** 운영 host gate를 닫는다. false 또는 미설정이면 CD와 Secret Sync
job은 self-hosted runner에 배정되지 않는다.

```bash
gh variable set GOLE_PRODUCTION_HOST_READY --body false \
  --repo GoLe-by-Colding/GoLe
```

먼저 로컬에서 version 5를 source하지 않고 새 후보와 Secret version을 만든다.

```bash
set +x
SECRET_NAME=gole-production-env
secret_work_dir="$(mktemp -d)"
gcloud secrets versions access 5 --secret "$SECRET_NAME" --project "$PROJECT_ID" \
  --out-file "$secret_work_dir/version-5.env" --quiet
printf 'Google 앱 비밀번호(공백 제거 16자): ' >&2
IFS= read -r -s SMTP_APP_PASSWORD
printf '\n' >&2
candidate_path="$(printf '%s' "$SMTP_APP_PASSWORD" | \
  python3 infra/gcp/scripts/prepare-production-env.py \
    "$secret_work_dir/version-5.env" --smtp-password-stdin \
    --output-directory "$secret_work_dir")"
unset SMTP_APP_PASSWORD
python3 infra/gcp/scripts/validate-production-env.py "$candidate_path"
NEW_SECRET_VERSION="$(gcloud secrets versions add "$SECRET_NAME" \
  --project "$PROJECT_ID" --data-file "$candidate_path" \
  --format='value(name.basename())')"
case "$NEW_SECRET_VERSION" in ''|*[!0-9]*) exit 1 ;; esac
test -n "$secret_work_dir" -a -d "$secret_work_dir"
find "$secret_work_dir" -type f -delete
rmdir "$secret_work_dir"
unset candidate_path secret_work_dir
```

위 삭제는 방금 `mktemp -d`로 만든 정확한 directory만 대상으로 한다. payload/SMTP를
stdout, Discord, git에 남기지 않는다. 다음 변수는 로컬 셸에 둔다.

```bash
OLD_SHA=8913e5718ac2026ba754083a30e2f4408b726941
REVIEWED_MAIN_SHA=REPLACE_WITH_REVIEWED_FULL_MAIN_SHA
test "${#REVIEWED_MAIN_SHA}" -eq 40
case "$REVIEWED_MAIN_SHA" in *[!0-9a-f]*) exit 1 ;; esac
ADOPTION_REQUEST_ID="$(uuidgen | tr '[:upper:]' '[:lower:]')"
```

old runner가 유휴인지 확인하고 service를 중지한 뒤 실제 SHA와 readiness를 다시 확인한다.

```bash
gcloud compute ssh gole-production --project "$PROJECT_ID" --zone "$ZONE" \
  --tunnel-through-iap --command="set -Eeuo pipefail
    test \"\$(git -C /app rev-parse HEAD)\" = '$OLD_SHA'
    test -z \"\$(git -C /app status --porcelain=v1 --untracked-files=all)\"
    curl -fsS http://127.0.0.1:8080/actuator/health/readiness >/dev/null
    set -- /etc/systemd/system/actions.runner.*.service
    test \"\$#\" -eq 1 -a -f \"\$1\"
    sudo -n systemctl stop \"\$(basename \"\$1\")\""
```

reviewed commit에서 host policy를 설치하고 checkout을 old SHA로 되돌린다. root bootstrap
source와 이후 신규 배포는 현재 main+green push CI를 요구한다. 반면 이 1회 adoption의
`OLD_SHA`는 새 bootstrap commit이 main에 들어간 뒤에는 더 이상 current main일 수 없으므로,
GitHub compare로 현재 main의 ancestor인지와 그 exact SHA의 과거 main push CI 성공을 함께
검증한 뒤에만 LKG로 기록한다. 임의 branch/PR commit은 거부한다. `/app`은 runner가
쓸 수 있으므로 root executable의 출처로 사용하지 않는다. 아래 launcher는 root-only 임시 bare
repository에 fixed HTTPS origin의 현재 main을 exact SHA로 fetch하고 replace/system/user Git
설정을 모두 끈 archive만 실행한다. 기존 env, version, HTTPS Nginx config,
certificate/data volume은 보존한다.

bootstrap 뒤 adoption은 기존 backend와 budget-relay의 Compose label, health, 동일 operations
route를 먼저 증명하고 현재 4개 Discord route를 값 출력 없이 읽어 root overlay로 보존한다.
기존 컨테이너에 support route가 없으면 operations를 임시 support 경로로 쓴다. 따라서 Mac으로
webhook payload를 복사하지 않고도 adoption 재기동 전에 `/etc/gole/discord.env`가 준비된다.

```bash
cat <<'ROOT_BOOTSTRAP' | gcloud compute ssh gole-production \
  --project "$PROJECT_ID" --zone "$ZONE" --tunnel-through-iap \
  --command="sudo -n env -i HOME=/root PATH=/usr/sbin:/usr/bin:/sbin:/bin \
    bash -seu -- '$REVIEWED_MAIN_SHA' '$PROJECT_ID' '$BUDGET_ID' '$BILLING_ACCOUNT_ID'"
reviewed_sha="$1"; project_id="$2"; budget_id="$3"; billing_account_id="$4"
repo="$(mktemp -d /run/gole-bootstrap-entry-repo.XXXXXX)"
tree="$(mktemp -d /run/gole-bootstrap-entry-tree.XXXXXX)"
trap 'rm -rf -- "$repo" "$tree"' EXIT
chmod 0700 "$repo" "$tree"
trusted_git() { env -i HOME=/root PATH=/usr/bin:/bin GIT_CONFIG_NOSYSTEM=1 \
  GIT_CONFIG_GLOBAL=/dev/null git "$@"; }
trusted_git init --bare "$repo" >/dev/null
trusted_git --git-dir="$repo" remote add origin https://github.com/GoLe-by-Colding/GoLe.git
trusted_git --git-dir="$repo" fetch --no-tags --force origin \
  refs/heads/main:refs/gole/bootstrap >/dev/null 2>&1
test "$(trusted_git --git-dir="$repo" rev-parse refs/gole/bootstrap)" = "$reviewed_sha"
env -i HOME=/root PATH=/usr/bin:/bin PYTHONNOUSERSITE=1 python3 - "$reviewed_sha" <<'PY'
import json, sys, urllib.request
request = urllib.request.Request(
    "https://api.github.com/repos/GoLe-by-Colding/GoLe/actions/workflows/ci.yml/runs"
    "?branch=main&event=push&status=completed&per_page=20",
    headers={"Accept":"application/vnd.github+json", "User-Agent":"GoLe-Root-Bootstrap/1.0"},
)
with urllib.request.urlopen(request, timeout=15) as response:
    runs = json.load(response).get("workflow_runs", [])
if not any(r.get("head_sha") == sys.argv[1] and r.get("conclusion") == "success"
           for r in runs if isinstance(r, dict)):
    raise SystemExit("reviewed SHA has no successful main push CI")
PY
trusted_git --no-replace-objects --git-dir="$repo" archive --format=tar "$reviewed_sha" |
  tar -x --no-same-owner --no-same-permissions -C "$tree"
test -z "$(find "$tree" -xdev -type l -print -quit)"
chown -R root:root "$tree"; chmod -R go-w "$tree"
env -i HOME=/root PATH=/usr/sbin:/usr/bin:/sbin:/bin \
  DOMAIN=gole.co.kr DEPLOY_USER=goledeploy GCP_PROJECT_ID="$project_id" \
  GCP_VM_COST_START=2026-09-01T19:57:05+09:00 \
  GCP_RUNTIME_RATE_TRANSITION_AT=2026-09-06T00:00:00+09:00 \
  GCP_HARD_STOP_AT=2026-10-28T01:50:00+09:00 \
  GCP_CREDIT_DEADLINE=2026-10-28T23:59:59+09:00 \
  GCP_EXPECTED_BUDGET_ID="$budget_id" \
  GCP_EXPECTED_BILLING_ACCOUNT_ID="$billing_account_id" \
  REPOSITORY_URL=https://github.com/GoLe-by-Colding/GoLe.git \
  BOOTSTRAP_SOURCE_SHA="$reviewed_sha" GITHUB_RUNNER_NAME=gole-production \
  GITHUB_RUNNER_LABELS=gole-gcp-production \
  bash "$tree/infra/gcp/scripts/bootstrap-host.sh"
ROOT_BOOTSTRAP

gcloud compute ssh gole-production --project "$PROJECT_ID" --zone "$ZONE" \
  --tunnel-through-iap --command="set -Eeuo pipefail
    sudo -n /usr/local/sbin/gole-verify-host-bootstrap
    sudo -n env SUDO_USER=root /usr/local/sbin/gole-hostctl watchdog-install
    sudo -n -u goledeploy -H git -C /app checkout --detach --force '$OLD_SHA'
    test -z \"\$(sudo -n -u goledeploy -H git -C /app status --porcelain=v1 --untracked-files=all)\""
```

새 env를 backup→install하고 동일 old SHA 서비스를 재기동한다. health/readiness/watchdog
성공 뒤 env version과 deployed SHA를 마지막에 commit한다. 실패/신호/marker 오류면 기존
env를 복원하고 동일 SHA 서비스를 재기동하며, 복구 전 새 rollout을 거부한다.

```bash
printf '%s\n' "$NEW_SECRET_VERSION" | \
  gcloud compute ssh gole-production --project "$PROJECT_ID" --zone "$ZONE" \
    --tunnel-through-iap --command="sudo -n env -i \
      HOME=/root PATH=/usr/sbin:/usr/bin:/sbin:/bin \
      /usr/local/sbin/gole-migrate-and-adopt-existing \
      --sha '$OLD_SHA' --request-id '$ADOPTION_REQUEST_ID' --version-stdin"

gcloud compute ssh gole-production --project "$PROJECT_ID" --zone "$ZONE" \
  --tunnel-through-iap --command="set -Eeuo pipefail
    sudo -n -u goledeploy -H git -C /app fetch --depth=1 origin '$REVIEWED_MAIN_SHA'
    sudo -n -u goledeploy -H git -C /app checkout --detach --force FETCH_HEAD
    sudo -n env SUDO_USER=root /usr/local/sbin/gole-verify-host-bootstrap --require-deployment"
```

adoption 후에는 OS Login을 켜기 전에 운영자 IAM 세 개만 별도 saved plan으로 만들고 exact
allowlist를 통과시켜 먼저 적용한다. 그 직후 `coldingcontact@gmail.com` 활성 계정으로 실제 IAP
SSH round trip까지 성공해야 한다. 이 preflight 전에는 VM/OS Login/resize plan을 apply하지
않는다. 그 다음 remote state에서 전체 plan을 새로 만들고 startup script 수동 검토와 JSON
gate를 다시 통과시킨 saved plan만 apply한다.

```bash
cd infra/gcp/terraform
terraform plan -out=operator-access.tfplan \
  -target=google_project_iam_member.operator_os_admin \
  -target=google_project_iam_member.operator_iap_tunnel \
  -target=google_service_account_iam_member.operator_service_account_user
operator_plan_json="$(mktemp "${TMPDIR:-/tmp}/gole-operator-plan.XXXXXX")"
chmod 0600 "$operator_plan_json"
terraform show -json operator-access.tfplan > "$operator_plan_json"
python3 ../scripts/verify-operator-iam-plan.py \
  --expected-project-id "$PROJECT_ID" < "$operator_plan_json"
rm -f -- "$operator_plan_json"
terraform apply operator-access.tfplan
bash ../scripts/verify-operator-access.sh --project "$PROJECT_ID" \
  --zone "$ZONE" --instance gole-production

terraform plan -out=existing-project.tfplan
review_and_verify_existing_plan existing-project.tfplan
terraform apply existing-project.tfplan
cd ../../..
gcloud compute ssh gole-production --project "$PROJECT_ID" --zone "$ZONE" \
  --tunnel-through-iap --command='sudo -n /usr/local/sbin/gole-verify-host-bootstrap --require-deployment'
GCP_PROJECT_ID="$PROJECT_ID" GCP_ZONE="$ZONE" GCP_INSTANCE_NAME=gole-production \
  GCP_DISK_NAME=gole-production GCP_SNAPSHOT_POLICY_NAME=gole-production-daily-snapshots \
  infra/gcp/scripts/verify-snapshot-policy.sh
```

이후에만 앞 절의 short-lived token으로 새 runner를 등록하고
`--require-runner --require-deployment`를 확인한 뒤 main CD를 시작한다. migration이
중단되면 같은 request/version 명령을 다시 실행해 journal 복구부터 수행한다. 임의로 env,
marker, backup을 지우지 않는다. bootstrap 중간 실패면 old runner를 정지한 채 `/app` 소유자와
host journal부터 검토하고, old SHA 복원 전 Terraform apply/reboot를 하지 않는다.

새 runner 검증까지 끝난 다음 GitHub Actions의 operations/account/payment webhook secrets가
모두 설정됐는지 확인한다. deploy/support secret은 선택이며 비어 있으면 operations로
fallback한다. 역할별 webhook이 같은 GoLe Discord room을 가리켜도 된다. 그 뒤에만 gate를 열고
수동 CD를 한 번 실행한다. 첫 strict CD는 legacy overlay를 GitHub secrets의 현재 값으로 원자
갱신한 뒤 배포한다. Secret Sync도 같은 overlay 설치를 rollout lock 전에 한 번만 수행하고,
parent-held deploy 경로는 overlay를 재설치하지 않고 root 검증만 하므로 lock self-conflict가 없다.

```bash
gh secret list --repo GoLe-by-Colding/GoLe | \
  grep -E '^(DISCORD_OPERATIONS_WEBHOOK_URL|DISCORD_ACCOUNT_WEBHOOK_URL|DISCORD_PAYMENT_WEBHOOK_URL)[[:space:]]'
gh variable set GOLE_PRODUCTION_HOST_READY --body true \
  --repo GoLe-by-Colding/GoLe
gh workflow run cd.yml --ref main --repo GoLe-by-Colding/GoLe
```

PortOne 공개/서버 키와 선택적 GA/GTM ID는 GitHub variables가 아니라
`gole-production-env`의 exact immutable Secret Manager version이 source of truth다. 변경 시
Secret Sync로 그 version을 적용한 뒤 CD로 frontend를 재빌드한다.

CD 성공 후 확인한다.

```bash
gcloud compute ssh gole-production --project "$PROJECT_ID" --zone "$ZONE" \
  --tunnel-through-iap --command="sudo -n env SUDO_USER=root \
    /usr/local/sbin/gole-hostctl deployment-verify-runtime '$REVIEWED_MAIN_SHA'"
curl -fsSI https://gole.co.kr/ | grep -i '^strict-transport-security:'
test "$(curl -sS -o /dev/null -w '%{http_code}|%{redirect_url}' http://www.gole.co.kr/)" = \
  '301|https://gole.co.kr/'
test "$(curl -sS -o /dev/null -w '%{http_code}|%{redirect_url}' https://www.gole.co.kr/)" = \
  '301|https://gole.co.kr/'
```

## Gabia DNS

Gabia DNS 관리에서 필요한 작업은 두 A record뿐이다. 현재 프로젝트를 유지하면 둘 다
`35.216.80.123`, 새 프로젝트로 이전하면 `terraform output -raw public_ip` 값이다.

| type | host | value | TTL |
|---|---|---|---:|
| A | `@` | production static IPv4 | 600 |
| A | `www` | production static IPv4 | 600 |

`DNS 호스트`(자체 nameserver) 화면이 아니라 `DNS 관리 → 레코드 수정`에서 입력한다.
메일용 MX/TXT가 생기면 건드리지 않는다. DNS가 전파되고 새 IP의 smoke가 통과하기 전 old
서버/IP를 해제하지 않는다.

## Nginx와 인증서

bootstrap/reboot는 기존 `/etc/gole/nginx.conf`를 보존한다. main 배포는 현재 certificate
lineage에 맞는 source-controlled template을 준비하고 digest-pinned Nginx의 `nginx -t`
후 root atomic install한다. 전체 smoke/SHA marker가 실패하면 EXIT trap이 config와 old
image를 복원해 Nginx까지 재기동한다. SIGKILL 후 다음 rollout은 journal 복구를 먼저 한다.
HTTP/HTTPS `www.gole.co.kr`은 모두 path/query를 보존해 `https://gole.co.kr`로 단일 301한다.

최초 Google Trust Services 발급만 runtime service account에
`roles/publicca.externalAccountKeyCreator`를 임시 부여한다. VM에서 user `gcloud auth login`
또는 key file을 만들지 않는다. 발급 후 `grant_gts_eab_creator=false`로 회수한다.

```bash
gcloud compute ssh gole-production --project "$PROJECT_ID" --zone "$ZONE" \
  --tunnel-through-iap --command="cd /app && GCP_PROJECT_ID='$PROJECT_ID' \
    DOMAIN=gole.co.kr EMAIL=coldingcontact@gmail.com \
    infra/gcp/scripts/issue-certificate.sh"

printf '' | openssl s_client -connect gole.co.kr:443 -servername gole.co.kr 2>/dev/null \
  | openssl x509 -noout -issuer -dates -ext subjectAltName
```

갱신 timer는 root-owned hostctl을 통해 strict Compose를 다시 검증하고 shared rollout lock을
잡은 뒤 certbot renew, nginx -t/reload를 실행한다. 배포와 동시에 갱신하지 않는다.

## Snapshot, 삭제 보호와 복원 검증

코드는 root-owned timer로 매일 04:30 KST(19:30 UTC)에 논리 복구점을 먼저 만든 뒤,
Seoul regional standard crash-consistent snapshot을 05:00 KST에 `guestFlush=false`로 만들고
3일 보관한다. Linux guest flush는 pre/post script와 guest-agent 구성이 없는 상태에서 실패한
실증이 있어 사용하지 않는다. 논리 백업은 MinIO를 먼저 freeze한 상태에서 Mongo `--oplog`
dump와 MinIO archive를 만들고 unfreeze하며, payload/checksum/directory를 fsync한 뒤에만
`COMPLETE`를 기록한다. 정확히 최근 2개만 로컬에 남겨 snapshot delta를 제한한다. source
disk 삭제 후에도 retention policy를 적용한다. VM은
`deletion_protection=true`, Terraform resource는 `prevent_destroy=true`다. 현재 실제
서버에는 아직 둘 다 적용하지 않았으므로 import/plan 리뷰 전 apply하지 않는다.

```bash
GCP_PROJECT_ID="$PROJECT_ID" GCP_REGION="$REGION" GCP_INSTANCE_ZONE="$ZONE" \
  GCP_INSTANCE_NAME=gole-production \
  infra/gcp/scripts/verify-snapshot-policy.sh
# 첫 일정 실행 이후
GCP_PROJECT_ID="$PROJECT_ID" GCP_REGION="$REGION" GCP_INSTANCE_ZONE="$ZONE" \
  GCP_INSTANCE_NAME=gole-production \
  infra/gcp/scripts/verify-snapshot-policy.sh --require-ready-snapshot
```

분기별 복원 drill은 최신 READY snapshot의 `sourceDisk`와
`sourceSnapshotSchedulePolicy`가 production과 일치하는지 확인하고, 별도 임시 disk/VM에
복원해 read-only mount 후 Docker volume 디렉터리, MongoDB WiredTiger, MinIO object를
검증한다. 원본 production disk에는 attach/mount하지 않는다. 임시 disk/VM 삭제는 복원
검증 기록과 정확한 resource name을 재확인한 후 별도 승인으로 수행한다. 복원본에서는 먼저
`/var/backups/gole-data`의 최신 `COMPLETE` checksum을 `gole-backup-data --verify-latest`로
확인하고 Mongo archive/MinIO archive를 격리된 임시 환경에 복원한다. 두 저장소 복원과
애플리케이션 readiness를 함께 통과하기 전 DNS/외부 트래픽을 열지 않는다.

특히 오래된 disk snapshot만 복원하면 그 이후 완료된 미디어 삭제가 다시 나타날 수 있다.
`media_deletion_journal` 및 `account_deletion_requests` 완료 기록은 같은 boot disk가 아닌
별도 암호화 저장소에
최신본을 보관해야 한다. 복원 절차는 그 최신 journal을 먼저 import하고
`GOLE_MEDIA_REPLAY_COMPLETED_SINCE`를 복구 기준 시각으로 설정해 MinIO의 완료 삭제를
재적용하며, 완료 계정 삭제도 같은 기준 이후 다시 적용한 뒤에만 외부 트래픽을 열어야 한다.
이 외부 journal export/replay가 구현되기
전까지 scheduled snapshot을 미디어 삭제 의미까지 보장하는 완전한 백업으로 간주하지 않는다.
pre-IaC 수동 snapshot도 새 프로젝트 restore와 journal replay를 검증한 뒤 operator가 이름과
생성 시각을 확인해 별도 삭제해야 하며 자동 삭제하지 않는다.

## 비용 가드

2026-09-04 Cloud Billing Catalog의 Seoul KRW on-demand SKU와 현재 실측을 비교해
`e2-standard-2`(2 vCPU·8 GiB)를 desired shape로 사용한다. 실측 RAM은 7.9 GiB 중
2.17 GiB 사용/5.77 GiB available, 컨테이너 합은 약 1.05 GiB, idle CPU 합 약 2.35%,
load 0.18이어서 RAM을 8 GiB로 유지하고 과한 CPU만 절반으로 줄인다.

| 항목 | 기존 e2-custom-4-8192 | desired e2-standard-2 |
|---|---:|---:|
| CPU/RAM | 206.274005316원/h | 118.914661130원/h |
| 100 GiB pd-balanced | 24.975894200원/h | 24.975894200원/h |
| VM+disk+실행 중 외부 IPv4 기본 | 240.749900000원/h | 153.390555330원/h |
| 3개 scheduled + 수동 1개 full snapshot 상한 포함 | 292.804580000원/h | 205.445235330원/h |

- e2-standard-2는 기존보다 세전 `87.359344670원/h` 저렴하다. 단, 9월 1일
  19:57:05~9월 6일 00:00 구간은 실제 4 vCPU 고율 `240.749900000원/h`로 계산하며 새
  요율을 과거에 소급하지 않는다.
- snapshot 100 GiB full-copy 보수 단가는 `13.013670000원/h`다. scheduled 보존 3일은
  첫날부터 3개 full copy(`39.041010000원/h`)로 잡고, pre-IaC 수동 snapshot 1개도
  deadline까지 별도 full copy로 잡는다. incremental/compression이나 3일 ramp 절감은
  안전 여유로만 남긴다.
- 3/5/7일 scheduled 보존 시 desired active all-in 세전 상한은 각각
  `205.445235330`/`231.472575330`/`257.499915330원/h`다. 3일은 매일 19:30 UTC에
  MinIO freeze→Mongo oplog dump→MinIO archive→fsync/COMPLETE를 만들고 정확히 최근 2개를
  disk에 두는 복구 계약으로 보완하며, runtime 테스트가 실패 unfreeze·checksum·rotation을
  검증한다.
- 절대 정지 `2026-10-28 01:50 KST`의 runtime은 `1349.881944h`다. 이때 고율
  `100.048611h`+저율 `1249.833333h`, scheduled/manual snapshot을 처음부터, 네트워크
  30 GiB 상한을 모두 합친 현재 세전 projection은 `295,611.626844원`이다. 정지 뒤
  deadline까지 100 GiB disk+미사용 IPv4, scheduled `min(남은 시간,72h)`, 수동 snapshot을
  더한 세전 tail은 `2,167.424518원`; VAT 10% 포함 최종 all-in은
  `327,556.956498원`이다. `350,000원` hard all-in과 credit `395,600.60원`보다 낮다.

비용식은 `max(현재 기간 Google Billing gross, VAT 포함 로컬 누적 projection)`에 정지 후
retained tail의 VAT 포함액을 더한다. 로컬 누적은 역사 고율 구간+이후 저율 구간+scheduled
3개+수동 1개+재부팅 후에도 단조 증가하는 네트워크 meter다. tail은 현재까지 비용을 다시
더하지 않으므로 이중계상하지 않는다. 반대로 GCS state·예상 밖 SKU처럼 Billing actual이 더
크면 actual이 이기며 더 일찍 정지한다. `350,000원` 상한, `320,000원` Billing stop,
30 GiB, 1350h와 absolute cutoff 중 먼저 닿는 조건을 사용한다.

최근 4 vCPU main 배포의 전체 deploy step은 3분 25초였다. 2 vCPU에서는 fixed smoke 시간을
포함해 보수적으로 5~7분을 예상한다(완전 CPU-bound 구간의 최악 상한은 약 2배). 첫 resize 뒤
실제 clean build 시간을 기록하고 10분을 넘거나 load/RAM pressure가 지속되면 즉시 사양을
자의적으로 올리지 말고 비용 모델과 plan을 다시 리뷰한다. resize 자체는 이 코드에 desired
값만 선언했으며 현재 VM에는 아직 적용하지 않았다.

2026-09-04 11:59:51Z read-only 관측의 현재 기간 Billing 게시값은 `15,835.15원`, 송신량은
약 `0.302 GiB`, guard trip/stop은 0이다. 2026-08-05 이전 기간 `53,919.08원`은 state에
보존돼도 현재 `GCP_HARD_STOP_PERIOD_START=2026-09-01` 계산에서 제외한다. resize 때는
`GCP_HARD_STOP_ARM_ID=2026-09-e2-standard-2-ipv4-v3`처럼 새 arm ID로 재무장하되 cost period와
원래 cost start를 임의로 초기화하지 않는다. current-period Billing은 arm과 무관하게 이어서
사용하고, 예전 arm의 경고/trip/stop event는 새 arm에 섞이지 않는 테스트가 있다.

Discord에서 `Google forecast ... (실제 초과 아님)`, `Google Billing 게시 실제 누적비용`,
`로컬 보수 projection`을 구분한다. Google forecast threshold만 도착한 것은 실제 예산 초과나
자동 정지를 의미하지 않는다. 자동 정지는 실제 Billing, 로컬 all-in, network, runtime,
absolute cutoff 중 먼저 닿는 독립 guard가 결정한다.

현재 Budget은 `370,000원`, minimum credit reserve는 `75,000원`, Billing stop은
`320,000원`, VAT/tail 포함 all-in stop은 `350,000원`이다. 네트워크 30 GiB, absolute
cutoff, runtime limit 중 하나라도 먼저 닿으면 더 일찍 정지한다.

가격 근거는 [Google Cloud VM pricing](https://cloud.google.com/products/compute/pricing/general-purpose)과
[Google Cloud Disk and image pricing](https://cloud.google.com/compute/disks-image-pricing)이며,
2026-09-04 07:00Z Cloud Billing Catalog Seoul KRW의 E2 core `9304-94C4-2117`, E2 RAM
`D715-4E57-BAFB`, custom core `F10F-0364-8D62`, custom RAM `B5E6-7318-DBF9`,
pd-balanced `5666-EFB4-5C79`, 미사용 IPv4 `08D8-05CF-9D56`를 대조했다. disk 계산의
미세 반올림 차이는 더 큰 `24.975894200원/h`, 기존 고율 합계도 올림한
`240.749900000원/h`를 사용한다. KRW 실제 청구는 Cloud Billing SKU가 최종 기준이다.
가격/환율/credit deadline이 바뀌면
Compose exact 값, validator, relay tests를 함께 수정한다.

Budget은 강제 차단이 아니며 게시가 지연될 수 있다. Pub/Sub topic에는
`billing-budget-alert@system.gserviceaccount.com` Publisher를 명시적으로 부여한다. runtime
service account는 production Secret exact-version read와 해당 subscription consume만 받는다.
VM 정지는 metadata token을 읽을 수 있는 root broker가 로컬 `systemctl poweroff`로 수행하며,
runner와 모든 container는 metadata/Docker socket에 접근하지 못한다. root broker는 relay 요청과
무관하게 10초마다 같은 정책을 독립 집행하고, state/계측을 읽거나 fsync하지 못하면
fail-closed로 VM을 정지한다.

VM 자동 정지는 데이터 삭제가 아니다. deadline 이후에도 boot disk, 미사용 고정 IP와 수동
snapshot은 operator가 검토해 이전·삭제할 때까지 계속 청구된다. broker는 14/7/3/1일 전과
deadline 경과를 Discord에 서로 다른 durable reminder로 보내고, deadline 이후 VM이 다시
켜져도 즉시 정지하지만 disk/IP/snapshot을 자동 삭제하지 않는다. 따라서 “절대 무과금” 보장은
credit deadline까지만의 hard cap이며, 종료 전 새 프로젝트 복구 검증→DNS 전환→old resource
정리 checklist를 완료해야 한다.

## 검증

로컬에서 실제 cloud mutation 없이 실행한다.

```bash
bash infra/gcp/tests/bootstrap-contract.test.sh
python3 -m unittest infra/gcp/budget-relay/tests/test_budget_relay.py
docker run --rm -v "$PWD:/mnt:ro" -w /mnt koalaman/shellcheck:v0.10.0 \
  infra/gcp/scripts/*.sh scripts/deploy.sh
docker run --rm -v "$PWD/infra/gcp/terraform:/workspace" -w /workspace \
  hashicorp/terraform:1.14.5 fmt -check
docker run --rm -v "$PWD/infra/gcp/terraform:/workspace" -w /workspace \
  hashicorp/terraform:1.14.5 init -backend=false
docker run --rm -v "$PWD/infra/gcp/terraform:/workspace" -w /workspace \
  hashicorp/terraform:1.14.5 validate
```

운영 변경 전 이 결과와 Terraform plan을 리뷰한다. 이 문서의 절차 자체는 실제 GCP apply,
Secret version 생성, runner 등록 또는 VM mutation을 자동 실행하지 않는다.
