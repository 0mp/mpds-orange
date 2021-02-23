from kafka import KafkaConsumer
from kafka import KafkaProducer
from kafka_client.kafka_util import get_producer
import json, time
import datetime
from dateutil.parser import isoparse
import uuid
from threading import Thread, Lock
import torch
import numpy as np

class KafkaConsumerThread(Thread):
    
    def __init__(self, topic, ip, window_len = 1, arr_len = 10000):
        
        self.topic = topic
        self.ip = ip
        self.lock = Lock()
        self.window_len = window_len
        self.arr_len = arr_len
        self.hist = np.empty((2, arr_len), dtype=np.double)
        self.curr_hist1 = True
        self.hist2_ahead = int(arr_len / 2)
        self.pointer = 0
        self.window = np.empty((window_len), dtype=np.double)
        self.timestamp = None
        self.consumer = KafkaConsumer(topic,
                                 bootstrap_servers = ip,
                                 value_deserializer = lambda m: json.loads(m.decode('ascii')))
        Thread.__init__(self, daemon=True)
        
    def run(self):

        i_buff = 0
        
        for msg in self.consumer:
            
            # Average buffer
            self.window[i_buff] = msg.value["kafkaMessagesPerSecond"]
            
            i_buff += 1
            if i_buff == self.window_len:

                with self.lock:
                    self.hist[0,self.pointer] = self.hist[1,(self.pointer + self.hist2_ahead) % self.arr_len] = np.mean(self.window, 0)
                    self.timestamp = msg.value["occurredOn"]

                i_buff = 0
                self.pointer += 1
                #print(self.pointer)

                if self.pointer == self.arr_len:
                    self.pointer = 0
                    self.curr_hist1 = False
                elif self.pointer == self.hist2_ahead:
                    self.curr_hist1 = True
                    
    def get_current(self):
        if self.curr_hist1:
            return self.hist[0], self.pointer, self.timestamp
        else:
            return self.hist[1], (self.pointer + self.hist2_ahead) % self.arr_len, self.timestamp
        
    def filled_more_than(self, amount):
        if self.pointer >= amount:
            return True
        return False
    
class KafkaPredictionProducer():
    
    def __init__(self, topic, ip, interval):
        
        producer = get_producer(ip)
        self.topic = topic
        self.interval = interval
    
    def send_predictions(self, pred, time):
        t = isoparse(time)
        out = {"predictedWorkload" : [p.item() for p in pred],
               "predictionForDateTime" :
               [(t+datetime.timedelta(seconds=i*self.interval))
                .replace(tzinfo=datetime.timezone.utc).isoformat()
                for i in range(1,pred.shape[0]+1)],
               "predictionBasedOnDateTime" : time,
               "UUID" : str(uuid.uuid4()),
               "occurredOn" :
               datetime.datetime.utcnow()
               .replace(tzinfo=datetime.timezone.utc).isoformat(),
               "eventType" : "LongtermPredictionReported"}
        
        #print(out)
        producer.send(self.topic, out)