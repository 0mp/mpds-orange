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
	./scripts/hdfs-deploy.sh
	cd ${HELM_DIR} && helm install mpds .
	${MAKE} flink-deploy

services-uninstall:
	-helm uninstall hadoop
	-helm uninstall mpds
	-${MAKE} flink-stop

FLINK_DIR=	flink-1.12.1
FLINK_TARBALL=	${FLINK_DIR}-bin-scala_2.12.tgz
FLINK_DOCKER_IMAGE_COVID_ENGINE=	eu.gcr.io/${PROJECT_NAME}/covid-engine:2.3.1

${FLINK_TARBALL}:
	wget -O "$@" "https://downloads.apache.org/flink/flink-1.12.1/$@"

${FLINK_DIR}: ${FLINK_TARBALL}
	tar -xf "${FLINK_TARBALL}"

flink-fetch-dependencies: ${FLINK_DIR}

# Build the Flink job Docker image.
flink-build-docker-image:
	cd docker/flink && \
	docker build -t ${FLINK_DOCKER_IMAGE_COVID_ENGINE} .

# Push the created image to the Container Registry.
flink-push-docker-image:
	gcloud auth configure-docker
	cd docker/flink && \
	docker push ${FLINK_DOCKER_IMAGE_COVID_ENGINE}

flink-deploy: ${FLINK_DIR}
	export FLINK_DIR="${FLINK_DIR}" && \
	export FLINK_DOCKER_IMAGE_COVID_ENGINE="${FLINK_DOCKER_IMAGE_COVID_ENGINE}" && \
	./scripts/flink-deploy.sh

flink-stop:
	kubectl get deployments | awk '/^flink-cluster.*/{ print $$1 }' | \
		xargs -n 1 kubectl delete deployment

flink-get-web-ui:
	node_port=$$(kubectl get --namespace default -o jsonpath="{.spec.ports[0].nodePort}" services flink-cluster-rest) && \
	node_ip=$$(kubectl get nodes -o jsonpath='{.items[0].status.addresses[?(@.type=="ExternalIP")].address}') && \
	echo "http://$$node_ip:$$node_port"
