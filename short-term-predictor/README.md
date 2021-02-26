# Short Term Predictor

## Howto Run


1. Configuration

in short-term-predictor.py set the url and port of kafka brokers

2. Run

```
python3 short-term-predictor.py
```




### Regression over configurable amount of previous time, in order to extrapolate for a section of time in the future
1. Do Quadratic Regression
2. Check if largest coefficient is Positive, Publish Quadratic Regression Extrapolation
3. If Negative, Publish Linear Regression Extrapolation

### Spike Detection
1. Check if New data point is beyond x * MSE of linear regression of current data set
2. If Yes, do not add to regression data set, add to Spike set
2.1 If spike set length is > 3, reset regression data set to spike set, clear spike set
3. If No, Continue as usual

### Incoming Data, 'metric' topic kafka example:
{
    "eventType":"MetricReported",
    "uuid":"87c8cac1-140d-4c99-85d6-85bbf722498d",
    "eventType":"MetricReported",
    "occurredOn":"2021-02-21T17:18:44Z",
    "kafkaTopic":"covid",
    "kafkaMessagesPerSeconds":0.005931407,
    "kafkaMaxMessageLatency":0,
    "recordsProcessedPerSeconds":0,
    "networkInPerSeconds":0.0,
    "networkOutPerSeconds":0.0,
    "sinkHealthy":true,
    "cpuUtilization":0.005931407,
    "memoryUsage":0.5370938,
    "flinkTopic":""
}

Outgoing Data, 'st_prediction' topic example message:
{
    "occurredOn": timestamp of last observation
    "predictedWorkload":prediction
    "eventTriggerUuid": uuid of the last observation
    "eventType":"PredictionReported",
    "predictionBasedOnDateTime": timestamp of the predicted workload
    'uuid': event uuid           
}