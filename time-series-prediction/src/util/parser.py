import argparse

# deprecated
def get_GRU_parser():
    parser = argparse.ArgumentParser()

    parser.add_argument('-k', '--kafka-ip', type=str, required=True, help="IP of kafka")
    parser.add_argument('-s', '--sequence-length', type=int, required=True, help="Length of input sequence")
    parser.add_argument('-p', '--prediction-length', type=int, required=True, help="Length of ouput prediction sequence")
    parser.add_argument('-i', '--input-interval', type=float, required=True, help="Time between two inputs in seconds")
    parser.add_argument('-a', '--average-length', type=int, default=1, help="Length of input sequence that should be averaged")
    parser.add_argument('-t', '--train-time', type=float, default=None, help="Max train time in seconds")
    parser.add_argument('-m', '--metrics-topic', type=str, default='statistics', help="Kafka topic to subscribe to to receive input")
    parser.add_argument('-o', '--output-topic', type=str, default='long-term-prediction', help="Kafka topic to which predictions are published")
    parser.add_argument('-e', '--expected-period', type=int, default=None, help="expected length of period")
    parser.add_argument('-x', '--self-simulate', type=bool, default=False, help="Metrics not available (x), self-simulate signal")
    parser.add_argument('-c', '--rnn-cell', type=str, default= 'GRU', help="Which rnn-cel to use: LSTM or GRU (default)")
    
    return parser