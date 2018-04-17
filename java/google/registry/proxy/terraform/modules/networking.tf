module "proxy_networking" {
  source                = "./networking"
  proxy_instance_groups = "${local.proxy_instance_groups}"
  proxy_ports           = "${var.proxy_ports}"
}

module "proxy_networking_canary" {
  source                = "./networking"
  proxy_instance_groups = "${local.proxy_instance_groups}"
  suffix                = "-canary"
  proxy_ports           = "${var.proxy_ports_canary}"
}
