# Setup

## LMU CPU Model

Run `python3 src/deploy_lmu.py`

The dependencies can be installed with `pip3 install docker/deploy_lmu_cpu/requirements.txt`.

Everything can be run easily in docker via the Dockerfile `docker/deploy_lmu_cpu/Dockerfile`

Build: 
```
docker build -t lmu-long-term-prediction -f /docker/deploy_lmu_cpu/Dockerfile .
```
Run:
```
docker run --name LMU lmu-long-term-prediction
```

### Configuration

Configuration settings can be specified before running/building in `lmu_config.yaml`:

Name | Function
---------- | ---------------
kafka.ip | ip and port of kafka broker
kafka.in_topic | name of kafka topic that supplies metrics
kafka.out_topic | name of kafka topic to publish to
kafka.input_interval | interval in seconds between input messages
kafka.avg\_agg_len | tumbling window size for average aggregation
kafka.expected_period | expected period of data in seconds
kafka.self_simulate  | whether input should be simulated
kafka.save_data | whether data and predictions should be saved
model.seq_len | input length for model
model.pred_len | ouput length for model
model.hidden_dim | hidden dimension of model
model.memory_dim | how many polynomials should be used to approximate
model.order | how many LMU cells should be stacked
train.time | length of training session between messages
train.buffer | how many messages should be buffered before first training
train.LR | learning rate
train.pretrain_iters | iterations pretrained on sin wave


## Simple CPU Model (For the time being deprecated, as not integrated with other changes)

Run  `python3 src/deploy_simple_rnn.py`

The dependencies can be installed with `pip3 install docker/deploy_simple_rnn_cpu/requirements.txt`.

Otherwise a Dockerfile is available in `docker/deploy_simple_rnn_cpu/Dockerfile`.
Build:
```
docker build -t autoscaler-longterm-prediction -f /docker/deploy_simple_rnn_cpu/Dockerfile .
```

### Configuration

Configuration settings can be specified in `simple_model_config.yaml`:

Name | Function
---------- | ---------------
kafka.ip | ip and port of kafka broker
kafka.in_topic | name of kafka topic that supplies metrics
kafka.out_topic | name of kafka topic to publish to
kafka.input_interval | interval in seconds between input messages
kafka.avg\_agg_len | tumbling window size for average aggregation
kafka.expected_period | expected period of data in seconds
kafka.self_simulate  | whether input should be simulated
model.seq_len | input length for model
model.pred_len | ouput length for model
model.cell | which rnn cell: "GRU" or "LSTM"
model.hidden_dim | hidden dimension of model
model.stack | how many cells to be stacked
train.time | length of training session between messages
train.buffer | how many messages should be buffered before first training
train.LR | learning rate
train.pretrain_iters | iterations pretrained on sin wave

Parameters in configuration file can be overriden by command line, like:
```
python3 src/deploy_simple_rnn.py kafka.ip=127.0.0.1:8000
```
