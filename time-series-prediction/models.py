import torch
import torch.nn as nn
import torch.nn.functional as F
import torch.optim as optim


class LSTM_1D_TimeSeries(nn.Module):
    
    def __init__(self, hidden_dim, ts_len, future = 1):
        
        super().__init__()
        self.hidden_dim = hidden_dim
        self.lstm_out_dim = ts_len * hidden_dim
        
        self.lstm = nn.LSTM(1, hidden_dim, batch_first=True)
        self.hidden2out = nn.Linear(self.lstm_out_dim, future)
        
    def forward(self, ts):
            
        series_len = ts.size(1)
        lstm_out, _ = self.lstm(ts.view(-1, series_len, 1))
        prediction = self.hidden2out(lstm_out.contiguous().view(-1, self.lstm_out_dim))
            
        return prediction