output "public_ip" {
  value = google_compute_address.gole.address
}

output "dns_records" {
  value = {
    "@"   = google_compute_address.gole.address
    "www" = google_compute_address.gole.address
  }
}

