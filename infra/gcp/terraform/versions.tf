terraform {
  required_version = ">= 1.6.0"

  # The bucket and prefix are deliberately supplied at init time. The bucket is
  # bootstrapped outside this state so a new project has no backend cycle.
  backend "gcs" {}

  required_providers {
    google = {
      source  = "hashicorp/google"
      version = "~> 7.0"
    }
  }
}

provider "google" {
  project               = var.project_id
  region                = var.region
  zone                  = var.zone
  billing_project       = var.project_id
  user_project_override = true
}
