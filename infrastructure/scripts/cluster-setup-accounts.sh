#! /bin/sh -

set -eu
set -x

project="mpds-task-orange"
terraform_user="terraform"
terraform_service_account="$terraform_user@$project.iam.gserviceaccount.com"

# Authenticate through gcloud if no gcp service account is used:
if [ "$(gcloud config get-value account)" = "" ]; then
	gcloud auth application-default login
fi

# Create Service Account for Terraform
if ! gcloud iam service-accounts list | grep -q "$terraform_service_account"; then
	gcloud iam service-accounts create "$terraform_user" \
		--description="This service account is used for Terraform" \
		--display-name="Terraform"
fi

# Create IAM policy binding
gcloud projects add-iam-policy-binding "$project" \
	--member="serviceAccount:${terraform_user}@${project}.iam.gserviceaccount.com" \
	--role="roles/owner"

# Add IAM policy binding service account user to user accounts
gcloud iam service-accounts add-iam-policy-binding \
	"${terraform_service_account}" \
	--member="user:$(gcloud config get-value account)" \
	--role="roles/iam.serviceAccountUser"

# Create service account key for Terraform
gcloud iam service-accounts keys create ./key.json \
	--iam-account "$terraform_service_account"

exit 0
