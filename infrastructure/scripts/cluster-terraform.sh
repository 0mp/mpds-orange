#! /bin/sh -
#
# Rerun this script in case of an error.

set -eu
set -x

project="$(gcloud config get-value project)"

# Set variables in Terraform files.
for file in backend.tf variables.tf; do
	(cd k8s/terraform && \
		sed "s|%%PROJECT_NAME%%|${project}|g" "${file}.in" > "$file")
done

# Put the key file into Terraform's directory.
ln -fs "$PWD/key.json" k8s/terraform/key.json

# Navigate to the folder k8s/terraform and initialize Terraform.
(cd k8s/terraform && terraform init)

# Validate the Terraform plan.
(cd k8s/terraform && terraform plan)

# Apply the Terraform plan and confirm the action.
(cd k8s/terraform && terraform apply)

# Configure kubectl with Terraform.
(cd k8s/terraform && gcloud container clusters get-credentials \
	"$(terraform output cluster_name)" \
	--zone "$(terraform output zone)")
