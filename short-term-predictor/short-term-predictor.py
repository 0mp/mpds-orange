#!/usr/bin/env python3

from kafka import KafkaConsumer, KafkaProducer
import logging
import json
import numpy as np
from datetime import datetime, timedelta
import uuid

"""
Short Term Predictor

Regression over configurable amount of previous time, in order to extrapolate for a section of time in the future
1. Do Quadratic Regression
2. Check if largest coefficient is Positive, Publish Quadratic Regression Extrapolation
3. If Negative, Publish Linear Regression Extrapolation

Spike Detection
1. Check if New data point is beyond x * MSE of linear regression of current data set
2. If Yes, do not add to regression data set, add to Spike set
2.1 If spike set length is > 3, reset regression data set to spike set, clear spike set
3. If No, Continue as usual

Incoming Data, 'metric' topic kafka example:
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
"""


# Globalen Variablen
LOAD_TOPIC = 'metric'
PREDICTION_TOPIC = 'short-term-predictions'
REBUILD_TIME_MIN = 2
REBUILD_TIME_SEC = REBUILD_TIME_MIN * 60
REGRESSION_WINDOW_FACTOR = 12
REGRESSION_WINDOW_MIN = REBUILD_TIME_MIN * REGRESSION_WINDOW_FACTOR
REGRESSION_WINDOW_SEC = REGRESSION_WINDOW_MIN * 60
SPIKE_WINDOW_MIN = 1 #minutes


def create_predictions():
    logging.info('Create Predictions')
    consumer =  KafkaConsumer(LOAD_TOPIC,
                         bootstrap_servers=['35.246.173.215:31090'],
                         auto_offset_reset='earliest', 
                         enable_auto_commit=False,
                         value_deserializer=lambda m: json.loads(m.decode('ascii')),
                         consumer_timeout_ms=60000)
    producer = KafkaProducer(bootstrap_servers=['35.246.173.215:31090'],
                        value_serializer=lambda v: json.dumps(v).encode('utf-8'))

    OBSERVATION = []
    spike_data = []
    quadratic_regression = []
    MSE = 0
    
    for message in consumer:

        ## Check if <3x MSE of Quadratic Regression
        if len(OBSERVATION) > 3:
            curr_prediction = linear_regression[0] * (len(OBSERVATION)) + \
                                linear_regression[1]
            if np.square(curr_prediction - message.value['kafkaMessagesPerSecond']) > 8 * MSE:
                spike_data.append(message.value)
                if len(spike_data) > 6:
                    logging.warning('Spike Detected')
                    OBSERVATION = spike_data
                    spike_data = []
                else:
                    continue
            else:
                spike_data = []                
        

        # Add new data to observation
        logging.info('Append Message')
        OBSERVATION.append(message.value)
        if len(OBSERVATION) < 3:
            continue



        # Truncate Observations based on time window
        curr_time = datetime.strptime(message.value['occurredOn'],'%Y-%m-%dT%H:%M:%SZ')
        while abs((curr_time - datetime.strptime(OBSERVATION[0]['occurredOn'],'%Y-%m-%dT%H:%M:%SZ')).seconds) > REGRESSION_WINDOW_SEC and len(OBSERVATION) > 3:
                logging.info("Pop observation, too old")
                OBSERVATION.pop(0)

        
        # Calculate Regression
        y = np.array([o['kafkaMessagesPerSecond'] for o in OBSERVATION])
        
        x = np.array([*range(len(OBSERVATION))])
        quadratic_regression = np.polyfit(x,y,2)
        linear_regression = np.polyfit(x,y,1)

        # Calculate future x for prediction
        future_x = len(OBSERVATION) + REBUILD_TIME_MIN * 6

        # Calculate MSE for spike detection
        MSE = np.square(np.subtract(y,np.polyval(linear_regression,x))).mean() 

        # Calculate prediction based on Curve of data
        prediction = 0
        if quadratic_regression[0] > 0:
            prediction = quadratic_regression[0] * future_x**2 + \
                            quadratic_regression[1] * future_x + \
                            quadratic_regression[2]
        else:
            prediction = linear_regression[0] * future_x + \
                            linear_regression[1]

        last_ob_time = datetime.strptime(OBSERVATION[-1]['occurredOn'],'%Y-%m-%dT%H:%M:%SZ')
        nextTime = last_ob_time + timedelta(minutes = REBUILD_TIME_MIN)
        producer.send(PREDICTION_TOPIC,
            {'predictedWorkload':prediction,
            'occurredOn': last_ob_time.strftime('%Y-%m-%dT%H:%M:%SZ'),
            "eventTriggerUuid":OBSERVATION[-1]['uuid'],
            "eventType":"ShorttermPredictionReported",
            "predictionBasedOnDateTime":nextTime.strftime('%Y-%m-%dT%H:%M:%SZ'),
            'uuid': str(uuid.uuid4())})
            



if __name__ == "__main__":
    logging.basicConfig(level=logging.INFO)
    create_predictions()
