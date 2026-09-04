output "public_ip" {
  value = google_compute_address.gole.address
}

output "dns_records" {
  value = {
    "@"   = google_compute_address.gole.address
    "www" = google_compute_address.gole.address
  }
}

output "billing_budget_subscription" {
  value = google_pubsub_subscription.billing_budget_discord.name
}

output "production_runtime_service_account" {
  value = google_service_account.production_runtime.email
}

output "production_env_secret" {
  description = "Secret Manager container only; versions and payloads remain outside Terraform"
  value       = google_secret_manager_secret.production_env.id
}

output "automatic_cost_guard_role" {
  description = "The VM can consume only its fixed budget subscription; shutdown is local through the root broker"
  value       = google_project_iam_custom_role.budget_subscription_consumer.name
}

output "billing_budget_id" {
  description = "Terraform-created exact Budget identity injected into the root cost guard"
  value       = try(google_billing_budget.gole_credit_guard[0].id, null)
}

output "boot_disk_snapshot_policy" {
  description = "Daily regional snapshot policy attached to the production boot disk"
  value = {
    name           = google_compute_resource_policy.daily_boot_disk_snapshots.name
    region         = google_compute_resource_policy.daily_boot_disk_snapshots.region
    retention_days = var.snapshot_retention_days
    start_time_utc = var.snapshot_start_time_utc
  }
}

output "github_runner_phase_two" {
  description = "Non-secret inputs for the explicit post-Terraform runner registration phase"
  value = {
    instance = google_compute_instance.gole.name
    zone     = google_compute_instance.gole.zone
    name     = var.github_runner_name
    labels   = concat(["self-hosted", "Linux", "X64"], split(",", var.github_runner_labels))
    script   = "/usr/local/sbin/gole-register-github-runner --token-stdin"
  }
}
