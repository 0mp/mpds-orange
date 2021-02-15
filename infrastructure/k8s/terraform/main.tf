provider "google" {
  credentials = file("key.json")
  project     = var.project_id
  region      = var.region
  zone        = var.zones[0]
  version = "~> 3.51"
}

variable "gke_username" {
  default     = ""
  description = "gke username"
}

variable "gke_password" {
  default     = ""
  description = "gke password"
}

variable "gke_num_nodes" {
  default     = 3
  description = "number of gke nodes"
}

variable "gke_auto_scaling_enabled" {
  default     = false
  description = "Enable/disable cluster auto scaling"
}

variable "gke_min_master_version" {
  default     = "1.16.15-gke.7800"
  description = "The minimum version of the master node on GKE"
}

variable "gke_machine_type" {
  default     = "e2-standard-4"
  description = "The minimum version of the master node on GKE"
}

variable "service_account_iam_roles" {
  type = list

  default = [
    "roles/container.admin",
    # "roles/Minimalk8sIamRole",
    "roles/logging.logWriter",
    "roles/monitoring.metricWriter",
    "roles/monitoring.viewer",
    "roles/storage.admin"
  ]
  description = <<-EOF
  List of the default IAM roles to attach to the service account on the
  GKE Nodes.
  EOF
}

variable "project_services" {
  type = list

  default = [
    "iam.googleapis.com",
    "cloudresourcemanager.googleapis.com",
    "container.googleapis.com",
    "compute.googleapis.com",
    "iam.googleapis.com",
    "logging.googleapis.com",
    "monitoring.googleapis.com",
  ]
  description = <<-EOF
  The GCP APIs that should be enabled in this project.
  EOF
}

// GKE cluster
resource "google_container_cluster" "dev-cluster" {
  name     = "${var.project_id}-cluster"
  //location = var.region

  // Zonal Cluster
  location = var.zones[0]

  remove_default_node_pool = true
  initial_node_count       = 1

  min_master_version = var.gke_min_master_version

  enable_binary_authorization = "true"

  network_policy {
    enabled = "false"
  }

  network    = google_compute_network.vpc.self_link
  subnetwork = google_compute_subnetwork.subnet.self_link

  master_auth {
    username = var.gke_username
    password = var.gke_password

    client_certificate_config {
      issue_client_certificate = false
    }
  }

    // Allocate IPs in our subnetwork
  ip_allocation_policy {
    //use_ip_aliases                = true
    cluster_secondary_range_name  = google_compute_subnetwork.subnet.secondary_ip_range.0.range_name
    services_secondary_range_name = google_compute_subnetwork.subnet.secondary_ip_range.1.range_name
  }

  cluster_autoscaling {
      enabled = var.gke_auto_scaling_enabled
  }

  vertical_pod_autoscaling {
    enabled = "true"
  }
  release_channel {
    channel = "STABLE"
  }

  depends_on = [
    google_project_service.service,
    google_project_iam_member.service-account,
    google_compute_network.vpc,
  ]
}

// Separately Managed Node Pool
resource "google_container_node_pool" "primary_nodes" {
  name       = "${google_container_cluster.dev-cluster.name}-standard-pool"
  location   = var.zones[0]
  cluster    = google_container_cluster.dev-cluster.name
  node_count = var.gke_num_nodes

//  autoscaling {
//    max_node_count = 6
//    min_node_count = 0
//  }
  node_config {
    oauth_scopes = [
      "https://www.googleapis.com/auth/logging.write",
      "https://www.googleapis.com/auth/monitoring",
      "https://www.googleapis.com/auth/devstorage.read_only",
    ]

    labels = {
      generated = "gke-tf"
    }
    service_account = google_service_account.gke-sa.email
    //preemptible  = true
    machine_type = var.gke_machine_type
    metadata = {
      disable-legacy-endpoints = "true"
    }
  }

    depends_on = [
    google_container_cluster.dev-cluster,
      google_project_service.service,
  ]
}

// Create the GKE service account
resource "google_service_account" "gke-sa" {
  account_id   = format("%s-sa", "${var.project_id}-cluster")
  display_name = "Terraform GKE Service Account"
  project      = var.project_id
}

// Add user-specified role
resource "google_project_iam_member" "service-account" {
  count   = length(var.service_account_iam_roles)
  project = var.project_id
  role    = element(var.service_account_iam_roles, count.index)
  member  = format("serviceAccount:%s", google_service_account.gke-sa.email)
  depends_on = [
    google_project_service.service,
  ]
}

// Enable required services on the project
resource "google_project_service" "service" {
  count   = length(var.project_services)
  project = var.project_id
  service = element(var.project_services, count.index)

  disable_on_destroy = false
}
