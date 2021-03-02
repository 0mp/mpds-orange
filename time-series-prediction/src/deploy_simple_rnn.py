from models.simple_models import CNN1D_1l_RNN
from train_loops import pre_train_lambda
from kafka_client.kafka_classes import KafkaConsumerThread, KafkaPredictionProducer
from kafka_client.kafka_util import sim_traffic
from train_loops import forward_walk_train as train

import numpy as np
import torch
import torch.optim as optim
import torch.nn as nn
import time
from threading import Thread
from csaps import csaps
import hydra
from omegaconf import OmegaConf

def set_up_train():
    iters = 0
    
def wait_for_fill(kafka, train_buff = 100):
    print("wait for data to build up")
    while(not kafka.filled_more_than(SEQ_LEN + PRED_LEN + CFG.train.buffer)):
        time.sleep(0.5)

def wait_for_new(kafka, old):
    while(kafka.pointer == old):
        time.sleep(0.2)
        
def find_max_train_iter(kafka, model):
    print("find max training iterations")
    train_iter = kafka.pointer - (SEQ_LEN + PRED_LEN) + 1

    with kafka.lock:
        hist, i, _, _ = kafka.get_current()
        opt = optim.AdamW(model.parameters(), lr=CFG.train.LR)
        crit = nn.MSELoss()
        total_dur = 0
        tensor_in, _, _ = normalize_input(hist[i-(train_iter+SEQ_LEN+PRED_LEN - 1):i])
        for _ in range(3):
            dur, _ = train(model,
                           tensor_in,
                           opt,
                           crit,
                           SEQ_LEN,
                           PRED_LEN)
            total_dur += dur
    
    return int(CFG.train.time * 1000 * train_iter * 3 / total_dur)

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
    
    opt = optim.AdamW(model.parameters(), lr=CFG.train.LR)
    crit = nn.MSELoss()
    producer = KafkaPredictionProducer(CFG.kafka.out_topic,
                                       CFG.kafka.ip,
                                       CFG.kafka.input_interval)
    
    while(True):
        
        with kafka.lock:
            hist, i, time, msg_uuid = kafka.get_current()
            
            if i < train_iter + SEQ_LEN + PRED_LEN:
                tensor_in, _max, _min = normalize_input(hist[0:i])
                
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
        
        producer.send_predictions(spline_smooth, time, msg_uuid)
        wait_for_new(kafka, i)
    

@hydra.main(config_path="../", config_name="simple_model_config.yaml")
def main(cfg : OmegaConf):
    
    print(cfg)
    global CFG, SEQ_LEN, PRED_LEN, SIGNAL
    CFG = cfg
    SEQ_LEN = cfg.model.seq_len
    PRED_LEN = cfg.model.pred_len
    SIGNAL = lambda t: (np.sin(t * 2 * np.pi / CFG.kafka.expected_period) + 1) / 2
    
    if CFG.kafka.self_simulate:
        Thread(target=sim_traffic,
               args=(SIGNAL,
                     CFG.kafka.ip,
                     CFG.kafka.in_topic,
                     CFG.kafka.input_interval*1000, ),
               daemon=True).start()
    
    kafka_thread = KafkaConsumerThread(CFG.kafka.in_topic,
                                       CFG.kafka.ip,
                                       CFG.kafka.avg_agg_len)
    kafka_thread.start()
          
    model = CNN1D_1l_RNN(CFG.model.hidden_dim,
                         SEQ_LEN,
                         stacked = CFG.model.stack,
                         future = PRED_LEN,
                         cell_type = CFG.model.cell)
    
    # TODO: check period
    pre_train_lambda(model,
                     SIGNAL,
                     future = PRED_LEN,
                     seq_len = SEQ_LEN,
                     tick_size = 2*np.pi/CFG.kafka.expected_period,
                     max_t = 10000,
                     epochs = CFG.train.pretrain_iters,
                     batch_size = 128,
                     lr=cfg.train.LR,
                     dev = torch.device("cpu"))
          
    wait_for_fill(kafka_thread)
    train_iter = find_max_train_iter(kafka_thread, model)
    run(model, train_iter, kafka_thread)
    
            

if __name__=="__main__":
    main()