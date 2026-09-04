variable "project_id" {
  type        = string
  description = "GCP project ID"
}

variable "billing_account_id" {
  type        = string
  description = "Billing account ID. Leave empty to provision everything except the Billing Budget."
  default     = ""
}

variable "budget_amount_krw" {
  type        = number
  description = "Gross-spend guardrail, intentionally lower than the remaining promotional credit"
  default     = 370000
}

variable "budget_period_start" {
  type = object({
    year  = number
    month = number
    day   = number
  })
  default = { year = 2026, month = 9, day = 1 }
}

variable "budget_period_end" {
  type = object({
    year  = number
    month = number
    day   = number
  })
  default = { year = 2026, month = 10, day = 28 }
}

variable "region" {
  type    = string
  default = "asia-northeast3"
}

variable "zone" {
  type    = string
  default = "asia-northeast3-a"
}

variable "domain" {
  type    = string
  default = "gole.co.kr"
}

variable "static_ip_name" {
  type        = string
  description = "Existing reserved regional address name; changing it would release or replace the production IP"
  default     = "he-testbed-feedback-ip"

  validation {
    condition     = var.static_ip_name == "he-testbed-feedback-ip"
    error_message = "static_ip_name must remain he-testbed-feedback-ip while 35.216.80.123 is the production address."
  }
}

variable "machine_type" {
  type        = string
  description = "Reviewed 2 vCPU/8 GiB production shape; RAM is preserved while idle CPU headroom is reduced"
  default     = "e2-standard-2"

  validation {
    condition     = var.machine_type == "e2-standard-2"
    error_message = "machine_type must remain the reviewed e2-standard-2 production shape."
  }
}

variable "boot_image" {
  type        = string
  description = "Immutable Ubuntu 24.04 image name; image-family aliases are intentionally forbidden"
  default     = "projects/ubuntu-os-cloud/global/images/ubuntu-2404-noble-amd64-v20260826"

  validation {
    condition = can(regex(
      "^projects/ubuntu-os-cloud/global/images/ubuntu-2404-noble-amd64-v[0-9]{8}$",
      var.boot_image
    ))
    error_message = "boot_image must be an exact Ubuntu 24.04 Noble amd64 image name, not a family alias."
  }
}

variable "runtime_service_account_id" {
  type        = string
  description = "Account ID for the dedicated production VM runtime service account"
  default     = "gole-production-runtime"
}

variable "production_env_secret_name" {
  type        = string
  description = "Name of the Secret Manager container; Terraform never manages its versions or payloads"
  default     = "gole-production-env"

  validation {
    condition     = can(regex("^[A-Za-z0-9_-]{1,255}$", var.production_env_secret_name))
    error_message = "production_env_secret_name contains unsupported characters."
  }
}

variable "allow_stopping_for_update" {
  type        = bool
  description = "Allow Terraform to stop the VM when an in-place update requires it, including service account changes"
  default     = true
}

variable "grant_gts_eab_creator" {
  type        = bool
  description = "Temporarily allow the runtime account to create the one-time Google Trust Services EAB during first certificate issuance"
  default     = false
}

variable "disk_size_gb" {
  type        = number
  description = "Reviewed production boot disk size; expansion also multiplies snapshot cost"
  default     = 100

  validation {
    condition     = var.disk_size_gb == 100
    error_message = "disk_size_gb must remain the reviewed 100 GiB production size."
  }
}

variable "operator_email" {
  type        = string
  description = "Human break-glass identity verified before enabling OS Login"
  default     = "coldingcontact@gmail.com"

  validation {
    condition     = var.operator_email == "coldingcontact@gmail.com"
    error_message = "operator_email must remain the reviewed Colding operations identity."
  }
}

variable "vm_cost_start" {
  type        = string
  description = "Reviewed start of this credit/runtime arm, including timezone"
  default     = "2026-09-01T19:57:05+09:00"

  validation {
    condition     = var.vm_cost_start == "2026-09-01T19:57:05+09:00"
    error_message = "vm_cost_start must match the reviewed September 2026 production arm."
  }
}

variable "hard_stop_at" {
  type        = string
  description = "Absolute local shutdown deadline derived from the all-in cost ceiling"
  default     = "2026-10-28T01:50:00+09:00"

  validation {
    condition     = var.hard_stop_at == "2026-10-28T01:50:00+09:00"
    error_message = "hard_stop_at must match the reviewed IPv4-inclusive cost arm."
  }
}

variable "runtime_rate_transition_at" {
  type        = string
  description = "Latest reviewed instant at which the VM must be e2-standard-2"
  default     = "2026-09-06T00:00:00+09:00"

  validation {
    condition     = var.runtime_rate_transition_at == "2026-09-06T00:00:00+09:00"
    error_message = "runtime_rate_transition_at must match the reviewed resize gate."
  }
}

variable "expected_budget_id" {
  type        = string
  description = "Exact Billing Budget UUID accepted by the root broker"
  default     = "b645c912-d766-43fc-8923-bff70ecfe8d8"

  validation {
    condition     = can(regex("^[0-9a-f]{8}(-[0-9a-f]{4}){3}-[0-9a-f]{12}$", var.expected_budget_id))
    error_message = "expected_budget_id must be a lowercase UUID."
  }
}

variable "credit_deadline" {
  type        = string
  description = "Credit expiry endpoint used for retained disk and snapshot tail projection"
  default     = "2026-10-28T23:59:59+09:00"

  validation {
    condition     = var.credit_deadline == "2026-10-28T23:59:59+09:00"
    error_message = "credit_deadline must match the reviewed credit expiry."
  }
}

variable "snapshot_policy_name" {
  type        = string
  description = "Name of the regional scheduled-snapshot policy attached to the production boot disk"
  default     = "gole-production-daily-snapshots"

  validation {
    condition     = can(regex("^[a-z]([-a-z0-9]{0,61}[a-z0-9])?$", var.snapshot_policy_name))
    error_message = "snapshot_policy_name must be a valid Compute Engine resource-policy name."
  }
}

variable "snapshot_retention_days" {
  type        = number
  description = "Number of days that automatic daily snapshots remain recoverable"
  default     = 3

  validation {
    condition     = var.snapshot_retention_days == 3
    error_message = "snapshot_retention_days is fixed at the reviewed three-day production policy."
  }
}

variable "snapshot_start_time_utc" {
  type        = string
  description = "UTC start window for the daily snapshot (20:00 UTC is 05:00 KST)"
  default     = "20:00"

  validation {
    condition     = var.snapshot_start_time_utc == "20:00"
    error_message = "snapshot_start_time_utc is fixed at the reviewed 20:00 UTC window."
  }
}

variable "repository_url" {
  type    = string
  default = "https://github.com/GoLe-by-Colding/GoLe.git"

  validation {
    condition     = var.repository_url == "https://github.com/GoLe-by-Colding/GoLe.git"
    error_message = "repository_url must remain the fixed GoLe production repository."
  }
}

variable "bootstrap_source_sha" {
  type        = string
  description = "Reviewed immutable Git commit used only for the first root-owned host bootstrap"

  validation {
    condition     = can(regex("^[0-9a-f]{40}$", var.bootstrap_source_sha))
    error_message = "bootstrap_source_sha must be a full lowercase 40-character Git commit SHA."
  }
}

variable "deploy_user" {
  type        = string
  description = "Fixed local service account used only by the repository-scoped GitHub Actions runner"
  default     = "goledeploy"

  validation {
    condition     = var.deploy_user == "goledeploy"
    error_message = "deploy_user must remain the dedicated goledeploy local service account."
  }
}

variable "github_runner_name" {
  type        = string
  description = "Non-secret name shown for the repository-scoped production runner"
  default     = "gole-production"

  validation {
    condition     = can(regex("^[A-Za-z0-9._-]{1,64}$", var.github_runner_name))
    error_message = "github_runner_name contains unsupported characters."
  }
}

variable "github_runner_labels" {
  type        = string
  description = "Comma-separated custom labels; default self-hosted, Linux and X64 labels are added by GitHub"
  default     = "gole-gcp-production"

  validation {
    condition = (
      can(regex("^[A-Za-z0-9._-]+(,[A-Za-z0-9._-]+)*$", var.github_runner_labels)) &&
      contains(split(",", var.github_runner_labels), "gole-gcp-production")
    )
    error_message = "github_runner_labels must be a safe comma-separated list containing gole-gcp-production."
  }
}
