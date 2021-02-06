# mpds-orange

## Overall system design

### Autoscaling system

There are 2 Kafka topics used by the autoscaling system:

- `Metric` for message rate metrics
- `Prediction` for output results of the prediction models

#### Prediction models

There are two prediction models available at the moment: a long-term one, and a short-term one. Both models are predicting the load based on the Kafka message rates.
