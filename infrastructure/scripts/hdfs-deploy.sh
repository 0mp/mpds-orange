#! /bin/sh -

set -eu
set -x

# Add gradiant helm repo.
helm repo add gradiant https://gradiant.github.io/charts

# Use Helm to install HDFS with persistent volumes (see
# https://hub.kubeapps.com/charts/gradiant/hdfs).
helm install hadoop \
	--set persistence.nameNode.enabled=true \
	--set persistence.nameNode.storageClass=standard \
	--set persistence.dataNode.enabled=true \
	--set persistence.dataNode.storageClass=standard \
	gradiant/hdfs
