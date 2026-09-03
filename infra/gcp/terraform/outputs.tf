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

output "automatic_cost_guard_roles" {
  value = {
    subscription_consumer = google_project_iam_custom_role.budget_subscription_consumer.name
    instance_stopper      = google_project_iam_custom_role.production_instance_stopper.name
  }
}

output "billing_budget_id" {
  value = try(google_billing_budget.gole_credit_guard[0].id, null)
}
