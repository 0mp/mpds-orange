
// VPC
resource "google_compute_network" "vpc" {
  name                    = "${var.project_id}-vpc"
  auto_create_subnetworks = "false"

}

// Subnet
resource "google_compute_subnetwork" "subnet" {
  name          = "${var.project_id}-subnet"
  region        = var.region
  network       = google_compute_network.vpc.name
  //ip_cidr_range = "10.10.0.0/24"
  ip_cidr_range = "10.0.0.0/24"

  secondary_ip_range {
    range_name    = format("%s-pod-range", "${var.project_id}-cluster")
    ip_cidr_range = "10.1.0.0/16"
  }

  secondary_ip_range {
    range_name    = format("%s-svc-range", "${var.project_id}-cluster")
    ip_cidr_range = "10.2.0.0/20"
  }

  depends_on = [
    google_project_service.service,
  ]
}

// Firewall setting for nodePorts
resource "google_compute_firewall" "nodePorts" {
  name    = "nodeports-firewall"
  network = google_compute_network.vpc.name

  allow {
    protocol = "icmp"
  }

  allow {
    protocol = "tcp"
  }

  allow {
    protocol = "udp"
  }
  source_ranges=["0.0.0.0/0"]
}


