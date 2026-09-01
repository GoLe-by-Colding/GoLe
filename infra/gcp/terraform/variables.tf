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

variable "machine_type" {
  type    = string
  default = "e2-custom-4-8192"
}

variable "runtime_service_account_id" {
  type        = string
  description = "Account ID for the dedicated production VM runtime service account"
  default     = "gole-production-runtime"
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
  type    = number
  default = 100
}

variable "repository_url" {
  type    = string
  default = "https://github.com/GoLe-by-Colding/GoLe.git"
}
