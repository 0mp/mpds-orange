output "region" {
  value       = var.region
  description = "region"
}

output "zone" {
  value       = var.zones[0]
  description = "zone"
}

output "cluster_name" {
  description = "Cluster name"
  value       = "${var.project_id}-cluster"
}

output "cluster_location" {
  description = "Cluster location"
  value       = var.region
}

output "cluster_endpoint" {
  description = "Cluster endpoint"
  value       = google_container_cluster.dev-cluster.endpoint
}

output "cluster_ca_certificate" {
  sensitive   = true
  description = "Cluster ca certificate (base64 encoded)"
  value       = google_container_cluster.dev-cluster.master_auth[0].cluster_ca_certificate
}

output "get_credentials" {
  description = "Gcloud get-credentials command"
  value       = format("gcloud container clusters get-credentials --project %s --region %s %s", var.project_id, var.zones[0], "${var.project_id}-cluster")
}
