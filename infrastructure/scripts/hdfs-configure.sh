#! /bin/sh -

set -eu
set -x

dirs="/flink /flink/checkpoints /flink/savepoints /flink/iot"

# Connect to the Hadoop pod and create necessary.
kubectl exec hadoop-hdfs-namenode-0 -c namenode -i -- /bin/bash <<COMMANDS
	set -eux
	for dir in $dirs; do
		if ! hadoop fs -test -d "\$dir"; then
			hadoop fs -mkdir "\$dir"
		fi
		hadoop fs -chown flink "\$dir"
	done
COMMANDS
