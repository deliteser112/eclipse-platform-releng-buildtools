# GCP project in which the proxy runs.
variable "proxy_project_name" {}

# GCP project in which Nomulus runs.
variable "nomulus_project_name" {}

# GCP project from which the proxy image is pulled.
variable "gcr_project_name" {}

# The base domain name of the proxy, without the whois. or epp. part.
variable "proxy_domain_name" {}

# Cloud KMS keyring name
variable "proxy_key_ring" {
  default = "proxy-key-ring"
}

# Cloud KMS key name
variable "proxy_key" {
  default = "proxy-key"
}

# Node ports exposed by the proxy.
variable "proxy_ports" {
  type = "map"

  default = {
    health_check = 30000
    whois        = 30001
    epp          = 30002
  }
}
