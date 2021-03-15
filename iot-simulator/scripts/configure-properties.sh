#! /bin/sh -
#
# Usage: $0 brokers hdfs_dir kafka_partitions

set -eu

#
# Main
#

brokers="$1"
hdfs_dir="$2"
kafka_partitions="$3"

cat << EOF | tee "./iot_vehicles_experiment/processor/src/main/resources/processor.properties"
# Kafka properties
kafka.brokers=$brokers
kafka.consumer.topic=iot-vehicles-events
kafka.producer.topic=iot-vehicles-notifications
kafka.partitions=$kafka_partitions

# HDFS properties
hdfs.backupFolder=$hdfs_dir

# Traffic properties
traffic.updateInterval=1000
traffic.speedLimit=70
traffic.windowSize=10000
EOF

cat << EOF | tee "./iot_vehicles_experiment/producer/src/main/resources/producer.properties"
# Traffic Generator properties
trafficGenerator.graphFileName=berlin-mitte.json
trafficGenerator.updateInterval=1000

# Dataset properties
dataset.fileName=IoT_21D_1S.csv

# Kafka properties
kafka.brokerList=$brokers
kafka.topic=iot-vehicles-events
kafka.partitions=$kafka_partitions
EOF

cat << JOB_ARGS | tee args
vehicles $brokers iot-vehicles-events iot-vehicles-notifications $kafka_partitions 30000
JOB_ARGS

# Patch TRANSACTION_TIMEOUT_CONFIG to a value suitable for our Kafka deployment.
tmpfile=$(mktemp)
run_java_file="iot_vehicles_experiment/processor/src/main/java/de/tu_berlin/dos/arm/iot_vehicles_experiment/processor/Run.java"
sed '/TRANSACTION_TIMEOUT_CONFIG/s/3600000/36000/' "$run_java_file" > "$tmpfile"
mv "$tmpfile" "$run_java_file"
