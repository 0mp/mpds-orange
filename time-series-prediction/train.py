import torch
import torch.nn as nn
import torch.optim as optim

from tqdm.notebook import tqdm
import numpy as np

from matplotlib import pyplot as plt

def train_lambda(model, lambda_func, future = 1, time_range = 24, tick_size = 0.1, max_t = 1000, epochs = 500, batch_size = 512, dev = torch.device("cuda")):   
    
    optimizer = optim.AdamW(model.parameters())
    criterion = nn.MSELoss()
    model = model.double()
    model.to(dev)
    
    model.train()
    
    with tqdm(range(epochs)) as pbar:
        
        for _ in pbar:
            starts = np.random.random_integers(0, max_t, batch_size)

            ticks = np.empty((batch_size, int(time_range / tick_size)))
            for i in range(ticks.shape[0]):
                ticks[i, :] = starts[i] + np.arange(time_range / tick_size) * tick_size

            future_ticks = np.empty((batch_size, future))
            for i in range(future_ticks.shape[0]):
                future_ticks[i, :] = starts[i] + time_range + np.arange(future) * tick_size

            x = torch.from_numpy(lambda_func(ticks)[:,None,:]).to(dev)
            y = torch.from_numpy(lambda_func(future_ticks)).to(dev)
            
            y_hat = model(x)
            #print(y_hat.size())
            #print(y.size())
            loss = criterion(y_hat, y)
            pbar.set_postfix(loss=loss.item())

            loss.backward()
            optimizer.step()
        
    eval_lambda(model, lambda_func, time_range, tick_size, dev)
            
def eval_lambda(model, lambda_func, time_range = 24, tick_size = 0.1, eval_from = 100, dev = torch.device("cuda")):
    
    ticks = 100 + np.arange(time_range / tick_size) * tick_size
    x = torch.from_numpy(lambda_func(ticks)[None,None,:]).to(dev)
    
    model.eval()
    with torch.no_grad():
        
        y_hat = model(x)
        future_ticks = 100 + time_range + np.arange(y_hat.shape[1]) * tick_size
        
        #plt.plot(ticks, x[0].cpu())
        plt.plot(future_ticks, y_hat[0].cpu())
    
    
        
        
        
        