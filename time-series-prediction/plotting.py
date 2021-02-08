from matplotlib import pyplot as plt
from threading import Thread, Lock
import time

class LiveGraph:
    def __init__(self, future=8):
        self.x_data = self.y_data = [i for i in range(future)]
        self.figure = plt.figure()
        self.line, = plt.plot(self.x_data, self.y_data)
        self.data_lock = Lock()

        self.th = Thread(target=self.update_graph, daemon=True)
        self.th.start()

    def update_graph(self):
        
        with self.data_lock:
            self.line.set_data(self.x_data, self.y_data)
            
        self.figure.gca().relim()
        self.figure.gca().autoscale_view()
        
        time.sleep(0.5)

    def show(self):
        plt.show()

    def set_data(self, data):
        
        with self.data_lock:
            self.x_data = data

