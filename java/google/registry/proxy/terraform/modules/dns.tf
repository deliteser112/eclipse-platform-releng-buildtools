resource "google_dns_managed_zone" "proxy_domain" {
  name = "proxy-domain"
  dns_name = "${var.proxy_domain_name}."
}

resource "google_dns_record_set" "proxy_epp_a_record" {
  name = "epp.${google_dns_managed_zone.proxy_domain.dns_name}"
  type = "A"
  ttl  = 300
  managed_zone = "${google_dns_managed_zone.proxy_domain.name}"
  rrdatas = ["${google_compute_global_address.proxy_ipv4_address.address}"]
}

resource "google_dns_record_set" "proxy_epp_aaaa_record" {
  name = "epp.${google_dns_managed_zone.proxy_domain.dns_name}"
  type = "AAAA"
  ttl  = 300
  managed_zone = "${google_dns_managed_zone.proxy_domain.name}"
  rrdatas = ["${google_compute_global_address.proxy_ipv6_address.address}"]
}

resource "google_dns_record_set" "proxy_whois_a_record" {
  name = "whois.${google_dns_managed_zone.proxy_domain.dns_name}"
  type = "A"
  ttl  = 300
  managed_zone = "${google_dns_managed_zone.proxy_domain.name}"
  rrdatas = ["${google_compute_global_address.proxy_ipv4_address.address}"]
}

resource "google_dns_record_set" "proxy_whois_aaaa_record" {
  name = "whois.${google_dns_managed_zone.proxy_domain.dns_name}"
  type = "AAAA"
  ttl  = 300
  managed_zone = "${google_dns_managed_zone.proxy_domain.name}"
  rrdatas = ["${google_compute_global_address.proxy_ipv6_address.address}"]
}
