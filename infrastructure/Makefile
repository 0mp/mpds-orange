HELM_DIR=	k8s/helm
PROJECT_NAME=	mpds-task-orange

cluster-create:
	./scripts/cluster-setup-accounts.sh
	./scripts/cluster-setup-storage.sh
	./scripts/cluster-terraform.sh
	${MAKE} flink-build-docker-image
	${MAKE} flink-push-docker-image

cluster-destroy:
	cd k8s/terraform && terraform destroy

services-install:
	${MAKE} hdfs-install
	${MAKE} mpds-install
	${MAKE} flink-install

services-uninstall:
	-${MAKE} hdfs-uninstall
	-${MAKE} mpds-uninstall
	-${MAKE} flink-uninstall

hdfs-install:
	./scripts/hdfs-deploy.sh
	sleep 10
	./scripts/hdfs-configure.sh

hdfs-uninstall:
	helm uninstall hadoop

mpds-install:
	cd ${HELM_DIR} && helm install mpds .

mpds-uninstall:
	helm uninstall mpds

mpds-upgrade:
	cd ${HELM_DIR} && helm upgrade mpds .

FLINK_DIR=	flink-1.12.1
FLINK_TARBALL=	${FLINK_DIR}-bin-scala_2.12.tgz
FLINK_DOCKER_IMAGE=	eu.gcr.io/${PROJECT_NAME}/flink-engine-java11-1.0.0

${FLINK_TARBALL}:
	wget -O "$@" "https://downloads.apache.org/flink/flink-1.12.1/$@"

${FLINK_DIR}: ${FLINK_TARBALL}
	tar -xf "${FLINK_TARBALL}"

flink-fetch-dependencies: ${FLINK_DIR}

# Build the Flink job Docker image.
flink-build-docker-image:
	cd docker/flink && \
	docker build -t ${FLINK_DOCKER_IMAGE} .

# Push the created image to the Container Registry.
flink-push-docker-image:
	gcloud auth configure-docker
	cd docker/flink && \
	docker push ${FLINK_DOCKER_IMAGE}

flink-install: ${FLINK_DIR}
	export FLINK_DIR="${FLINK_DIR}" && \
	export FLINK_DOCKER_IMAGE="${FLINK_DOCKER_IMAGE}" && \
	./scripts/flink-install.sh

flink-uninstall:
	kubectl get deployments | awk '/^flink-cluster.*/{ print $$1 }' | \
		xargs -n 1 kubectl delete deployment

flink-get-web-ui:
	node_port=$$(kubectl get --namespace default -o jsonpath="{.spec.ports[0].port}" services flink-cluster-rest) && \
	node_ip=$$(kubectl get --namespace default -o jsonpath="{.status.loadBalancer.ingress[0].ip}" services flink-cluster-rest) && \
	echo "http://$$node_ip:$$node_port"

flink-run-job-iot:
	export FLINK_DIR="${FLINK_DIR}" && \
	export FLINK_JOB_JAR="../iot-simulator/iot_vehicles_experiment/processor/target/processor-1.0-SNAPSHOT.jar" && \
	./scripts/flink-run-job.sh \
	$$(cat ../iot-simulator/args)

flink-stop-jobs:
	-${FLINK_DIR}/bin/flink list \
		--target kubernetes-session \
		-Dkubernetes.cluster-id=flink-cluster | \
	grep -Eo '[0-9a-z]{32}' | \
	xargs -n 1 ${FLINK_DIR}/bin/flink cancel

FLINK_JOB_JAR=	covid-engine-2.3.2.jar

flink-get-ids:
	@printf '    job-id: '
	@${FLINK_DIR}/bin/flink list --target kubernetes-session -Dkubernetes.cluster-id=flink-cluster | \
		grep -Eo '[0-9a-z]{32}'
	@printf '    jar-id: '
	@node_port=$$(kubectl get --namespace default -o jsonpath="{.spec.ports[0].port}" services flink-cluster-rest) && \
	node_ip=$$(kubectl get --namespace default -o jsonpath="{.status.loadBalancer.ingress[0].ip}" services flink-cluster-rest) && \
	curl -Ss --location --request GET "http://$$node_ip:$$node_port/jars" | \
		jq -r '.files[] | select(.name=="${FLINK_JOB_JAR}") | .id'

kafka-show-brokers:
	node_ip=$$(kubectl get nodes -o jsonpath='{.items[0].status.addresses[?(.type=="ExternalIP")].address}') && \
	kafkacat -b $$node_ip:31090 -L | grep '  broker'

grafana-get-web-ui:
	node_port=30080 && \
	node_ip=$$(kubectl get nodes -o jsonpath='{.items[0].status.addresses[?(.type=="ExternalIP")].address}') && \
	echo "http://$$node_ip:$$node_port"
