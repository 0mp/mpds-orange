#! /bin/sh -

get_brokers() {
	node_ip=$(gcloud compute instances list --format="value(EXTERNAL_IP)" | head -n 1)
	kafkacat -b "$node_ip:31090" -L | grep '  broker' | awk '{print $4}' | tr '\n' ','
}

#
# Globals
#

partitions=8
brokers=$(get_brokers)
# Remove a trailing comma.
brokers=${brokers%,}

cat << EOF | tee "./iot_vehicles_experiment/processor/src/main/resources/processor.properties"
# Kafka properties
kafka.brokers=$brokers
kafka.consumer.topic=iot-vehicles-events
kafka.producer.topic=iot-vehicles-notifications
kafka.partitions=$partitions

# HDFS properties
hdfs.backupFolder=hdfs://hadoop-hdfs-namenode:8020/flink/savepoints

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
kafka.partitions=$partitions
EOF

cat << JOB_ARGS | tee args
vehicles $brokers iot-vehicles-events iot-vehicles-notifications $partitions 30000
JOB_ARGS

