#! /bin/sh -

set -eu
set -x

# Connect to the Hadoop pod and create necessary.
kubectl exec hadoop-hdfs-namenode-0 -c namenode -i -- /bin/bash <<COMMANDS
	set -eux
	for dir in /flink /flink/checkpoints /flink/savepoints; do
		if ! hadoop fs -test -d "\$dir"; then
			hadoop fs -mkdir "\$dir"
		fi
		hadoop fs -chown flink "\$dir"
	done
COMMANDS
