#from threading import Thread, Lock
#import time

import matplotlib
from matplotlib import pyplot as plt
import numpy as np
import pandas as pd
import os
from math import ceil
from threading import Thread, Lock
import time

def plot_signal(signal, f=0, t=20, tick_amount=200):
    
    plt.figure(figsize=(14,8))
    ticks = np.linspace(f, t, tick_amount)
    plt.plot(ticks, signal(ticks))
    plt.xlabel("Time Steps", fontsize=15)
    plt.ylabel("Traffic Load", fontsize=15)
    os.makedirs("./plots", exist_ok=True)
    plt.savefig(os.path.join("./plots", "test-signal") +".png")

def plot_pred_loss_repo(dir_path, from_time = 0, cols = 2):
    
    rows = ceil(len(os.listdir(dir_path))/float(cols))
    figure, axis = plt.subplots(rows, cols, figsize=(cols*12,rows*5))
    
    j = -1
    
    for i, file_name in enumerate(os.listdir(dir_path)):
        file_path = os.path.join(dir_path, file_name)
        df = pd.read_csv(file_path)
        
        pred_loss = df["pred_loss"][from_time:]
        
        #if i % 4 == 0:
        #    j += 1
        #print(j, i % 4)
        axis[int(i/cols), i % cols].plot(np.arange(pred_loss.shape[0]), pred_loss)
        axis[int(i/cols), i % cols].set_title(file_name)
   
    plt.show()
    
def plot_comparison(list_y_values, x_values, labels, title):
    
    plt.figure(figsize=(14,8))
    
    for y_values, label in zip(list_y_values, labels):
        plt.plot(x_values, y_values, label=label)
    
    plt.legend(loc="upper right", prop={'size': 18})
    plt.title(title, fontsize=22, fontweight="bold")
    plt.xlabel("Time Steps", fontsize=15)
    plt.ylabel("Prediction Loss", fontsize=15)
    #plt.show()
    os.makedirs("./plots", exist_ok=True)
    plt.savefig(os.path.join("./plots", "-".join(title.split())) +".png")
    
    
class LivePlotPrediction():
    
    def __init__(self, live_plot, seq_len, pred_len):
        
        self.seq_len = seq_len
        self.pred_len = pred_len
        #self.seq = list(range(seq_len))
        #self.pred = list(range(seq_len, seq_len+pred_len))
        self.line_seq = live_plot.add_line()
        self.line_pred = live_plot.add_line()
        
    
    """def run(self):
        
        with LiveGraph(backend='nbAgg') as h:
            
            self.line_seq = h.add_line()
            self.line_pred = h.add_line()
            
            while(True):
                with self.lock:
                    self.line_seq.update(range(self.seq_len), self.seq)
                    self.line_pred.update(range(self.seq_len, self.seq_len+self.pred_len), self.pred)
                time.sleep(self.update_interval)"""
        
    def update(self, seq, pred):
        self.line_seq.update(range(self.seq_len), seq)
        self.line_pred.update(range(self.seq_len, self.seq_len+self.pred_len), pred)
        

class LiveLine:
    def __init__(self, graph, fmt=''):
        # LiveGraph object
        self.graph = graph
        # instant line
        self.line, = self.graph.ax.plot([], [], fmt)
        # holder of new lines
        self.lines = []

    def update(self, x_data, y_data):
        # update the instant line
        self.line.set_data(x_data, y_data)
        self.graph.update_graph()

    def addtive_plot(self, x_data, y_data, fmt=''):
        # add new line in the same figure
        line, = self.graph.ax.plot(x_data, y_data, fmt)
        # store line in lines holder
        self.lines.append(line)
        # update figure
        self.graph.update_graph()
        # return line index
        return self.lines.index(line)

    def update_indexed_line(self, index, x_data, y_data):
        # use index to update that line
        self.lines[index].set_data(x_data, y_data)
        self.graph.update_graph()


class LiveGraph:
    def __init__(self, backend='nbAgg', figure_arg={}, window_title=None, 
                 suptitle_arg={'t':None}, ax_label={'x':'', 'y':''}, ax_title=None):

        # save current backend for later restore
        self.origin_backend = matplotlib.get_backend()

        # check if current backend meets target backend
        if self.origin_backend != backend:
            print("original backend:", self.origin_backend)
            # matplotlib.use('nbAgg',warn=False, force=True)
            plt.switch_backend(backend)
            print("switch to backend:", matplotlib.get_backend())

        # set figure
        self.figure = plt.figure(**figure_arg)
        self.figure.canvas.set_window_title(window_title)
        self.figure.suptitle(**suptitle_arg)

        # set axis
        self.ax = self.figure.add_subplot(111)
        self.ax.set_xlabel(ax_label['x'])
        self.ax.set_ylabel(ax_label['y'])
        self.ax.set_title(ax_title)

        # holder of lines
        self.lines = []

    def __enter__(self):
        return self

    def __exit__(self, exc_type, exc_val, exc_tb):
        self.close()

    def close(self):
        # check if current beckend meets original backend, if not, restore it
        if matplotlib.get_backend() != self.origin_backend:
            # matplotlib.use(self.origin_backend,warn=False, force=True)
            plt.switch_backend(self.origin_backend)
            print("restore to backend:", matplotlib.get_backend())

    def add_line(self, fmt=''):
        line = LiveLine(graph=self, fmt=fmt)
        self.lines.append(line)
        return line

    def update_graph(self):
        self.figure.gca().relim()
        self.figure.gca().autoscale_view()
        self.figure.canvas.draw()

