# mpds-orange

## Overall system design

### Autoscaling system

There are 2 Kafka topics used by the autoscaling system:

- `Metric` for message rate metrics
- `Prediction` for output results of the prediction models

#### Metrics

See the [Prometheus Postman collection](./Prometheus.postman_collection.json).

<details>
  <summary>Example of a Prometheus API request and response:</summary>

Here's the request. It is not a single HTTP request, but a separate request for each metric. Although it is technically possible to get all the metrics at once (as per [the documentation](https://prometheus.io/docs/prometheus/latest/querying/basics/)), we would have to do aggregations on our own. Having multiple queries is not a problem, however. We can simply specify a `time` field in the HTTP request to retrieve a consistent set of metrics.

```sh
request() {
	address="http://prometheus:30090"
	endpoint="/api/v1/query"
	url="${address}${endpoint}"
	time="2021-02-08T10:10:51.781Z"

	curl -Ss -X POST -F query="$1" -F time="$time" "$url" | jq
}

request "avg(avg by (operator_id) (flink_taskmanager_job_latency_source_id_operator_id_operator_subtask_index_latency{quantile=\"0.95\"}))"
request "avg(kafka_server_brokertopicmetrics_total_messagesinpersec_count)"
request kafka_controller_kafkacontroller_controllerstate_value
```

The response:

```
{
  "status": "success",
  "data": {
    "resultType": "vector",
    "result": [
      {
        "metric": {},
        "value": [
          1612779051.781,
          "181.43333333333334"
        ]
      }
    ]
  }
}
{
  "status": "success",
  "data": {
    "resultType": "vector",
    "result": [
      {
        "metric": {},
        "value": [
          1612779051.781,
          "112629192.33333334"
        ]
      }
    ]
  }
}
{
  "status": "success",
  "data": {
    "resultType": "vector",
    "result": [
      {
        "metric": {
          "__name__": "kafka_controller_kafkacontroller_controllerstate_value",
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
          1612779051.781,
          "0"
        ]
      },
      {
        "metric": {
          "__name__": "kafka_controller_kafkacontroller_controllerstate_value",
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
          1612779051.781,
          "0"
        ]
      },
      {
        "metric": {
          "__name__": "kafka_controller_kafkacontroller_controllerstate_value",
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
          1612779051.781,
          "0"
        ]
      }
    ]
  }
}
```
</details>

The following metrics are potentially interesting, but are not available available at this moment in Prometheus:
- Network in/out (to do)
- CPU Utilization (to do, probably available via Kubernetes API's)
- Memory Usage (to do, probably avialable via Kubernetes API's)


#### Prediction models

There are two prediction models available at the moment: a long-term one, and a short-term one. Both models are predicting the load based on the Kafka message rates.
