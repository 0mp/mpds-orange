IOT_VEHICLES_EXPERIMENT_DIR=	./iot_vehicles_experiment

.PHONY: configure
configure:
	./scripts/configure-properties.sh

.PHONY: build
build: configure
	cd ${IOT_VEHICLES_EXPERIMENT_DIR} && mvn clean package

${IOT_VEHICLES_EXPERIMENT}:
	git clone git@github.com:dos-group/iot_vehicles_experiment.git

.PHONY: producer-start
producer-start:
	cd ${IOT_VEHICLES_EXPERIMENT_DIR} && java -jar ./producer/target/producer-1.0-SNAPSHOT.jar
