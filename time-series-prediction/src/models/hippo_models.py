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
    
class HiPPOMemPlainSingleStepsCell(nn.Module):
    
    def __init__(self, hidden_dim, input_dim=1, future=32, memory_dim=256):
        
        super().__init__()
        self.hidden_dim = hidden_dim
        self.input_dim = input_dim
        self.memory_dim = memory_dim
        self.future = future
        self.mem_cell = TimeLSICell(input_dim,
                                    hidden_dim,
                                    memory_dim,
                                    measure = "legs",
                                    method = "manual",
                                    memory_order = -1
                                   )
        self.state = None
        
        
    def forward(self, next_step):
        if self.state is None:
            self.state = self.mem_cell.default_state(next_step[0], 1)
        mem_out, new_state = self.mem_cell(next_step, self.state)
        self.state = (new_state[0].detach(), new_state[1].detach(), new_state[2].detach())
        return mem_out
      
class HiPPOMemPlainSingleSteps(nn.Module):
    
    def __init__(self, hidden_dim, input_dim=1, future=32, memory_dim=256):
        
        super().__init__()
        self.mem_cell = HiPPOMemPlainSingleStepsCell(hidden_dim, input_dim, future, memory_dim)
        self.hidden2out = nn.Linear(hidden_dim, future)
        
    def forward(self, next_step):
        mem_out = self.mem_cell(next_step)
        prediction = self.hidden2out(mem_out)
        return prediction
    
class HiPPOMemPlainSingleSteps2layer(nn.Module):
    
    def __init__(self, hidden_dim, input_dim=1, future=32, memory_dim=256):
        
        super().__init__()
        self.mem_cell = HiPPOMemPlainSingleStepsCell(hidden_dim, input_dim, future, memory_dim)
        self.hidden2out1 = nn.Linear(hidden_dim, 16)
        self.hidden2out2 = nn.Linear(16, future)
        
    def forward(self, next_step):
        mem_out = self.mem_cell(next_step)
        prediction = self.hidden2out2(F.relu(self.hidden2out1(mem_out)))
        return prediction

class HiPPOMemPlainCell(nn.Module):
    
    def __init__(self, hidden_dim, input_dim=1, future=32, memory_dim=256, init_state=None):
        
        
        super().__init__()
        self.hidden_dim = hidden_dim
        self.input_dim = input_dim
        self.memory_dim = memory_dim
        self.future = future
        self.mem_cell = TimeLSICell(input_dim,
                                    hidden_dim,
                                    memory_dim,
                                    measure = "legs",
                                    method = "manual",
                                    memory_order = 256
                                   )
        self.init_state = init_state
        
    def forward(self, ts):
        ts = ts.permute(0,2,1)
        if self.init_state is None:
            state = self.mem_cell.default_state(ts[0,0,:], 1)
        else:
            state = self.init_state
                
        for i in range(ts.size(1)):
            mem_out, state = self.mem_cell(ts[:,i,:], state)
        
        return mem_out
                
class HiPPOMemPlain(nn.Module):
    
    def __init__(self, hidden_dim, input_dim=1, future=32, memory_dim=1):
        
        super().__init__()
        self.mem_cell = HiPPOMemPlainCell(hidden_dim, input_dim, future, memory_dim)
        self.hidden2out = nn.Linear(hidden_dim, future)
        
    def forward(self, ts):
        mem_out = self.mem_cell(ts)
        prediction = self.hidden2out(mem_out)
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