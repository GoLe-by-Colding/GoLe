data "google_project" "current" {
  project_id = var.project_id
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

resource "google_service_account" "production_runtime" {
  account_id   = var.runtime_service_account_id
  display_name = "GoLe production runtime"
  description  = "Runtime identity for the GoLe production VM"

  depends_on = [google_project_service.iam]
}

resource "google_project_iam_custom_role" "budget_subscription_consumer" {
  role_id     = "goleBudgetSubscriptionConsumer"
  title       = "GoLe budget subscription consumer"
  description = "Consumes only the GoLe billing budget Pub/Sub subscription"
  permissions = ["pubsub.subscriptions.consume"]
  stage       = "GA"

  depends_on = [google_project_service.iam]
}

resource "google_project_iam_custom_role" "production_instance_stopper" {
  role_id     = "goleProductionInstanceStopper"
  title       = "GoLe production instance stopper"
  description = "Stops only the GoLe production Compute Engine instance"
  permissions = ["compute.instances.stop"]
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

resource "google_compute_address" "gole" {
  name   = "gole-production-ip"
  region = var.region
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
}

resource "google_compute_firewall" "ssh_iap" {
  name    = "gole-ssh-iap"
  network = "default"

  allow {
    protocol = "tcp"
    ports    = ["22"]
  }

  source_ranges = ["35.235.240.0/20"]
  target_tags   = ["gole-ssh-iap"]
}

resource "google_compute_instance" "gole" {
  name                      = "gole-production"
  machine_type              = var.machine_type
  zone                      = var.zone
  allow_stopping_for_update = var.allow_stopping_for_update
  tags                      = ["gole-web", "gole-ssh-iap"]

  labels = {
    app         = "gole"
    environment = "production"
    managed-by  = "terraform"
  }

  boot_disk {
    initialize_params {
      image = "ubuntu-os-cloud/ubuntu-2404-lts-amd64"
      size  = var.disk_size_gb
      type  = "pd-balanced"
    }
  }

  network_interface {
    network = "default"
    access_config {
      nat_ip = google_compute_address.gole.address
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
      apt-get update
      apt-get install -y git
      if [ ! -d /app/.git ]; then
        git clone ${var.repository_url} /app
      fi
      DOMAIN=${var.domain} bash /app/infra/gcp/scripts/bootstrap-host.sh
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
    google_project_iam_member.gts_eab_creator,
    google_pubsub_subscription_iam_member.budget_relay_subscriber,
  ]

  lifecycle {
    prevent_destroy = true
  }
}

resource "google_pubsub_topic" "billing_budget" {
  name       = "gole-billing-budget"
  depends_on = [google_project_service.pubsub]
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

resource "google_compute_instance_iam_member" "production_instance_stopper" {
  project       = var.project_id
  zone          = google_compute_instance.gole.zone
  instance_name = google_compute_instance.gole.name
  role          = google_project_iam_custom_role.production_instance_stopper.name
  member        = "serviceAccount:${google_service_account.production_runtime.email}"
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
  ]
}
