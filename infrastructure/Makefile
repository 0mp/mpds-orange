CHARTS_DIR=	k8s/helm/charts

cluster-create:
	./scripts/cluster-setup-accounts.sh
	./scripts/cluster-setup-storage.sh
	./scripts/cluster-terraform.sh
	./scripts/hdfs-deploy.sh

helm-install: grafana-install kafka-install prometheus-install
helm-uninstall: grafana-uninstall kafka-uninstall prometheus-uninstall

grafana-install kafka-install prometheus-install:
	name="$$(echo "$@" | cut -d - -f 1)" && \
	cd "${CHARTS_DIR}/$$name" && \
	helm install "$$name" .

grafana-uninstall kafka-uninstall prometheus-uninstall:
	name="$$(echo "$@" | cut -d - -f 1)" && \
	helm uninstall "$$name"
