import tensorflow as tf
from tensorflow import keras
import numpy as np
from time import time



def forward_walk_train(model, time_series, seq_len, future, optimizer, loss_fn):
    
    @tf.function
    def train_step(x, y):
        with tf.GradientTape() as tape:
            y_hat = model(x, training=True)
            loss = loss_fn(y, y_hat)
        grads = tape.gradient(loss, model.trainable_weights)
        optimizer.apply_gradients(zip(grads, model.trainable_weights))
        return loss
        
    start = time() * 1000
    loss_sum = 0
    iters =  time_series.shape[1] - (seq_len + future) + 1
    for i in range(iters):
        loss_sum += train_step(time_series[:,i:seq_len+i,:], time_series[:,(seq_len+i):(seq_len+i+future),0])
    duration = int(round(time()*1000 - start))
    loss_avg = loss_sum/iters
    
    #print(f"Training completed in {duration} ms - Loss: {loss_avg}")
    return duration, loss_avg


def pre_train_lambda(model, lambda_func, future = 1, seq_len = 24, tick_size = 0.1, max_t = 10000, epochs = 500, batch_size = 128, lr=0.001):
    
    print("pre train")
    optimizer = keras.optimizers.Adam(learning_rate=lr)
    loss_fn = keras.losses.MSE
    
    @tf.function
    def train_step(x, y):
        with tf.GradientTape() as tape:
            y_hat = model(x, training=True)
            loss = loss_fn(y, y_hat)
        grads = tape.gradient(loss, model.trainable_weights)
        optimizer.apply_gradients(zip(grads, model.trainable_weights))
        return loss
    
    for _ in range(epochs):
        
        starts = np.random.random_integers(0, max_t, batch_size)
        ticks = np.empty((batch_size, seq_len))
        
        for i in range(ticks.shape[0]):
            ticks[i, :] = starts[i] + np.arange(seq_len)

            future_ticks = np.empty((batch_size, future))
        
        for i in range(future_ticks.shape[0]):
            future_ticks[i, :] = starts[i] + seq_len + np.arange(future)

        x = tf.convert_to_tensor(lambda_func(ticks)[:,:,None])
        y = tf.convert_to_tensor(lambda_func(future_ticks))
            
        train_step(x, y)