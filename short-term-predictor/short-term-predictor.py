#!/usr/bin/env python3

from kafka import KafkaConsumer, KafkaProducer
import logging
import json
import numpy as np
from datetime import datetime, timedelta


# Globalen Variablen
LOAD_TOPIC = 'metric'
PREDICTION_TOPIC = 'st_prediction'
REBUILD_TIME_MIN = 5
REBUILD_TIME_SEC = REBUILD_TIME_MIN * 60
REGRESSION_WINDOW_FACTOR = 12
REGRESSION_WINDOW_MIN = REBUILD_TIME_MIN * REGRESSION_WINDOW_FACTOR
REGRESSION_WINDOW_SEC = REGRESSION_WINDOW_MIN * 60
SPIKE_WINDOW_MIN = 3 #minutes


def create_predictions():
    logging.info('Create Predictions')
    consumer =  KafkaConsumer(LOAD_TOPIC,
                         bootstrap_servers=['localhost:9092'],
                         auto_offset_reset='earliest', 
                         enable_auto_commit=False,
                         value_deserializer=lambda m: json.loads(m.decode('ascii')),
                         consumer_timeout_ms=30000)
    producer = KafkaProducer(bootstrap_servers=['localhost:9092'],
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
            if np.square(curr_prediction - message.value['kafkaMessagesPerSeconds']) > 8 * MSE:
                spike_data.append(message.value)
                if len(spike_data) > 3:
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
        while abs((curr_time - datetime.strptime(OBSERVATION[0]['occurredOn'],'%Y-%m-%dT%H:%M:%SZ')).seconds) > REGRESSION_WINDOW_SEC :
                OBSERVATION.pop(0)

        
        # Calculate Regression
        y = np.array([o['kafkaMessagesPerSeconds'] for o in OBSERVATION])
        x = np.array([*range(len(OBSERVATION))])
        quadratic_regression = np.polyfit(x,y,2)
        linear_regression = np.polyfit(x,y,1)

        # Calculate future x for prediction
        future_x = len(OBSERVATION) + REBUILD_TIME_MIN

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
            {'prediction':prediction,
            'occurredOn': nextTime.strftime('%Y-%m-%dT%H:%M:%SZ')})
            



if __name__ == "__main__":
    logging.basicConfig(level=logging.INFO)
    create_predictions()
