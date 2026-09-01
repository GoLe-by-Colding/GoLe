variable "project_id" {
  type        = string
  description = "GCP project ID"
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
  default = "e2-standard-4"
}

variable "disk_size_gb" {
  type    = number
  default = 100
}

variable "repository_url" {
  type    = string
  default = "https://github.com/GoLe-by-Colding/GoLe.git"
}

