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
  gcloud container clusters get-credentials $(terraform output kubernetes_cluster_name) --region $(terraform output region)
  ```
* Repeat the Terraform commands in the same order to apply new changes or in case of failures, i.e.:
  ```
  terraform init
  terraform plan
  terraform apply
  ```
* To delete all resources created by Terraform, run:
  ```
  terraform destroy
  ```
  
## Building the artifacts
* Build the Flink Docker image
```
docker build -t eu.gcr.io/mpds-task-2/covid-engine:2.1.1 .
```
## Deploying the applications

Deploy the Hadoop cluster for HDFS:
```
gcloud dataproc clusters create hadoop --region=europe-west3
```

Kafka, Prometheus, and Grafana can be deployed on a Kubernetes cluster using the Helm charts located in the `infrastructure/k8s/helm` directory. Configure which charts to deploy in the global values.yaml by setting enabled: true for each desired technology. Cluster sizes and ports for external access can also be specified here.
Each subchart can be deployed by itself and contains its own values.yaml file with futher configurations. If deployed from the umbrella chart, values in the global values.yaml will overwrite the values in the subchart's values.yaml.

Deploy the charts with:
```
helm install [DEPLOYMENT NAME] [CHART DIRECTORY]
```
Uninstall the charts with:
```
helm uninstall [DEPLOYMENT NAME]
```
Deploy the Flink cluster using the cli from the downloaded Flink package

```
./bin/flink run-application \
    --target kubernetes-application \
    -Dkubernetes.cluster-id=mpds-task-2-cluster \
    -Dkubernetes.container.image=eu.gcr.io/mpds-task-2/covid-engine:2.1.1 \
    local:///opt/flink/usrlib/covid-engine-2.1.1.jar
```

Once the application cluster is deployed you can interact with it:
```
# List running job on the cluster
$ ./bin/flink list --target kubernetes-application -Dkubernetes.cluster-id=mpds-task-2-cluster
# Cancel running job
$ ./bin/flink cancel --target kubernetes-application -Dkubernetes.cluster-id=mpds-task-2-cluster <jobId>  
```
_You can override configurations set in conf/flink-conf.yaml by passing key-value pairs -Dkey=value to bin/flink_

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

## Troubleshooting
* Sometimes the Terraform commands don't work immediately. In that case, repeat the Terraform commands (see above)
* Update the latest GKE stable version if errors are thrown related to that on the Terraform main.tf file
* Enable the APIs manually through the GCP console if required
