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
  name         = "gole-production"
  machine_type = var.machine_type
  zone         = var.zone
  tags         = ["gole-web", "gole-ssh-iap"]

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

  lifecycle {
    prevent_destroy = true
  }
}

