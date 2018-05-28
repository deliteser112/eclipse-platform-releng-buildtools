locals {
  proxy_cluster_zone = "${lookup(var.proxy_cluster_zones, var.proxy_cluster_region)}"
}

data "google_container_engine_versions" "gke_version" {
  zone = "${local.proxy_cluster_zone}"
}

resource "google_container_cluster" "proxy_cluster" {
  name               = "proxy-cluster-${var.proxy_cluster_region}"
  zone               = "${local.proxy_cluster_zone}"
  node_version       = "${data.google_container_engine_versions.gke_version.latest_node_version}"
  min_master_version = "${data.google_container_engine_versions.gke_version.latest_master_version}"

  timeouts {
    update = "30m"
  }

  node_pool {
    name               = "proxy-node-pool"
    initial_node_count = 1

    node_config {
      tags = [
        "proxy-cluster",
      ]

      service_account = "${var.proxy_service_account_email}"

      oauth_scopes = [
        "https://www.googleapis.com/auth/cloud-platform",
        "https://www.googleapis.com/auth/userinfo.email",
      ]
    }

    autoscaling {
      max_node_count = 5
      min_node_count = 1
    }

    management {
      auto_repair  = true
      auto_upgrade = true
    }
  }
}
