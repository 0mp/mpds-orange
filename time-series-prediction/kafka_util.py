from kafka import KafkaConsumer
from kafka import KafkaProducer
import json, time
from datetime import datetime


def get_msg(topic, ip, interval = 100):
    
    consumer = KafkaConsumer(topic,
                             bootstrap_servers = ip,
                             value_deserializer = lambda m: json.loads(m.decode('ascii')))
    
    for msg in consumer:
        
        yield msg
        
        
def get_producer(topic, ip, batch_size=1):
    
    return KafkaProducer(bootstrap_servers = ip,
                             value_serializer = lambda v: json.dumps(v).encode('utf-8'),
                             batch_size = 0
                            )
        
def sim_traffic(func, ip, topic="statistics", interval = 300, amount = 1000):
    
    producer = get_producer(topic, ip)
    
    for x in range(1, 1000):
        
        record = {"timestamp" : datetime.now().strftime('%Y-%m-%d %H:%M:%S'),
                  "load" : func(x)
                 }
        
        producer.send("statistics", record)
        
        time.sleep(interval/1000)