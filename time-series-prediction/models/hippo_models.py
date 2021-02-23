import torch
import torch.nn as nn
import torch.nn.functional as F

from model.orthogonalcell import OrthogonalCell
from model.model import Model as HiPPOModel
from model.rnn import RNN
from model.memory import TimeLSICell

class HiPPO(nn.Module):
    
    def __init__(self, hidden_dim, input_dim, future):
        
        super().__init__()
        self.hidden_dim = hidden_dim
        self.input_dim = input_dim
        
        self.ortho_rnn = RNN(OrthogonalCell(input_dim, hidden_dim))
        self.hidden2out = nn.Linear(hidden_dim, future)
        
    def forward(self, ts):
        _, ortho_out = self.ortho_rnn(ts.permute(2,0,1))
        #print(ortho_out.size())
        prediction = self.hidden2out(ortho_out)
        return prediction
    
class HiPPOMem(nn.Module):
    
    def __init__(self, hidden_dim, input_dim, future):
        
        super().__init__()
        self.hidden_dim = hidden_dim
        self.input_dim = input_dim
        self.rnn = RNN(TimeLSICell(input_dim, hidden_dim, 16))
        self.hidden2out = nn.Linear(hidden_dim, future)
        
    def forward(self, ts):
        _, rnn_out = self.rnn(ts.permute(2,0,1))
        #for m in rnn_out:
        #    print(m.size())
        prediction = self.hidden2out(rnn_out[0])
        return prediction
    

"""
class HiPPO(nn.Module):
    
    def __init__(self, hidden_dim, input_dim, future, output_dim=1):
        
        super().__init__()
        self.hidden_dim = hidden_dim
        self.input_dim = input_dim
        
        self.hippo = HiPPOModel(input_dim, output_dim, future, cell='orthogonal', cell_args={'hidden_size':hidden_dim})
        
    def forward(self, ts):
        hippo_out = self.hippo(ts.permute(0,2,1))
        print(ts.size())
        hippo_out = hippo_out.view(-1, self.hidden_dim)
        return hippo_out
"""