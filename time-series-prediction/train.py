import torch
import torch.nn as nn
import torch.optim as optim

from tqdm.notebook import tqdm
import numpy as np

def train_lambda(model, lambda_func, future = 1, time_range = 24, tick_size = 0.1, max_t = 10000, epochs = 500, batch_size = 128, dev = torch.device("cuda")):   
    
    optimizer = optim.AdamW(model.parameters())
    criterion = nn.MSELoss()
    model = model.double()
    model.to(dev)
    
    model.train()
    
    with tqdm(range(epochs)) as pbar:
        
        for _ in pbar:
            starts = np.random.random_integers(0, max_t, batch_size)

            ticks = np.empty((batch_size, int(np.floor(time_range / tick_size))))
            for i in range(ticks.shape[0]):
                ticks[i, :] = starts[i] + np.arange(time_range / tick_size) * tick_size

            future_ticks = np.empty((batch_size, future))
            for i in range(future_ticks.shape[0]):
                future_ticks[i, :] = starts[i] + time_range + np.arange(future) * tick_size

            x = torch.from_numpy(lambda_func(ticks)).to(dev)
            y = torch.from_numpy(lambda_func(future_ticks)).to(dev)

            loss = criterion(model(x), y)
            pbar.set_postfix(loss=loss.item())

            loss.backward()
            optimizer.step()
        
        
        