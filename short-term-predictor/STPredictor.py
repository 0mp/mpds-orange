#!/usr/bin/env python3

from kafka import KafkaConsumer
import logging
import json
import numpy as np
from datetime import datetime, timedelta


# Globalen Variablen
LOAD_TOPIC = 'load'
REBUILD_TIME_MIN = 5
REBUILD_TIME_SEC = REBUILD_TIME_MIN * 60
REGRESSION_WINDOW_FACTOR = 12
REGRESSION_WINDOW_MIN = REBUILD_TIME_MIN * REGRESSION_WINDOW_FACTOR
REGRESSION_WINDOW_SEC = REGRESSION_WINDOW_MIN * 60

OBSERVATION = []


def create_predictions():
    logging.info('Create Predictions')
    consumer =  KafkaConsumer(LOAD_TOPIC,
                         bootstrap_servers=['localhost:9092'],
                         auto_offset_reset='earliest', 
                         enable_auto_commit=False,
                         value_deserializer=lambda m: json.loads(m.decode('ascii')))
    
    count = 0
    for message in consumer:

        logging.info('Append Message')
        OBSERVATION.append(message.value)
        
        curr_time = datetime.strptime(message.value['timestamp'],'%Y-%m-%d %H:%M:%S')

        while abs((curr_time - datetime.strptime(OBSERVATION[0]['timestamp'],'%Y-%m-%d %H:%M:%S')).seconds) > REGRESSION_WINDOW_SEC :
                OBSERVATION.pop(0)

        if len(OBSERVATION) < 3:
            continue

        y = np.array([o['load'] for o in OBSERVATION])
        
        x = np.array([*range(len(OBSERVATION))])
        quadratic_regression = np.polyfit(x,y,2)
        linear_regression = np.polyfit(x,y,1)
        future_x = len(OBSERVATION) + len(OBSERVATION) / REGRESSION_WINDOW_FACTOR

        if quadratic_regression[0] > 0:
            
            prediction = quadratic_regression[0] * future_x**2 + \
                            quadratic_regression[1] * future_x + \
                            quadratic_regression[2]
            print(prediction)
            
        else:
            
            prediction = linear_regression[0] * future_x + \
                            linear_regression[1]
            print(prediction)


if __name__ == "__main__":
    logging.basicConfig(level=logging.INFO)
    create_predictions()