from models.simple_models import CNN1D_1l_RNN
from models.lmu import get_lmu_model
import train_loops
import train_tf_keras

import torch
import torch.nn as nn
import torch.optim as optim
import numpy as np
from tqdm.notebook import tqdm
import tensorflow as tf
from tensorflow import keras
import datetime
import os
import pandas as pd
from concurrent.futures import ThreadPoolExecutor
import threading

def make_pf_from_dict(dic):
    
    df = pd.DataFrame.from_dict({k : v for k, v in dic.items() if isinstance(v, list)})
    
    for k, v in dic.items():
        
        if not isinstance(v, list):
            df[k] = v
    
    return df
 
def run_gru(gru_model, optimizer, param_dict, torch_ts, start, end, train_buff, path):
    
    torch_loss = nn.MSELoss()

    for i in range(start, end):
        duration, loss = train_loops.forward_walk_train(gru_model,
                                                        torch_ts[:,:,(i+1)-(param_dict["seq_len"]
                                                                            + param_dict["pred_len"]
                                                                            + train_buff):i+1],
                                                        optimizer,
                                                        torch_loss,
                                                        param_dict["seq_len"],
                                                        param_dict["pred_len"])
                    
        with torch.no_grad():
            gru_model.eval()
            ts_in = torch_ts[:,:,(i+1)-param_dict["seq_len"]:i+1]
            to_pred = torch_ts[:,:,i+1:i+1+param_dict["pred_len"]]
            pred_loss = torch_loss(gru_model(ts_in), to_pred[:,0,:]).item()
                        
        param_dict["train_dur"].append(duration)
        param_dict["train_loss"].append(loss)
        param_dict["pred_loss"].append(pred_loss)

    df = make_pf_from_dict(param_dict)
    df.to_csv(path, index = False)
    
    return 
    
def run_lmu(lmu_model, optimizer, param_dict, tf_ts, start, end, train_buff, path):
    
    tf_loss = keras.losses.MSE
    
    for i in range(start, end):
        duration, loss = train_tf_keras.forward_walk_train(lmu_model,
                                                           tf_ts[:,(i+1)-(param_dict["seq_len"]
                                                                          + param_dict["pred_len"]
                                                                          + train_buff):i+1,:],
                                                           param_dict["seq_len"],
                                                           param_dict["pred_len"],
                                                           optimizer,
                                                           tf_loss,
                                                           )
        
        ts_in = tf_ts[:,(i+1)-param_dict["seq_len"]:i+1,:]
        to_pred = tf_ts[:,i+1:i+1+param_dict["pred_len"],0]
        pred_loss = tf_loss(to_pred, lmu_model(ts_in)).numpy().item()
        param_dict["train_dur"].append(duration)
        param_dict["train_loss"].append(loss.numpy().item())
        param_dict["pred_loss"].append(pred_loss)
        
    df = make_pf_from_dict(param_dict)
    df.to_csv(path, index = False)


def lmu_vs_gru(signal, lrs = [0.001, 0.003], gru_stacks = [3,5], hidden_dims = [16, 32], seq_lens = [128, 256],  pred_lens = [16, 64], lmu_mem_dims = [1, 2], lmu_orders = [32, 64], train_buff = 100, iterations = 250, threads = 16):
       
    gru_models = []
    lmu_models = [] 
    
    print("preparing models")
    for lr in lrs:
        for seq_len in seq_lens:
            for pred_len in pred_lens:
                for hidden_dim in hidden_dims:
                    
                    for stack in gru_stacks:
                        model = CNN1D_1l_RNN(hidden_dim, seq_len, stacked=stack, future = pred_len, cell_type="GRU").double()
                        gru_models.append((model,
                                          optim.AdamW(model.parameters(), lr=lr),
                                          {"name" : "GRU",
                                           "lr" : lr,
                                           "seq_len" : seq_len,
                                           "pred_len" : pred_len, 
                                           "hidden_dim" : hidden_dim,
                                           "stack" : stack,
                                           "train_dur" : [],
                                           "train_loss" : [],
                                           "pred_loss" : []}))
                        
                    for mem_dim in lmu_mem_dims:
                        for order in lmu_orders:
                            lmu_models.append((get_lmu_model(seq_len, pred_len, mem_dim, order, hidden_dim),
                                               keras.optimizers.Adam(learning_rate=lr),
                                               {"name" : "GRU",
                                                "lr" : lr,
                                                "seq_len" : seq_len,
                                                "pred_len" : pred_len, 
                                                "hidden_dim" : hidden_dim,
                                                "memory_dim" : mem_dim,
                                                "order" : order,
                                                "train_dur" : [],
                                                "train_loss" : [],
                                                "pred_loss" : []}))
    
    
    print(f"number of GRU models: {len(gru_models)}")
    print(f"number of LMU models: {len(lmu_models)}")
    start = max(seq_lens) + max(pred_lens) + train_buff
    end = iterations + start
    
    time_series = signal(np.arange(end + max(pred_lens)))[None,:,None]
    
    torch_ts = torch.from_numpy(time_series).permute(0,2,1)
    tf_ts = tf.convert_to_tensor(time_series)
                                 
    timestamp =  datetime.datetime.now().strftime("%Y%m%d_%H-%M-%S")
    gru_path = f"./model-comparison-stats/gru/{timestamp}"
    lmu_path = f"./model-comparison-stats/lmu/{timestamp}"
    os.makedirs(gru_path, exist_ok=True)
    os.makedirs(lmu_path, exist_ok=True)
    
    with ThreadPoolExecutor(threads) as executor:
        futures = []
        for gru_model, optimizer, param_dict in gru_models:
            name = f"lr_{param_dict['lr']}-seq_len_{param_dict['seq_len']}-pred_len_{param_dict['pred_len']}-hidden_dim_{param_dict['hidden_dim']}-stack_{param_dict['stack']}"
                
                #run_gru(gru_model, optimizer, param_dict, torch_ts.clone(), start, end, train_buff, os.path.join(gru_path, name))
            futures.append((executor.submit(run_gru, gru_model, optimizer, param_dict, torch_ts.clone(), start, end, train_buff, os.path.join(gru_path, name)), name))
            
        with tqdm(futures) as pbar:
            for f, name in pbar:
                pbar.set_postfix(model=name)
                f.result()
    
    with ThreadPoolExecutor(threads) as executor:
        futures = []
        
        for lmu_model, optimizer, param_dict in lmu_models:
            
            name = f"lr_{param_dict['lr']}-seq_len_{param_dict['seq_len']}-pred_len_{param_dict['pred_len']}-hidden_dim_{param_dict['hidden_dim']}-memory_dim_{param_dict['memory_dim']}-order_{param_dict['order']}"
            
            #run_lmu(lmu_model, optimizer, param_dict, tf.identity(tf_ts), start, end, train_buff, os.path.join(lmu_path, name))
            futures.append((executor.submit(run_lmu, lmu_model, optimizer, param_dict, tf.identity(tf_ts), start, end, train_buff, os.path.join(lmu_path, name)), name))
            
        with tqdm(futures) as pbar:
            for f, name in pbar:
                pbar.set_postfix(model=name)
                f.result()
                
                
if __name__=="__main__":
    signal1 = lambda t: np.sin(t/4) + np.sin(t/2) + np.random.normal(loc=0.0, scale=0.2, size=t.shape)
    lmu_vs_gru(signal1)
    signal2 = lambda t: np.sin(t/4 + 2) + np.sin(t/2) + np.sin(t + 1) + np.random.normal(loc=0.0, scale=0.2, size=t.shape)
    lmu_vs_gru(signal2)
