import torch
import torch.nn as nn
import torch.optim as optim

from tqdm.notebook import tqdm
import numpy as np
from matplotlib import pyplot as plt
from time import time

def train_lambda(model, lambda_func, future = 1, time_range = 24, tick_size = 0.1, max_t = 10000, epochs = 500, batch_size = 1028, lr=0.001, dev = torch.device("cuda")):   
    
    optimizer = optim.AdamW(model.parameters(), lr=lr)
    criterion = nn.MSELoss()
    model = model.double()
    model.to(dev)
    
    model.train()
    
    with tqdm(range(epochs)) as pbar:
        
        for _ in pbar:
            optimizer.zero_grad()
            starts = np.random.random_integers(0, max_t, batch_size)

            ticks = np.empty((batch_size, int(time_range / tick_size)))
            for i in range(ticks.shape[0]):
                ticks[i, :] = starts[i] + np.arange(time_range / tick_size) * tick_size

            future_ticks = np.empty((batch_size, future))
            for i in range(future_ticks.shape[0]):
                future_ticks[i, :] = starts[i] + time_range + np.arange(future) * tick_size

            x = torch.from_numpy(lambda_func(ticks)[:,None,:]).to(dev)
            #x = torch.from_numpy(lambda_func(ticks)[:,:,None]).to(dev)
            y = torch.from_numpy(lambda_func(future_ticks)).to(dev)
            
            y_hat = model(x)
            #print(y_hat.size())
            #print(y.size())
            loss = criterion(y_hat, y)
            pbar.set_postfix(loss=loss.item())

            loss.backward()
            optimizer.step()
        
    eval_lambda(model, lambda_func, time_range, tick_size, dev=dev)
            
def eval_lambda(model, lambda_func, time_range = 24, tick_size = 0.1, eval_from = 100, dev = torch.device("cuda")):
    
    ticks = eval_from + np.arange(time_range / tick_size) * tick_size
    x = torch.from_numpy(lambda_func(ticks)[None,None,:]).to(dev)
    
    model.eval()
    with torch.no_grad():
        
        y_hat = model(x)
        future_ticks = eval_from + time_range + np.arange(y_hat.shape[1]) * tick_size
        
        #plt.plot(ticks, x[0].cpu())
        plt.plot(future_ticks, y_hat[0].cpu())
        
def pre_train_lambda(model, lambda_func, future = 1, seq_len = 24, max_t = 10000, epochs = 500, batch_size = 128, lr=0.001, dev = torch.device("cuda")):
    
    print("pre train")
    optimizer = optim.AdamW(model.parameters(), lr=lr)
    criterion = nn.MSELoss()
    model.to(dev)
    
    model.train()
    
    for _ in range(epochs):
        optimizer.zero_grad()
        starts = np.random.random_integers(0, max_t, batch_size)

        ticks = np.empty((batch_size, seq_len))
        for i in range(ticks.shape[0]):
            ticks[i, :] = starts[i] + np.arange(seq_len)

            future_ticks = np.empty((batch_size, future))
        
        for i in range(future_ticks.shape[0]):
            future_ticks[i, :] = starts[i] + seq_len + np.arange(future)

        x = torch.from_numpy(lambda_func(ticks)[:,None,:]).to(dev)
        y = torch.from_numpy(lambda_func(future_ticks)).to(dev)
            
        y_hat = model(x)
        loss = criterion(y_hat, y)
        #print(f"loss: {loss.item()}")
        loss.backward()
        optimizer.step()
        

def forward_walk_train(model, time_series, optimizer, criterion, seq_len=32, future=8):
    
    start = time() * 1000
    loss_sum = 0
    iters = time_series.size(2) - (seq_len + future) + 1
    #print(iters)
    model.train()
    for i in range(iters):
        optimizer.zero_grad()
        loss = criterion(model(time_series[:,:,i:(seq_len+i)]), time_series[:,0,(seq_len+i):(seq_len+i+future)])
        loss_sum += loss.item()
        loss.backward()
        optimizer.step()
        
    duration = int(round(time()*1000 - start))
    loss_avg = loss_sum/iters
    #print(f"Training completed in {duration} ms - Loss: {loss_avg}")
    return duration, loss_avg
        