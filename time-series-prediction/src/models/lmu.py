import tensorflow as tf
from tensorflow import keras
import keras_lmu

def get_lmu_model(seq_len, future, memory_dim=1, order=32, hidden_dim=16):
    
    lmu_layer = tf.keras.layers.RNN(
        keras_lmu.LMUCell(
            memory_d=memory_dim,
            order=order,
            theta=seq_len,
            hidden_cell=tf.keras.layers.SimpleRNNCell(hidden_dim),
            hidden_to_memory=False,
            memory_to_memory=False,
            input_to_hidden=True,
            kernel_initializer="ones",
        )
    )

    inputs = tf.keras.Input((seq_len, 1))
    lmus = lmu_layer(inputs)
    outputs = tf.keras.layers.Dense(future)(lmus)

    model = tf.keras.Model(inputs=inputs, outputs=outputs)
    #model.summary()
    
    return model