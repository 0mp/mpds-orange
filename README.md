# mpds-orange

## Overall system design

There are 2 Kafka topics used by the autoscaling system:

- `Metric` for message rate metrics
- `Prediction` for output results of the prediction models
