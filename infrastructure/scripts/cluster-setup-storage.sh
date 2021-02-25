#! /bin/sh -
#
# Create the Google Cloud Storage bucket.

set -eu
set -x

project="$(gcloud config get-value project)"

if ! gsutil ls | grep -q "^gs://$project/$"; then
	gsutil mb "gs://$project"
fi
