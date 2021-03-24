from kafka import KafkaConsumer
from kafka import KafkaProducer
import json, time
import datetime
from dateutil.parser import isoparse
import uuid
from threading import Thread, Lock
import numpy as np
import uuid


def get_msg(topic, ip, interval = 100):
    
    consumer = KafkaConsumer(topic,
                             bootstrap_servers = ip,
                             value_deserializer = lambda m: json.loads(m.decode('ascii')))
    
    for msg in consumer:
        yield msg
        
        
def get_producer(ip, batch_size=1):
    
    return KafkaProducer(bootstrap_servers = ip,
                             value_serializer = lambda v: json.dumps(v).encode('utf-8'),
                             batch_size = 0
                            )
        
def sim_traffic(func, ip, topic="metric", interval = 200, amount = 1000):
    
    producer = get_producer(ip)
    
    for x in range(1, 1000):
        
        record = {"occurredOn" : datetime.datetime.utcnow().strftime('%Y-%m-%dT%H:%M:%SZ'),
                  "kafkaMessagesPerSecond" : func(x),
                  "uuid" : str(uuid.uuid4())
                 }
        #print(f"send {x}")
        producer.send(topic, record)
        
        time.sleep(interval/1000)
