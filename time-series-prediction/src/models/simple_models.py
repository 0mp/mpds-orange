import torch
import torch.nn as nn
import torch.nn.functional as F
import torch.optim as optim


class LSTM(nn.Module):
    
    def __init__(self, hidden_dim, ts_len, input_dim=1, future=1, stacked=1, dropout=0):
        
        super().__init__()
        self.hidden_dim = hidden_dim
        self.input_dim = input_dim
        
        self.lstm = nn.LSTM(input_dim, hidden_dim, batch_first=True, num_layers=stacked, dropout=dropout)
        self.hidden2out = nn.Linear(hidden_dim, future)
        
    def forward(self, ts):
        lstm_out, _ = self.lstm(ts.permute(0,2,1))
        prediction = self.hidden2out(lstm_out[:,-1,:].view(-1, self.hidden_dim))
            
        return prediction
    
class GRU(nn.Module):
    
    def __init__(self, hidden_dim, ts_len, input_dim=1, future=1, stacked=1, dropout=0):
        
        super().__init__()
        self.hidden_dim = hidden_dim
        self.input_dim = input_dim
        
        self.gru = nn.GRU(input_dim, hidden_dim, batch_first=True, num_layers=stacked, dropout=dropout)
        self.hidden2out = nn.Linear(hidden_dim, future)
        
    def forward(self, ts):
        gru_out, _ = self.gru(ts.permute(0,2,1))
        prediction = self.hidden2out(gru_out[:, -1,:].view(-1, self.hidden_dim))
        return prediction
    

class CNN1D_1l_RNN(nn.Module):
    
    def __init__(self, hidden_dim, ts_len, stacked=3, future = 1, k_size=16, stride=4, ch_out=16, input_dim=1, cell_type="GRU"):
        
        super().__init__()
        self.conv1 = nn.Conv1d(input_dim, ch_out, k_size, stride)
        if cell_type == "GRU": 
            self.recurr_cell = GRU(hidden_dim, int((ts_len - k_size)/stride + 1), ch_out, future, stacked)
        else:
            self.recurr_cell = LSTM(hidden_dim, int((ts_len - k_size)/stride + 1), ch_out, future, stacked)
        
    def forward(self, ts):
        ts = F.relu(self.conv1(ts))
        return self.recurr_cell(ts)    
    
    
class CNN1D_2l_RNN(nn.Module):
    
    def __init__(self, hidden_dim, ts_len, stacked=2, future = 1, k_size=16, stride=4, ch_out=16, input_dim=1, cell_type="GRU"):
        
        super().__init__()
        self.conv1 = nn.Conv1d(input_dim, 8, k_size, stride)
        stride2 = stride
        if stride > 1:
            stride2 = int(stride/2)
        k_size2 = k_size
        if k_size > 2:
            k_size2 = int(k_size/2)

        self.conv2 = nn.Conv1d(8, ch_out, k_size2, stride2)
        if cell_type == "GRU": 
            self.recurr_cell = GRU(hidden_dim, int((ts_len - k_size)/stride + 1), ch_out, future, stacked)
        else:
            self.recurr_cell = LSTM(hidden_dim, int((ts_len - k_size)/stride + 1), ch_out, future, stacked)
        
    def forward(self, ts):
        
        ts = F.relu(self.conv1(ts))
        ts = F.relu(self.conv2(ts))
        return self.lstm(ts)
    
    

    