#!/usr/bin/env python3

from kafka import KafkaConsumer
import logging
import json
import numpy as np
from datetime import datetime, timedelta
import matplotlib.pyplot as plt 

# Globalen Variablen
LOAD_TOPIC = 'load'
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
    

    OBSERVATION = []
    pyplot_prediction = []
    pyplot_observation = []

    spike_data = []
    quadratic_regression = []
    MSE = 0
    
    for message in consumer:

        ## Check if <3x MSE of Quadratic Regression
        if len(OBSERVATION) > 3:
            curr_prediction = linear_regression[0] * (len(OBSERVATION)) + \
                                linear_regression[1]
            if np.square(curr_prediction - message.value['load']) > 8 * MSE:
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
        pyplot_observation.append(message.value['load'])
        if len(OBSERVATION) < 3:
            continue



        # Truncate Observations based on time window
        curr_time = datetime.strptime(message.value['timestamp'],'%Y-%m-%d %H:%M:%S')
        while abs((curr_time - datetime.strptime(OBSERVATION[0]['timestamp'],'%Y-%m-%d %H:%M:%S')).seconds) > REGRESSION_WINDOW_SEC :
                OBSERVATION.pop(0)

        
        # Calculate Regression
        y = np.array([o['load'] for o in OBSERVATION])
        x = np.array([*range(len(OBSERVATION))])
        quadratic_regression = np.polyfit(x,y,2)
        linear_regression = np.polyfit(x,y,1)

        # Calculate future x for prediction
        future_x = len(OBSERVATION) + REBUILD_TIME_MIN

        # Calculate MSE for spike detection
        MSE = np.square(np.subtract(y,np.polyval(linear_regression,x))).mean() 

        # Calculate prediction based on Curve of data
        if quadratic_regression[0] > 0:
            prediction = quadratic_regression[0] * future_x**2 + \
                            quadratic_regression[1] * future_x + \
                            quadratic_regression[2]
            pyplot_prediction.append(prediction)
            
        else:
            prediction = linear_regression[0] * future_x + \
                            linear_regression[1]
            pyplot_prediction.append(prediction)
            



    ### PYPLOT PRINTING 
    print(len(pyplot_prediction))
    print(len(pyplot_observation))

    x = [*range(len(pyplot_observation))]
    x_pred = [i+8 for i in x[2:]]
    pyplot_prediction_norm = []
    for i in pyplot_prediction:
        if abs(i) > 3:
            if i < 0: 
                pyplot_prediction_norm.append(i % -3)
            else:
                pyplot_prediction_norm.append(i % 3)
        else:
            pyplot_prediction_norm.append(i)
    
    plt.plot(x, pyplot_observation, label='Load Data') 
    plt.plot(x_pred, pyplot_prediction_norm, label='Prediction')
    plt.title('Short Term Predictor, Spike Detection')
    plt.xlabel('Time (minutes)') 
    plt.ylabel('Message Load') 
    plt.legend(loc="upper right")
    plt.show() 



if __name__ == "__main__":
    logging.basicConfig(level=logging.INFO)
    create_predictions()
