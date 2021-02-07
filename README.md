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
- Records per second (potentially `flink_taskmanager_job_task_numRecordsIn` in Prometheus)
- Network in/out (to do)
- Sink health metrics:
    - Maybe `kafka_controller_kafkacontroller_controllerstate_value` offers some interesting insights (details: https://cwiki.apache.org/confluence/display/KAFKA/KIP-143%3A+Controller+Health+Metrics)?
- CPU Utilization (to do, probably available via Kubernetes API's)
- Memory Usage (to do, probably avialable via Kubernetes API's)

#### Prediction models

There are two prediction models available at the moment: a long-term one, and a short-term one. Both models are predicting the load based on the Kafka message rates.
