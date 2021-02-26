#! /bin/sh -

set -eu
set -x

if [ ! -d "$FLINK_DIR" ]; then
	set +u
	echo "$0: FLINK_DIR is not a directory: $FLINK_DIR" >&2
	exit 1
fi

if [ ! -f "$FLINK_JOB_JAR_COVID_ENGINE" ]; then
	set +u
	echo "$0: FLINK_JOB_JAR_COVID_ENGINE is invalid: $FLINK_JOB_JAR_COVID_ENGINE" >&2
	exit 1
fi

flink_dir="$FLINK_DIR"
jar="$FLINK_JOB_JAR_COVID_ENGINE"

# Arguments before the "$jar" variable are passed to the "flink" script.
# All the arguments after "$jar", however, are Flink job's arguments.
"$flink_dir/bin/flink" run \
	--detached \
	--target kubernetes-session \
	--parallelism 1 \
	-Dkubernetes.cluster-id=flink-cluster \
	-Dstate.savepoints.dir=hdfs://hadoop-hdfs-namenode:8020/flink/savepoints/savepoint-040a83-73e0bac50483 \
	"$jar" \
	--statebackend.default false \
	--checkpoint hdfs://hadoop-hdfs-namenode:8020/flink/checkpoints \
	--checkpoint.interval 300000
