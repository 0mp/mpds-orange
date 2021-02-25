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

# Connect to the Hadoop pod and create necessary.
kubectl exec hadoop-hdfs-namenode-0 -c namenode -it -- /bin/bash <<COMMANDS
	set -eux
	for dir in /flink /flink/checkpoints /flink/savepoints; do
		if ! hadoop fs -test -d "\$dir"; then
			hadoop fs -mkdir "\$dir"
		fi
		hadoop fs -chown flink "\$dir"
	done
COMMANDS
