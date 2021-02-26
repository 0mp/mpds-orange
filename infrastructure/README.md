# Setting up the infrastructure

The Docker images and Helm charts can be deployed on any Kubernetes Cluster. In this case, the infrastructure was setup
through the Google Kubernetes Engine (GKE). However, if the Kubernetes Cluster was set up on any other cloud provider or
on-premise, the Helm charts can be used to do the deployments of the application components.

## Prerequisites

* Install Terraform (see https://learn.hashicorp.com/tutorials/terraform/install-cli?in=terraform/gcp-get-started)
* Install the gcloud sdk (see https://cloud.google.com/sdk) in case the gcp commands are executed from the local machine
  instead of using Google Cloud Shell`
* Download the Flink package to get the cli and extract it: 
  ```
  wget https://downloads.apache.org/flink/flink-1.12.1/flink-1.12.1-bin-scala_2.12.tgz 
  tar -xf flink-1.12.1-bin-scala_2.12.tgz
  ```
  
## Building the artifacts
* Build the Flink Docker image using the files under /infrastructure/docker/flink from the project root path
```
docker build -t eu.gcr.io/mpds-task-2/covid-engine:2.3.0 .
```
* Push the created image to the Container Registry
```
docker push eu.gcr.io/mpds-task-2/covid-engine:2.3.0
```

## Setup GKE cluster

_Skip this section if there is already an existent Kubernetes Cluster_

* Use Google Cloud Shell (see https://cloud.google.com/shell) or the gcloud sdk with a cli to run the commands from your
  local machine
* Authenticate through gcloud if no gcp service account is used:
  ```
  gcloud auth application-default login
  ```
* Create Service Account for Terraform
  ```
  gcloud iam service-accounts create terraform \
   --description="This service account is used for Terraform" \
   --display-name="Terraform"
  ```
* Create IAM policy binding
  ```
  gcloud projects add-iam-policy-binding mpds-task-2 \
   --member="serviceAccount:terraform@mpds-task-2.iam.gserviceaccount.com" \
   --role="roles/owner"
  ```
* Add IAM policy binding service account user to user accounts
  ```
  gcloud iam service-accounts add-iam-policy-binding \
   terraform@mpds-task-2.iam.gserviceaccount.com \
   --member="user:MY_GCP_EMAIL_ADDRESS" \
   --role="roles/iam.serviceAccountUser"
  ```
  _While Replacing MY_GCP_EMAIL_ADDRESS with the real account_
* Create service account key for Terraform
  ```
  gcloud iam service-accounts keys create ./key.json \
  --iam-account terraform@mpds-task-2.iam.gserviceaccount.com
  ```
* Retrieve the IAM roles if required:
  ```
  gcloud projects get-iam-policy mpds-task-2 \
  --flatten="bindings[].members" \
  --format='table(bindings.role)' \
  --filter="bindings.members:terraform@mpds-task-2.iam.gserviceaccount.com"
  ```
  
* Create the Google Cloud Storage bucket through the GCP console:
  ```
  mpds-task-2
  ```
* Navigate to the folder k8s/terraform and initialize Terraform through the command:
  ```
  terraform init
  ```
* Validate the Terraform plan:
  ```
  terraform plan
  ```
* Apply the Terraform plan and confirm the action:
  ```
  terraform apply
  ```
* Configure kubectl with Terraform
  ```
  gcloud container clusters get-credentials $(terraform output cluster_name) --zone $(terraform output zone)
  ```
* Repeat the Terraform commands in the same order to apply new changes or in case of failures, i.e.:
  ```
  terraform init
  terraform plan
  terraform apply
  ```
  
## Deploying the applications

### Deploying Hadoop for HDFS
SSH into the master node and create folder on HDFS
```
hadoop fs -mkdir /flink
hadoop fs -mkdir /flink/checkpoints
```
Add gradiant helm repo:
```
  helm repo add gradiant https://gradiant.github.io/charts
```
Use Helm to install HDFS with persistent volumes (see https://hub.kubeapps.com/charts/gradiant/hdfs):
```
helm install hadoop \
  --set persistence.nameNode.enabled=true \
  --set persistence.nameNode.storageClass=standard \
  --set persistence.dataNode.enabled=true \
  --set persistence.dataNode.storageClass=standard \
  gradiant/hdfs
```

Connect to the Hadoop pod
```
kubectl exec -it hadoop-hdfs-namenode-0 bash
```

Run the following commands on the Hadoop name node pod
```
hadoop fs -ls /
hadoop fs -mkdir /flink
hadoop fs -mkdir /flink/checkpoints
hadoop fs -mkdir /flink/savepoints
hadoop fs -chown flink /flink
hadoop fs -chown flink /flink/checkpoints
hadoop fs -chown flink /flink/savepoints
```

#### TO BE REMOVED since the approach was not suitable
Deploy the Hadoop cluster for HDFS with dataproc:
```
gcloud dataproc clusters create hadoop --region=europe-west3
```
Delete Dataproc-Cluster
```
  gcloud dataproc clusters delete hadoop --region=europe-west3
```

### Deploying Kafka, Prometheus, Grafana
Kafka, Prometheus, and Grafana can be deployed on a Kubernetes cluster using the Helm charts located in the `infrastructure/k8s/helm` directory. Configure which charts to deploy in the global values.yaml by setting enabled: true for each desired technology. Cluster sizes and ports for external access can also be specified here.
Each subchart can be deployed by itself and contains its own values.yaml file with futher configurations. If deployed from the umbrella chart, values in the global values.yaml will overwrite the values in the subchart's values.yaml.

Deploy the charts with:
```
helm install [DEPLOYMENT NAME] [CHART DIRECTORY]
```

Get the Grafana URL to visit by running these commands in the same shell:
  ```
  export NODE_PORT=$(kubectl get --namespace default -o jsonpath="{.spec.ports[0].nodePort}" services grafana)
  export NODE_IP=$(kubectl get nodes -o jsonpath='{.items[0].status.addresses[?(@.type=="ExternalIP")].address}')
  echo http://$NODE_IP:$NODE_PORT
  ```

### Deploying native Kubernetes Apache Flink 

Create clusterrolebinding on Kubernetes for Flink
```
$ kubectl create clusterrolebinding flink-role-binding-default --clusterrole=edit --serviceaccount=default:default
```ls


If you do not want to use the default service account, use the following command to create a new flink-service-account service account and set the role binding. 
Then use the config option -Dkubernetes.service-account=flink-service-account to make the JobManager pod use the flink-service-account service account to create/delete TaskManager pods and leader ConfigMaps. 
Also this will allow the TaskManager to watch leader ConfigMaps to retrieve the address of JobManager and ResourceManager.
```
$ kubectl create serviceaccount flink-service-account
$ kubectl create clusterrolebinding flink-role-binding-flink --clusterrole=edit --serviceaccount=default:flink-service-account
```

#### Deploy the Flink cluster using the cli from the downloaded Flink package
A Flink native Kubernetes cluster in session mode could be deployed like this:
```
./bin/kubernetes-session.sh \
    -Dkubernetes.cluster-id=flink-cluster \
    -Dkubernetes.container.image=eu.gcr.io/mpds-task-2/covid-engine:2.3.1 \
    -Dkubernetes.container.image.pull-policy=Always \
    -Dexecution.attached=true \
    -Dkubernetes.jobmanager.annotations=prometheus.io/scrape:'true',prometheus.io/port:'9999' \
    -Dkubernetes.taskmanager.annotations=prometheus.io/scrape:'true',prometheus.io/port:'9999' \
    -Dmetrics.latency.granularity=OPERATOR \
    -Dmetrics.latency.interval=1000 \
    -Dmetrics.reporters=prom \
    -Dmetrics.reporter.prom.class=org.apache.flink.metrics.prometheus.PrometheusReporter \
    -Dmetrics.reporter.prom.port=9999 \
    -Dmetrics.reporter.jmx.class=org.apache.flink.metrics.jmx.JMXReporter \
    -Dmetrics.reporter.jmx.port=8789 \
    -Dstate.savepoints.dir=hdfs://hadoop-hdfs-namenode:8020/flink/savepoints
```

Get the Flink Web UI URL:
  ```
  export NODE_PORT=$(kubectl get --namespace default -o jsonpath="{.spec.ports[0].nodePort}" services flink-cluster-rest)
  export NODE_IP=$(kubectl get nodes -o jsonpath='{.items[0].status.addresses[?(@.type=="ExternalIP")].address}')
  echo http://$NODE_IP:$NODE_PORT
  ````

Submit the Flink job and start the application, e.g.:
```
 Program Arguments: --statebackend.default false --checkpoint hdfs://hadoop-hdfs-namenode:8020/flink/checkpoints --checkpoint.interval 300000
 Parallelism: 1
 Savepoint Path: hdfs://hadoop-hdfs-namenode:8020/flink/savepoints/savepoint-040a83-73e0bac50483
```


Alternatively, Flink could be deployed in application mode
```        
./bin/flink run-application \
    --target kubernetes-application \
    -Dkubernetes.cluster-id=flink-cluster \
    -Dkubernetes.container.image=eu.gcr.io/mpds-task-2/covid-engine:2.3.1 \
    -Dkubernetes.container.image.pull-policy=Always \
    -Dkubernetes.jobmanager.annotations=prometheus.io/scrape:'true',prometheus.io/port:'9999' \
    -Dkubernetes.taskmanager.annotations=prometheus.io/scrape:'true',prometheus.io/port:'9999' \
    -Dmetrics.latency.granularity=OPERATOR \
    -Dmetrics.latency.interval=1000 \
    -Dmetrics.reporters=prom \
    -Dmetrics.reporter.prom.class=org.apache.flink.metrics.prometheus.PrometheusReporter \
    -Dmetrics.reporter.prom.port=9999 \
    -Dmetrics.reporter.jmx.class=org.apache.flink.metrics.jmx.JMXReporter \
    -Dmetrics.reporter.jmx.port=8789 \
    -Dstate.savepoints.dir=hdfs://hadoop-hdfs-namenode:8020/flink/savepoints \
    local:///opt/flink/usrlib/covid-engine-2.3.1.jar \
    --statebackend.default false \
    --checkpoint hdfs://hadoop-hdfs-namenode:8020/flink/checkpoints \
    --checkpoint.interval 300000    
    
    
    // Start a Flink cluster from a specific savepoint
    ./bin/flink run-application \
    --target kubernetes-application \
    --parallelism 3 \
    --fromSavepoint hdfs://hadoop-hdfs-namenode:8020/flink/savepoints/savepoint-f856bd-8b076fb00f92/_metadata\
    -Dkubernetes.cluster-id=flink-cluster \
    -Dkubernetes.container.image=eu.gcr.io/mpds-task-2/covid-engine:2.3.1 \
    -Dkubernetes.container.image.pull-policy=Always \
    -Dkubernetes.jobmanager.annotations=prometheus.io/scrape:'true',prometheus.io/port:'9999' \
    -Dkubernetes.taskmanager.annotations=prometheus.io/scrape:'true',prometheus.io/port:'9999' \
    -Dmetrics.latency.granularity=OPERATOR \
    -Dmetrics.latency.interval=1000 \
    -Dmetrics.reporters=prom \
    -Dmetrics.reporter.prom.class=org.apache.flink.metrics.prometheus.PrometheusReporter \
    -Dmetrics.reporter.prom.port=9999 \
    -Dmetrics.reporter.jmx.class=org.apache.flink.metrics.jmx.JMXReporter \
    -Dmetrics.reporter.jmx.port=8789 \
    -Dstate.savepoints.dir=hdfs://hadoop-hdfs-namenode:8020/flink/savepoints \
    local:///opt/flink/usrlib/covid-engine-2.3.1.jar \
    --statebackend.default false \
    --checkpoint hdfs://hadoop-hdfs-namenode:8020/flink/checkpoints \
    --checkpoint.interval 300000
```
Once the application cluster is deployed you can interact with it:
```
# List running job on the cluster
$ ./bin/flink list --target kubernetes-application -Dkubernetes.cluster-id=flink-cluster
# Cancel running job
$ ./bin/flink cancel --target kubernetes-application -Dkubernetes.cluster-id=flink-cluster <jobId>  
```
_You can override configurations set in conf/flink-conf.yaml by passing key-value pairs -Dkey=value to bin/flink_

Stops the Flink cluster if is not needed anymore, e.g.:
```
./bin/flink stop \
--target kubernetes-application \
-Dkubernetes.cluster-id=flink-cluster 7f41ed2c0a863ea5ddfa2c315416fceb
```

## Viewing metrics in Grafana

<p align="center">
  <img width="460" height="300" src="images/grafana.png">
</p>

Grafana is accessible at <kubernetes_node_ip>:<nodeport>.
The default nodeport is ``30080`` and the default username and password is ``admin``

After logging into Grafana, the data source must be added.
Navigate to: ``Configuration > Data Sources > Add data source > Prometheus``
Set the Url to ``prometheus:9090`` and click save and test. You should see a green notification that the data source is working.

To import the premade grafana dashboard to show metrics, navigate to:
``Create > Import > Upload JSON file``
Upload the ``grafana-dashboard.json`` file from the root directory.

## Flink DSP Engine

<p align="center">
  <img width="460" height="300" src="images/pipeline.jpg">
</p>

## Removal & Cleanup
Manual Resource Cleanup for FLink
```
kubectl delete deployment/flink-cluster
```
Uninstall the charts with:
```
  helm uninstall [DEPLOYMENT NAME]
```
To delete all resources created by Terraform, run:
  ```
  terraform destroy
  ```

## Troubleshooting
* Sometimes the Terraform commands don't work immediately. In that case, repeat the Terraform commands mentioned above (see (see https://stackoverflow.com/questions/62106154/frequent-error-when-deploying-helm-to-gke-with-terraform))
* Update the latest GKE stable version if errors are thrown related to that on the Terraform main.tf file
* Enable the APIs manually through the GCP console if required
* Get cluster credentials without Terraform if required
```
  gcloud container clusters get-credentials mpds-task-orange-cluster --zone europe-west3-a
```
