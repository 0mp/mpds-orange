from parser import get_GRU_parser
from models import CNN1D_1l_RNN
from train import pre_train_lambda
from kafka_util import KafkaConsumerThread, sim_traffic, KafkaPredictionProducer
from train import forward_walk_train as train

import numpy as np
import torch
import torch.optim as optim
import torch.nn as nn
import time
from threading import Thread
from csaps import csaps

args = get_GRU_parser().parse_args()
KAFKA_IP = args.kafka_ip
SEQ_LEN = args.sequence_length
PRED_LEN = args.prediction_length
INTERVAL = args.input_interval
AVG_LEN = args.average_length
TRAIN_TIME = args.train_time
if TRAIN_TIME is None:
    TRAIN_TIME = INTERVAL * AVG_LEN - 3
IN_TOPIC = args.metrics_topic
OUT_TOPIC = args.output_topic
PERIOD = args.expected_period
CELL = args.rnn_cell
if PERIOD is None:
    PERIOD = int(SEQ_LEN / 2)
SIM = args.self_simulate

TRAIN_BUFF = 10
PRE_TRAIN_EPOCHS = 100
HIDDEN_DIM = 16
STACK = 3
LR = 0.001
SIGNAL = lambda t: (np.sin(t * 2 * np.pi / PERIOD) + 1) / 2

def set_up_train():
    iters = 0
    
def wait_for_fill(kafka, train_buff = TRAIN_BUFF):
    while(not kafka.filled_more_than(SEQ_LEN + PRED_LEN + train_buff)):
        time.sleep(0.5)

def wait_for_new(kafka, old):
    while(kafka.pointer == old):
        time.sleep(0.2)
        
def find_max_train_iter(kafka, model):

    train_iter = kafka.pointer - (SEQ_LEN + PRED_LEN)

    with kafka.lock:
        hist, i, _ = kafka.get_current()
        opt = optim.AdamW(model.parameters())
        crit = nn.MSELoss()
        total_dur = 0
        tensor_in, _, _ = normalize_input(hist[i-(train_iter+SEQ_LEN+PRED_LEN):i])
        for _ in range(3):
            dur, _ = train(model,
                           tensor_in,
                           opt,
                           crit,
                           SEQ_LEN,
                           PRED_LEN)
            total_dur += dur
    
    return int(TRAIN_TIME * 1000 * train_iter * 3 / total_dur)

def normalize_input(arr):
    
    smooth = csaps(range(arr.shape[0]),
                     arr,
                     range(arr.shape[0]),
                     smooth=0.8)

    _max = np.max(smooth)
    _min = np.min(smooth)
    normalized = (smooth - _min) / (_max - _min)
    return torch.from_numpy(normalized)[None, None,:], _max, _min

def run(model, train_iter, kafka):
    
    opt = optim.AdamW(model.parameters())
    crit = nn.MSELoss()
    producer = KafkaPredictionProducer(OUT_TOPIC, KAFKA_IP, INTERVAL)
    
    while(True):
        with kafka.lock:
            hist, i, time = kafka.get_current()
            if i < train_iter+SEQ_LEN+PRED_LEN:
                tensor_in, _max, _min = normalize_input(hist[0:i])
                train(model,
                      tensor_in,
                      opt,
                      crit,
                      SEQ_LEN,
                      PRED_LEN)
            else:
                tensor_in, _max, _min = normalize_input(
                    hist[i-(train_iter+SEQ_LEN+PRED_LEN):i]
                )
                train(model,
                      tensor_in,
                      opt,
                      crit,
                      SEQ_LEN,
                      PRED_LEN)
        
            model.eval()
            in_len = tensor_in.shape[0]
            with torch.no_grad():
                for j in range(PRED_LEN-1):
                    model(tensor_in[in_len-(SEQ_LEN+PRED_LEN-j):in_len])
                pred = model(tensor_in[in_len-SEQ_LEN:in_len])[0] * (_max - _min) + _min
        
        spline_smooth = csaps(range(SEQ_LEN, SEQ_LEN + PRED_LEN),
                                          pred,
                                          range(SEQ_LEN, SEQ_LEN + PRED_LEN),
                                          smooth=0.8)
        
        producer.send_predictions(spline_smooth, time)
        wait_for_new(kafka, i)
    

def main():
    
    kafka_thread = KafkaConsumerThread(IN_TOPIC, KAFKA_IP, AVG_LEN)
    kafka_thread.start()
          
    model = CNN1D_1l_RNN(HIDDEN_DIM,
                         SEQ_LEN,
                         stacked=STACK,
                         future = PRED_LEN,
                         cell_type=CELL)
    
    pre_train_lambda(model,
                     SIGNAL,
                     future = PRED_LEN,
                     seq_len = SEQ_LEN,
                     tick_size = 2*np.pi/PERIOD,
                     max_t = 10000,
                     epochs = PRE_TRAIN_EPOCHS,
                     batch_size = 128,
                     lr=LR,
                     dev = torch.device("cpu"))
          
    wait_for_fill(kafka_thread)
    train_iter = find_max_train_iter(kafka_thread, model)
    run(model, train_iter, kafka_thread)
    
            

if __name__=="__main__":
    if SIM:
        Thread(target=sim_traffic, args=(SIGNAL, KAFKA_IP, IN_TOPIC, INTERVAL*1000, ), daemon=True).start()
    main()