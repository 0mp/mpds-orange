import matplotlib.pyplot as plt 
import numpy as np 
import random 
import json
from datetime import datetime, timedelta

 
# Create timestamps
# 2 days is 172800 seconds == 2880 minutes
# 2 days is 172800 seconds == 17280 10 seconds
timestamps = 2880 

fmt = '%Y-%m-%d %H:%M:%S'
base = datetime.strptime('2021-02-01 12:00:00', fmt)
timestamp_arr = np.array([(base + timedelta(minutes=i)).strftime(fmt) for i in range(timestamps)])
#print(timestamp_arr)



# Generate  Sin wave of Traffic
Fs = timestamps 
frequency = 2 
x = np.arange(timestamps) 
noise = np.random.normal(0,0.05,timestamps)
y = np.sin(2 * np.pi * frequency * x / Fs) - 0.7 * np.sin(4*np.pi * frequency * x / Fs )+noise 

plt.plot(x, y) 
plt.xlabel('Time (minutes)') 
plt.ylabel('Msg Load') 
plt.show() 


# Create Json Ojects for Kafka
with open('msg_load.json','w') as outfile:
    for i in range(timestamps):
        data = {}
        data['timestamp'] = timestamp_arr[i]
        data['load']= y[i]
        json.dump(data,outfile)
        outfile.write("\n")

