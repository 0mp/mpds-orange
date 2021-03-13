IOT_VEHICLES_EXPERIMENT_DIR=	./iot_vehicles_experiment
HDFS_BACKUP_DIR=	hdfs://hadoop-hdfs-namenode:8020/flink/iot
KAFKA_PARTITIONS=	8

.PHONY: configure
configure:
	./scripts/configure-properties.sh ${HDFS_BACKUP_DIR} ${KAFKA_PARTITIONS}

.PHONY: build
build: configure
	cd ${IOT_VEHICLES_EXPERIMENT_DIR} && mvn clean package

${IOT_VEHICLES_EXPERIMENT}:
	git clone git@github.com:dos-group/iot_vehicles_experiment.git

.PHONY: producer-start
producer-start:
	cd ${IOT_VEHICLES_EXPERIMENT_DIR} && java -jar ./producer/target/producer-1.0-SNAPSHOT.jar
