#from threading import Thread, Lock
#import time

import matplotlib
from matplotlib import pyplot as plt
import numpy as np

def plot_signal(signal, f=0, t=20, tick_amount=200):
    ticks = np.linspace(f, t, tick_amount)
    plt.plot(ticks, signal(ticks))

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

