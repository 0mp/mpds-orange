from kafka import KafkaConsumer
from kafka import KafkaProducer
from kafka_client.kafka_util import get_producer
import json, time
import datetime
from dateutil.parser import isoparse
import uuid
from threading import Thread, Lock
import numpy as np
import os
import csv

class KafkaConsumerThread(Thread):
    
    def __init__(self, topic, ip, window_len = 1, arr_len = 10000, path=None):
        
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
        self.msg_uuid = None
        self.consumer = KafkaConsumer(topic,
                                      bootstrap_servers = ip,
                                      value_deserializer = lambda m: json.loads(m.decode('ascii')))
        Thread.__init__(self, daemon=True)
        
        if path is None:
            self.save_hist = False
        else:
            self.save_hist = True
            self.path = path
            os.makedirs(os.path.split(path)[0], exist_ok=True)
            
            with open(self.path, 'w', newline='') as csv_file:
                writer = csv.writer(csv_file, delimiter=',')
                writer.writerow(["dateTime",
                                 "eventTriggerUuid",
                                 "kafkaMessagesPerSecond"])
        
    def run(self):
        print("running subscriber thread")
        i_buff = 0
        
        for msg in self.consumer:
            # Average buffer
            self.window[i_buff] = msg.value["kafkaMessagesPerSecond"]
            
            i_buff += 1
            if i_buff == self.window_len:

                with self.lock:
                    self.hist[0,self.pointer] = self.hist[1,(self.pointer + self.hist2_ahead) % self.arr_len] = np.mean(self.window, 0)
                    self.timestamp = msg.value["occurredOn"]
                    self.msg_uuid = msg.value["uuid"]

                i_buff = 0
                self.pointer += 1
                #print(self.pointer)

                if self.pointer == self.arr_len:
                    self.pointer = 0
                    self.curr_hist1 = False
                elif self.pointer == self.hist2_ahead:
                    self.curr_hist1 = True
                    
            if self.save_hist:
                with open(self.path, 'a', newline='') as csv_file:
                    writer = csv.writer(csv_file, delimiter=',')
                    writer.writerow([self.timestamp,
                                     self.msg_uuid,
                                     self.get_last_value().item()])
                    
    def get_current(self):
        if self.curr_hist1:
            return self.hist[0], self.pointer, self.timestamp, self.msg_uuid
        else:
            return self.hist[1], (self.pointer + self.hist2_ahead) % self.arr_len, self.timestamp
        
    def get_last_value(self):
        if self.curr_hist1:
            return self.hist[0][self.pointer-1]
        else:
            return self.hist[1][(self.pointer + self.hist2_ahead - 1) % self.arr_len]
        
    def filled_more_than(self, amount):
        if self.pointer >= amount:
            return True
        return False
    
class KafkaPredictionProducer():
    
    def __init__(self, topic, ip, interval, path=None, pred_len=8):
        
        self.producer = get_producer(ip)
        self.topic = topic
        self.interval = interval
        if path is None:
            self.save_pred = False
        else:
            self.save_pred = True
            self.path = path
            self.pred_len = True
            os.makedirs(os.path.split(path)[0], exist_ok=True)
            
            with open(self.path, 'w', newline='') as csv_file:
                writer = csv.writer(csv_file, delimiter=',')
                writer.writerow(["predictionBasedOnDateTime",
                                 "eventTriggerUuid",
                                 "occurredOn",
                                 "interval"] + [f"prediction{i}"
                                                for i in range(pred_len)])
                                 
    
    def send_predictions(self, pred, time, msg_uuid):
        t = isoparse(time)
        now = datetime.datetime.utcnow().strftime('%Y-%m-%dT%H:%M:%SZ')
        out = {"predictedWorkloads" : [{"value" : p.item(),
                                        "dateTime": (t+datetime.timedelta(seconds=(i+1)*self.interval))
                                        .strftime('%Y-%m-%dT%H:%M:%SZ')}
                                       for i, p in enumerate(pred)],
               "predictionBasedOnDateTime" : time,
               "eventTriggerUuid" : msg_uuid,
               "uuid" : str(uuid.uuid4()),
               "occurredOn" : now,
               "eventType" : "LongtermPredictionReported"}
        
        #print(out)
        self.producer.send(self.topic, out)
        
        if self.save_pred:
            with open(self.path, 'a', newline='') as csv_file:
                writer = csv.writer(csv_file, delimiter=',')
                writer.writerow([time,
                                 msg_uuid,
                                 now,
                                 self.interval] + list(pred))
