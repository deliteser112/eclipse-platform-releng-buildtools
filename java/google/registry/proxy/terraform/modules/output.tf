output "proxy_name_servers" {
  value = "${google_dns_managed_zone.proxy_domain.name_servers}"
}

output "proxy_service_account_client_id" {
  value = "${google_service_account.proxy_service_account.unique_id}"
}

output "proxy_ipv4_address" {
  value = "${google_compute_global_address.proxy_ipv4_address.address}"
}

output "proxy_ipv6_address" {
  value = "${google_compute_global_address.proxy_ipv6_address.address}"
}
