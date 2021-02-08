# mpds-orange

## Overall system design

### Autoscaling system

There are 2 Kafka topics used by the autoscaling system:

- `Metric` for message rate metrics
- `Prediction` for output results of the prediction models

#### Metrics

The following metrics are of interest to the Autoscaler:

- Max message latency (`flink_taskmanager_job_latency_source_id_operator_id_operator_subtask_index_latency` in Prometheus)
- Kafka messages in per second (`kafka_server_brokertopicmetrics_total_messagesinpersec_count` in Prometheus)
- Sink health metrics:
    - Maybe `kafka_controller_kafkacontroller_controllerstate_value` offers some interesting insights (details: https://cwiki.apache.org/confluence/display/KAFKA/KIP-143%3A+Controller+Health+Metrics)?

The following metrics are potentially interesting, but are not available available at this moment in Prometheus:
- Network in/out (to do)
- CPU Utilization (to do, probably available via Kubernetes API's)
- Memory Usage (to do, probably avialable via Kubernetes API's)

In order to obtain those metrics from Prometheus, it is necessary to send a POST request to the `/api/v1/query` endpoint. Here's an example using curl:

```
curl -X POST \
  -F query=kafka_server_brokertopicmetrics_total_messagesinpersec_count \
  prometheus:9090/api/v1/query
```


<details>
  <summary>The response body looks like this:</summary>

```
{
  "status": "success",
  "data": {
    "resultType": "vector",
    "result": [
      {
        "metric": {
          "__name__": "kafka_server_brokertopicmetrics_total_messagesinpersec_count",
          "app_kubernetes_io_component": "kafka",
          "app_kubernetes_io_instance": "mpds",
          "app_kubernetes_io_managed_by": "Helm",
          "app_kubernetes_io_name": "kafka",
          "controller_revision_hash": "kafka-7dc6cd8b54",
          "helm_sh_chart": "kafka-11.8.2",
          "instance": "10.1.0.10:5556",
          "job": "kubernetes-pods",
          "kubernetes_namespace": "default",
          "kubernetes_pod_name": "kafka-1",
          "statefulset_kubernetes_io_pod_name": "kafka-1"
        },
        "value": [
          1612726221.53,
          "84822793"
        ]
      },
      {
        "metric": {
          "__name__": "kafka_server_brokertopicmetrics_total_messagesinpersec_count",
          "app_kubernetes_io_component": "kafka",
          "app_kubernetes_io_instance": "mpds",
          "app_kubernetes_io_managed_by": "Helm",
          "app_kubernetes_io_name": "kafka",
          "controller_revision_hash": "kafka-7dc6cd8b54",
          "helm_sh_chart": "kafka-11.8.2",
          "instance": "10.1.1.6:5556",
          "job": "kubernetes-pods",
          "kubernetes_namespace": "default",
          "kubernetes_pod_name": "kafka-0",
          "statefulset_kubernetes_io_pod_name": "kafka-0"
        },
        "value": [
          1612726221.53,
          "126520535"
        ]
      },
      {
        "metric": {
          "__name__": "kafka_server_brokertopicmetrics_total_messagesinpersec_count",
          "app_kubernetes_io_component": "kafka",
          "app_kubernetes_io_instance": "mpds",
          "app_kubernetes_io_managed_by": "Helm",
          "app_kubernetes_io_name": "kafka",
          "controller_revision_hash": "kafka-7dc6cd8b54",
          "helm_sh_chart": "kafka-11.8.2",
          "instance": "10.1.2.6:5556",
          "job": "kubernetes-pods",
          "kubernetes_namespace": "default",
          "kubernetes_pod_name": "kafka-2",
          "statefulset_kubernetes_io_pod_name": "kafka-2"
        },
        "value": [
          1612726221.53,
          "126534259"
        ]
      }
    ]
  }
}
```
</details>

#### Prediction models

There are two prediction models available at the moment: a long-term one, and a short-term one. Both models are predicting the load based on the Kafka message rates.
