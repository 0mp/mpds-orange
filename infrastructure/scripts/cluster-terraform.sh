#! /bin/sh -
#
# Rerun this script in case of an error.

set -eu
set -x

cd k8s/terraform

# Navigate to the folder k8s/terraform and initialize Terraform.
terraform init

# Validate the Terraform plan.
terraform plan

# Apply the Terraform plan and confirm the action.
terraform apply

# Configure kubectl with Terraform.
gcloud container clusters get-credentials \
	"$(terraform output cluster_name)" \
	--zone "$(terraform output zone)"
