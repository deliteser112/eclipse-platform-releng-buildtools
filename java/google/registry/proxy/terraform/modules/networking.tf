resource "google_compute_global_address" "proxy_ipv4_address" {
  name = "proxy-ipv4-address"
  ip_version = "IPV4"
}

resource "google_compute_global_address" "proxy_ipv6_address" {
  name = "proxy-ipv6-address"
  ip_version = "IPV6"
}

resource "google_compute_firewall" "proxy_firewall" {
  name = "proxy-firewall"
  network = "default"

  allow {
    protocol = "tcp"
    ports = [
      "${var.proxy_ports["epp"]}",
      "${var.proxy_ports["whois"]}",
      "${var.proxy_ports["health_check"]}"]
  }

  source_ranges = [
    "130.211.0.0/22",
    "35.191.0.0/16"]

  target_tags = [
    "proxy-cluster"
  ]
}

resource "google_compute_health_check" "proxy_health_check" {
  name = "proxy-health-check"

  tcp_health_check {
    port = "${var.proxy_ports["health_check"]}"
    request = "HEALTH_CHECK_REQUEST"
    response = "HEALTH_CHECK_RESPONSE"
  }
}

resource "google_compute_backend_service" "epp_backend_service" {
  name = "epp-backend-service"
  protocol = "TCP"
  timeout_sec = 3600
  port_name = "epp"
  backend {
    group = "${local.proxy_instance_groups["americas"]}"
  }
  backend {
    group = "${local.proxy_instance_groups["emea"]}"
  }
  backend {
    group = "${local.proxy_instance_groups["apac"]}"
  }
  health_checks = [
    "${google_compute_health_check.proxy_health_check.self_link}"]
}

resource "google_compute_backend_service" "whois_backend_service" {
  name = "whois-backend-service"
  protocol = "TCP"
  timeout_sec = 60
  port_name = "whois"
  backend {
    group = "${local.proxy_instance_groups["americas"]}"
  }
  backend {
    group = "${local.proxy_instance_groups["emea"]}"
  }
  backend {
    group = "${local.proxy_instance_groups["apac"]}"
  }
  health_checks = [
    "${google_compute_health_check.proxy_health_check.self_link}"]
}

resource "google_compute_target_tcp_proxy" "epp_tcp_proxy" {
  name = "epp-tcp-proxy"
  proxy_header = "PROXY_V1"
  backend_service = "${google_compute_backend_service.epp_backend_service.self_link}"
}

resource "google_compute_target_tcp_proxy" "whois_tcp_proxy" {
  name = "whois-tcp-proxy"
  proxy_header = "PROXY_V1"
  backend_service = "${google_compute_backend_service.whois_backend_service.self_link}"
}

resource "google_compute_global_forwarding_rule" "epp_ipv4_forwarding_rule" {
  name = "epp-ipv4-forwarding-rule"
  ip_address = "${google_compute_global_address.proxy_ipv4_address.address}"
  target = "${google_compute_target_tcp_proxy.epp_tcp_proxy.self_link}"
  port_range = "700"
}

resource "google_compute_global_forwarding_rule" "epp_ipv6_forwarding_rule" {
  name = "epp-ipv6-forwarding-rule"
  ip_address = "${google_compute_global_address.proxy_ipv6_address.address}"
  target = "${google_compute_target_tcp_proxy.epp_tcp_proxy.self_link}"
  port_range = "700"
}

resource "google_compute_global_forwarding_rule" "whois_ipv4_forwarding_rule" {
  name = "whois-ipv4-forwarding-rule"
  ip_address = "${google_compute_global_address.proxy_ipv4_address.address}"
  target = "${google_compute_target_tcp_proxy.whois_tcp_proxy.self_link}"
  port_range = "43"
}

resource "google_compute_global_forwarding_rule" "whois_ipv6_forwarding_rule" {
  name = "whois-ipv6-forwarding-rule"
  ip_address = "${google_compute_global_address.proxy_ipv6_address.address}"
  target = "${google_compute_target_tcp_proxy.whois_tcp_proxy.self_link}"
  port_range = "43"
}
