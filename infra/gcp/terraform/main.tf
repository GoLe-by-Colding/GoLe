data "google_project" "current" {
  project_id = var.project_id
}

resource "google_project_service" "compute" {
  service            = "compute.googleapis.com"
  disable_on_destroy = false
}

resource "google_project_service" "pubsub" {
  service            = "pubsub.googleapis.com"
  disable_on_destroy = false
}

resource "google_project_service" "billing_budgets" {
  service            = "billingbudgets.googleapis.com"
  disable_on_destroy = false
}

resource "google_project_service" "public_ca" {
  service            = "publicca.googleapis.com"
  disable_on_destroy = false
}

resource "google_project_service" "iam" {
  service            = "iam.googleapis.com"
  disable_on_destroy = false
}

resource "google_project_service" "secret_manager" {
  service            = "secretmanager.googleapis.com"
  disable_on_destroy = false
}

resource "google_service_account" "production_runtime" {
  account_id   = var.runtime_service_account_id
  display_name = "GoLe production runtime"
  description  = "Runtime identity for the GoLe production VM"

  depends_on = [google_project_service.iam]
}

# Terraform owns only the empty secret container and its least-privilege IAM.
# Secret versions and payloads are created out of band and never enter state.
resource "google_secret_manager_secret" "production_env" {
  secret_id = var.production_env_secret_name

  replication {
    auto {}
  }

  depends_on = [google_project_service.secret_manager]

  lifecycle {
    prevent_destroy = true
  }
}

resource "google_secret_manager_secret_iam_member" "production_env_accessor" {
  secret_id = google_secret_manager_secret.production_env.id
  role      = "roles/secretmanager.secretAccessor"
  member    = "serviceAccount:${google_service_account.production_runtime.email}"
}

resource "google_project_iam_custom_role" "budget_subscription_consumer" {
  role_id     = "goleBudgetSubscriptionConsumer"
  title       = "GoLe budget subscription consumer"
  description = "Consumes only the GoLe billing budget Pub/Sub subscription"
  permissions = ["pubsub.subscriptions.consume"]
  stage       = "GA"

  depends_on = [google_project_service.iam]
}

resource "google_project_iam_member" "gts_eab_creator" {
  count   = var.grant_gts_eab_creator ? 1 : 0
  project = var.project_id
  role    = "roles/publicca.externalAccountKeyCreator"
  member  = "serviceAccount:${google_service_account.production_runtime.email}"

  depends_on = [google_project_service.public_ca]
}

# OS Login is enabled on the production VM in the same reviewed apply that
# changes the machine shape. Keep the human recovery identity explicit so that
# enabling OS Login cannot silently remove the only IAP administration path.
resource "google_project_iam_member" "operator_os_admin" {
  project = var.project_id
  role    = "roles/compute.osAdminLogin"
  member  = "user:${var.operator_email}"
}

resource "google_project_iam_member" "operator_iap_tunnel" {
  project = var.project_id
  role    = "roles/iap.tunnelResourceAccessor"
  member  = "user:${var.operator_email}"
}

resource "google_service_account_iam_member" "operator_service_account_user" {
  service_account_id = google_service_account.production_runtime.name
  role               = "roles/iam.serviceAccountUser"
  member             = "user:${var.operator_email}"
}

resource "google_compute_address" "gole" {
  name         = var.static_ip_name
  region       = var.region
  network_tier = "STANDARD"

  depends_on = [google_project_service.compute]
}

resource "google_compute_firewall" "web" {
  name    = "gole-web"
  network = "default"

  allow {
    protocol = "tcp"
    ports    = ["80", "443"]
  }

  source_ranges = ["0.0.0.0/0"]
  target_tags   = ["gole-web"]

  depends_on = [google_project_service.compute]
}

resource "google_compute_firewall" "ssh_iap" {
  name     = "gole-ssh-iap"
  network  = "default"
  priority = 800

  allow {
    protocol = "tcp"
    ports    = ["22"]
  }

  source_ranges = ["35.235.240.0/20"]
  target_tags   = ["gole-ssh-iap"]

  depends_on = [google_project_service.compute]
}

# 새 프로젝트의 default VPC에는 0.0.0.0/0 SSH/RDP 허용 규칙이 자동으로
# 남을 수 있다. IAP 허용 규칙을 더 높은 우선순위로 둔 뒤 GoLe VM의 관리
# 포트는 명시적으로 차단해 계정 이전 때도 기본 규칙에 노출되지 않게 한다.
resource "google_compute_firewall" "deny_public_admin" {
  name      = "gole-deny-public-admin"
  network   = "default"
  direction = "INGRESS"
  priority  = 900

  deny {
    protocol = "tcp"
    ports    = ["22", "3389"]
  }

  source_ranges = ["0.0.0.0/0"]
  target_tags   = ["gole-ssh-iap"]

  depends_on = [google_project_service.compute]
}

resource "google_compute_instance" "gole" {
  name                      = "gole-production"
  machine_type              = var.machine_type
  zone                      = var.zone
  allow_stopping_for_update = var.allow_stopping_for_update
  deletion_protection       = true
  tags                      = ["gole-web", "gole-ssh-iap"]

  labels = {
    app         = "gole"
    environment = "production"
    managed-by  = "terraform"
  }

  boot_disk {
    auto_delete = false
    initialize_params {
      image = var.boot_image
      size  = var.disk_size_gb
      type  = "pd-balanced"
    }
  }

  network_interface {
    network = "default"
    access_config {
      nat_ip       = google_compute_address.gole.address
      network_tier = "STANDARD"
    }
  }

  service_account {
    email  = google_service_account.production_runtime.email
    scopes = ["cloud-platform"]
  }

  metadata = {
    enable-oslogin = "TRUE"
    startup-script = <<-EOT
      #!/usr/bin/env bash
      set -euo pipefail

      # Install the root policy once from an immutable reviewed commit. Later
      # boots must never execute the runner-writable /app checkout as root.
      if [ -e /etc/gole/host-bootstrap.complete ]; then
        if [ -f /etc/gole/host-bootstrap.complete ] && \
          [ ! -L /etc/gole/host-bootstrap.complete ] && \
          [ "$(stat -c '%U:%G:%a' /etc/gole/host-bootstrap.complete)" = "root:root:644" ] && \
          grep -Eq '^bootstrap_source_sha=[0-9a-f]{40}$' /etc/gole/host-bootstrap.complete && \
          [ -x /usr/local/sbin/gole-hostctl ]; then
          exit 0
        fi
        echo "host bootstrap completion marker is invalid" >&2
        exit 1
      fi

      apt-get update
      apt-get install -y ca-certificates git python3
      bootstrap_repository="$(mktemp -d /run/gole-startup-repository.XXXXXX)"
      bootstrap_tree="$(mktemp -d /run/gole-startup-tree.XXXXXX)"
      trap 'rm -rf -- "$bootstrap_repository" "$bootstrap_tree"' EXIT
      chmod 0700 "$bootstrap_repository" "$bootstrap_tree"
      env -i HOME=/root PATH=/usr/bin:/bin GIT_CONFIG_NOSYSTEM=1 GIT_CONFIG_GLOBAL=/dev/null \
        git init --bare "$bootstrap_repository" >/dev/null
      env -i HOME=/root PATH=/usr/bin:/bin GIT_CONFIG_NOSYSTEM=1 GIT_CONFIG_GLOBAL=/dev/null \
        git --git-dir="$bootstrap_repository" remote add origin ${jsonencode(var.repository_url)}
      env -i HOME=/root PATH=/usr/bin:/bin GIT_CONFIG_NOSYSTEM=1 GIT_CONFIG_GLOBAL=/dev/null \
        git --git-dir="$bootstrap_repository" fetch --no-tags --force origin \
          refs/heads/main:refs/gole/bootstrap >/dev/null 2>&1
      [ "$(env -i HOME=/root PATH=/usr/bin:/bin GIT_CONFIG_NOSYSTEM=1 GIT_CONFIG_GLOBAL=/dev/null \
        git --git-dir="$bootstrap_repository" rev-parse refs/gole/bootstrap)" = \
        ${jsonencode(var.bootstrap_source_sha)} ] || {
        echo "reviewed bootstrap SHA is not current origin/main" >&2
        exit 1
      }
      env -i HOME=/root PATH=/usr/bin:/bin PYTHONNOUSERSITE=1 \
        python3 - ${jsonencode(var.bootstrap_source_sha)} <<'PY'
      import json
      import sys
      import urllib.request

      sha = sys.argv[1]
      request = urllib.request.Request(
          "https://api.github.com/repos/GoLe-by-Colding/GoLe/actions/workflows/ci.yml/runs"
          "?branch=main&event=push&status=completed&per_page=20",
          headers={
              "Accept": "application/vnd.github+json",
              "User-Agent": "GoLe-Startup-Bootstrap/1.0",
              "X-GitHub-Api-Version": "2022-11-28",
          },
      )
      with urllib.request.urlopen(request, timeout=15) as response:
          runs = json.load(response).get("workflow_runs", [])
      if not any(
          isinstance(run, dict)
          and run.get("head_sha") == sha
          and run.get("conclusion") == "success"
          for run in runs
      ):
          raise SystemExit("bootstrap SHA has no successful main push CI")
      PY
      env -i HOME=/root PATH=/usr/bin:/bin GIT_CONFIG_NOSYSTEM=1 GIT_CONFIG_GLOBAL=/dev/null \
        git --no-replace-objects --git-dir="$bootstrap_repository" archive \
          --format=tar ${jsonencode(var.bootstrap_source_sha)} | \
        tar -x --no-same-owner --no-same-permissions -C "$bootstrap_tree"
      find "$bootstrap_tree" -xdev -type l -print -quit | grep -q . && {
        echo "bootstrap source contains a symbolic link" >&2
        exit 1
      }
      chown -R root:root "$bootstrap_tree"
      chmod -R go-w "$bootstrap_tree"
      DOMAIN=${jsonencode(var.domain)} \
      DEPLOY_USER=${jsonencode(var.deploy_user)} \
      GCP_PROJECT_ID=${jsonencode(var.project_id)} \
      GCP_VM_COST_START=${jsonencode(var.vm_cost_start)} \
      GCP_HARD_STOP_AT=${jsonencode(var.hard_stop_at)} \
      GCP_CREDIT_DEADLINE=${jsonencode(var.credit_deadline)} \
      GCP_RUNTIME_RATE_TRANSITION_AT=${jsonencode(var.runtime_rate_transition_at)} \
      GCP_EXPECTED_BUDGET_ID=${jsonencode(var.expected_budget_id)} \
      GCP_EXPECTED_BILLING_ACCOUNT_ID=${jsonencode(var.billing_account_id)} \
      REPOSITORY_URL=${jsonencode(var.repository_url)} \
      BOOTSTRAP_SOURCE_SHA=${jsonencode(var.bootstrap_source_sha)} \
      GITHUB_RUNNER_NAME=${jsonencode(var.github_runner_name)} \
      GITHUB_RUNNER_LABELS=${jsonencode(var.github_runner_labels)} \
        bash "$bootstrap_tree/infra/gcp/scripts/bootstrap-host.sh"
    EOT
  }

  shielded_instance_config {
    enable_secure_boot          = true
    enable_vtpm                 = true
    enable_integrity_monitoring = true
  }

  scheduling {
    automatic_restart   = true
    on_host_maintenance = "MIGRATE"
  }

  depends_on = [
    google_project_service.compute,
    google_project_iam_member.gts_eab_creator,
    google_project_iam_member.operator_os_admin,
    google_project_iam_member.operator_iap_tunnel,
    google_service_account_iam_member.operator_service_account_user,
    google_pubsub_subscription_iam_member.budget_relay_subscriber,
    google_secret_manager_secret_iam_member.production_env_accessor,
  ]

  lifecycle {
    prevent_destroy = true
  }
}

# A regional standard snapshot chain is incremental. Three daily recovery
# points protect the single-disk deployment without introducing another always-
# on VM. The attachment is kept separate from the instance resource so an
# existing VM can be imported first and the policy added in a reviewed apply.
resource "google_compute_resource_policy" "daily_boot_disk_snapshots" {
  name        = var.snapshot_policy_name
  region      = var.region
  description = "Daily three-day recovery points for the GoLe production boot disk"

  snapshot_schedule_policy {
    schedule {
      daily_schedule {
        days_in_cycle = 1
        start_time    = var.snapshot_start_time_utc
      }
    }

    retention_policy {
      max_retention_days    = var.snapshot_retention_days
      on_source_disk_delete = "APPLY_RETENTION_POLICY"
    }

    snapshot_properties {
      # The stock Ubuntu guest does not have the required application-specific
      # pre/post snapshot hooks. A root-owned logical backup timer creates and
      # checksums MongoDB and MinIO recovery artifacts before this crash-
      # consistent disk snapshot instead of claiming unsupported guest flush.
      guest_flush       = false
      storage_locations = [var.region]
      labels = {
        app         = "gole"
        environment = "production"
        backup      = "daily"
        managed-by  = "terraform"
      }
    }
  }

  lifecycle {
    prevent_destroy = true
  }

  depends_on = [google_project_service.compute]
}

resource "google_compute_disk_resource_policy_attachment" "daily_boot_disk_snapshots" {
  name = google_compute_resource_policy.daily_boot_disk_snapshots.name
  disk = google_compute_instance.gole.name
  zone = google_compute_instance.gole.zone

  depends_on = [google_compute_instance.gole]
}

resource "google_pubsub_topic" "billing_budget" {
  name       = "gole-billing-budget"
  depends_on = [google_project_service.pubsub]
}

# Cloud Billing uses this Google-managed identity to publish budget updates.
# Keep the grant explicit: losing it makes programmatic notifications fail
# silently, so the Discord relay and automatic stop input would both go stale.
resource "google_pubsub_topic_iam_member" "billing_budget_publisher" {
  topic  = google_pubsub_topic.billing_budget.name
  role   = "roles/pubsub.publisher"
  member = "serviceAccount:billing-budget-alert@system.gserviceaccount.com"
}

resource "google_pubsub_subscription" "billing_budget_discord" {
  name                       = "gole-billing-budget-discord"
  topic                      = google_pubsub_topic.billing_budget.id
  ack_deadline_seconds       = 60
  message_retention_duration = "604800s"
}

resource "google_pubsub_subscription_iam_member" "budget_relay_subscriber" {
  subscription = google_pubsub_subscription.billing_budget_discord.name
  role         = google_project_iam_custom_role.budget_subscription_consumer.name
  member       = "serviceAccount:${google_service_account.production_runtime.email}"
}

resource "google_billing_budget" "gole_credit_guard" {
  count           = var.billing_account_id == "" ? 0 : 1
  billing_account = var.billing_account_id
  display_name    = "GoLe production credit guard"

  budget_filter {
    projects               = ["projects/${data.google_project.current.number}"]
    credit_types_treatment = "EXCLUDE_ALL_CREDITS"

    custom_period {
      start_date {
        year  = var.budget_period_start.year
        month = var.budget_period_start.month
        day   = var.budget_period_start.day
      }
      end_date {
        year  = var.budget_period_end.year
        month = var.budget_period_end.month
        day   = var.budget_period_end.day
      }
    }
  }

  amount {
    specified_amount {
      currency_code = "KRW"
      units         = tostring(var.budget_amount_krw)
    }
  }

  threshold_rules { threshold_percent = 0.50 }
  threshold_rules { threshold_percent = 0.75 }
  threshold_rules { threshold_percent = 0.85 }
  threshold_rules { threshold_percent = 0.90 }
  threshold_rules { threshold_percent = 0.95 }
  threshold_rules { threshold_percent = 1.00 }
  all_updates_rule {
    pubsub_topic                    = google_pubsub_topic.billing_budget.id
    schema_version                  = "1.0"
    enable_project_level_recipients = true
  }

  depends_on = [
    google_project_service.billing_budgets,
    google_pubsub_topic_iam_member.billing_budget_publisher,
  ]
}
