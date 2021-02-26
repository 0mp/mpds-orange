CHARTS_DIR=	k8s/helm/charts
PROJECT_NAME=	mpds-task-orange

cluster-create:
	./scripts/cluster-setup-accounts.sh
	./scripts/cluster-setup-storage.sh
	./scripts/cluster-terraform.sh
	./scripts/hdfs-deploy.sh

cluster-destroy:
	cd k8s/terraform && terraform destroy

helm-install: grafana-install kafka-install prometheus-install
helm-uninstall: grafana-uninstall kafka-uninstall prometheus-uninstall

grafana-install kafka-install prometheus-install:
	name="$$(echo "$@" | cut -d - -f 1)" && \
	cd "${CHARTS_DIR}/$$name" && \
	helm install "$$name" .

grafana-uninstall kafka-uninstall prometheus-uninstall:
	name="$$(echo "$@" | cut -d - -f 1)" && \
	helm uninstall "$$name"

FLINK_DIR=	flink-1.12.1
FLINK_TARBALL=	${FLINK_DIR}-bin-scala_2.12.tgz
FLINK_DOCKER_IMAGE_COVID_ENGINE=	eu.gcr.io/${PROJECT_NAME}/covid-engine:2.3.1

${FLINK_TARBALL}:
	wget -O "$@" "https://downloads.apache.org/flink/flink-1.12.1/$@"

${FLINK_DIR}: ${FLINK_TARBALL}
	tar -xf "${FLINK_TARBALL}"

flink-fetch-dependencies: ${FLINK_DIR}

flink-build-docker-image:
	cd docker/flink && \
	docker build -t ${FLINK_DOCKER_IMAGE_COVID_ENGINE} .

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
