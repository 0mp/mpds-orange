from models.lmu import get_lmu_model
from kafka_client.kafka_classes import KafkaConsumerThread, KafkaPredictionProducer
from kafka_client.kafka_util import sim_traffic
from train_tf_keras import pre_train_lambda
from train_tf_keras import forward_walk_train as train
from util.plotting import LivePlotPrediction, LiveGraph

import numpy as np
import time
from threading import Thread
from csaps import csaps
import hydra
from hydra.experimental import compose, initialize
from omegaconf import OmegaConf
import datetime

import tensorflow as tf
from tensorflow import keras

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
        opt  = keras.optimizers.Adam(learning_rate=CFG.train.LR)
        crit = keras.losses.MSE
        total_dur = 0
        tensor_in, _, _ = normalize_input(hist[i-(train_iter+SEQ_LEN+PRED_LEN - 1):i])
        for _ in range(3):
            dur, _ = train(model,
                           tensor_in,
                           SEQ_LEN,
                           PRED_LEN,
                           opt,
                           crit)
            total_dur += dur
    
    return int(CFG.train.time * 1000 * train_iter * 3 / total_dur)

def normalize_input(arr):
    
    smooth = csaps(range(arr.shape[0]),
                     arr,
                     range(arr.shape[0]),
                     smooth=0.8)

    _max = np.max(smooth)
    _min = np.min(smooth)
    
    if _max - _min != 0:
        normalized = (smooth - _min) / (_max - _min)
    else:
        normalized = (smooth - _min)
        
    return tf.convert_to_tensor(normalized)[None, :, None], _max, _min

def run(model, train_iter, kafka_consumer, kafka_producer, live_plot=None):
    
    opt = keras.optimizers.Adam(learning_rate=CFG.train.LR)
    crit = keras.losses.MSE
    
    while(True):
        
        with kafka_consumer.lock:
            hist, i, time, msg_uuid = kafka_consumer.get_current()
            
            if i < train_iter + SEQ_LEN + PRED_LEN:
                tensor_in, _max, _min = normalize_input(hist[0:i])
                
            else:
                tensor_in, _max, _min = normalize_input(
                    hist[i-(train_iter+SEQ_LEN+PRED_LEN):i]
                )

            train(model,
                  tensor_in,
                  SEQ_LEN,
                  PRED_LEN,
                  opt,
                  crit)
            
            in_len = tensor_in.shape[0]
            for j in range(PRED_LEN):
                model(tensor_in[:,-(SEQ_LEN+PRED_LEN-j):-(PRED_LEN-j),:])
                
            if _max - _min != 0:
                pred = model(tensor_in[:,-SEQ_LEN:,:])[0] * (_max - _min) + _min
            else:
                pred = model(tensor_in[:,-SEQ_LEN:,:])[0] + _min
        
        spline_smooth = csaps(range(SEQ_LEN, SEQ_LEN + PRED_LEN),
                                          pred,
                                          range(SEQ_LEN, SEQ_LEN + PRED_LEN),
                                          smooth=0.8)
        
        kafka_producer.send_predictions(spline_smooth, time, msg_uuid)
        
        if live_plot is not None:
            live_plot.update(hist[i-SEQ_LEN:i], spline_smooth)
            
        wait_for_new(kafka_consumer, i)
    

#@hydra.main(config_path="../", config_name="lmu_config.yaml")
def main(live_plotting=False):
    
    initialize(config_path="../")
    cfg = compose(config_name="lmu_config.yaml")
    print(cfg)
    global CFG, SEQ_LEN, PRED_LEN, SIGNAL
    CFG = cfg
    SEQ_LEN = cfg.model.seq_len
    PRED_LEN = cfg.model.pred_len
    SIGNAL = lambda t: (np.sin(t * 2 * np.pi / CFG.kafka.expected_period) + 1) / 2
    
    if CFG.kafka.save_data:
        timestamp =  datetime.datetime.now().strftime("%Y%m%d_%H-%M-%S")
        hist_path = f"./history/load_data/{timestamp}.csv"
        pred_path = f"./history/pred_data/{timestamp}.csv"
    else:
        hist_path = None
        pred_path = None
    
    if CFG.kafka.self_simulate:
        Thread(target=sim_traffic,
               args=(SIGNAL,
                     CFG.kafka.ip,
                     CFG.kafka.in_topic,
                     CFG.kafka.input_interval*1000, ),
               daemon=True).start()
    
    kafka_thread = KafkaConsumerThread(CFG.kafka.in_topic,
                                       CFG.kafka.ip,
                                       CFG.kafka.avg_agg_len,
                                       path = hist_path)
    kafka_thread.start()
    
    kafka_producer = KafkaPredictionProducer(CFG.kafka.out_topic,
                                       CFG.kafka.ip,
                                       CFG.kafka.input_interval,
                                       path = pred_path)
          
    model = get_lmu_model(SEQ_LEN,
                          PRED_LEN,
                          memory_dim = CFG.model.memory_dim,
                          order = CFG.model.order,
                          hidden_dim = CFG.model.hidden_dim)
    
    # TODO: check period
    pre_train_lambda(model,
                     SIGNAL,
                     future = PRED_LEN,
                     seq_len = SEQ_LEN,
                     tick_size = 2*np.pi/CFG.kafka.expected_period,
                     max_t = 10000,
                     epochs = CFG.train.pretrain_iters,
                     batch_size = 128,
                     lr=cfg.train.LR)
          
    wait_for_fill(kafka_thread)
    train_iter = find_max_train_iter(kafka_thread, model)
    print(f"forward walking training steps per round: {train_iter}")
    
    if live_plotting:
        with LiveGraph(backend='nbAgg') as h:
            live_plot_prediction = LivePlotPrediction(h, SEQ_LEN, PRED_LEN)
            run(model, train_iter, kafka_thread, kafka_producer, live_plot_prediction)
    else:
        run(model, train_iter, kafka_thread, kafka_producer)
    
if __name__=="__main__":
    main()
