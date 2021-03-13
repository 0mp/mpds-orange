#! /bin/sh -
#
# Usage: env FLINK_DIR="..." FLINK_JOB_JAR="..." $0 [job-args ...]

set -eu
set -x

if [ ! -d "$FLINK_DIR" ]; then
       set +u
       echo "$0: FLINK_DIR is not a directory: $FLINK_DIR" >&2
       exit 1
fi

if [ ! -f "$FLINK_JOB_JAR" ]; then
       set +u
       echo "$0: FLINK_JOB_JAR is invalid: $FLINK_JOB_JAR" >&2
       exit 1
fi

flink_dir="$FLINK_DIR"
jar="$FLINK_JOB_JAR"

# Arguments before the "$jar" variable are passed to the "flink" script.
# All the arguments after "$jar", however, are Flink job's arguments.
"$flink_dir/bin/flink" run \
       --detached \
       --target kubernetes-session \
       --parallelism 1 \
       -Dkubernetes.cluster-id=flink-cluster \
       "$jar" \
       "$@"
