from matplotlib import pyplot as plt
import seaborn as sns
import pandas as pd
import numpy as np
from datetime import datetime


def fix_parallelism(para):
	for  i in range(para.shape[0]):
		if para[i] <= 0:
			for j in range(i+1, para.shape[0]):
				if para[j] > 0:
					para[i] = para[j]
					break
		 
	
df = pd.read_csv("2021-03-17T13:03:14.656552125.csv")
df["Time"] = range(1, df.shape[0]+1)
#print(datetime.strptime(df["Timestamp"].loc[0], "%Y-%m-%dT%H:%M:%S"))

fix_parallelism(df["Parallelism"])


#df_qos_par = pd.melt(df, id_vars=["Timestamp"], value_vars=["Lag Latency", "Parallelism"])

#plot = sns.lineplot(data=df_qos_par, x="Timestamp", y="value", hue="variable")
#plot.figure.savefig("lag-lateny.png")

SMALL_SIZE = 16
MEDIUM_SIZE = 26
BIGGER_SIZE = 32

plt.rc('font', size=SMALL_SIZE)          # controls default text sizes
plt.rc('axes', titlesize=BIGGER_SIZE)     # fontsize of the axes title
plt.rc('axes', labelsize=MEDIUM_SIZE)    # fontsize of the x and y labels
plt.rc('xtick', labelsize=SMALL_SIZE)    # fontsize of the tick labels
plt.rc('ytick', labelsize=SMALL_SIZE)    # fontsize of the tick labels
plt.rc('legend', fontsize=SMALL_SIZE)    # legend fontsize
plt.rc('figure', titlesize=BIGGER_SIZE)  # fontsize of the figure title 

sns.set_theme()
fig, axes = plt.subplots(3,1, figsize=(28, 14))
#fig.suptitle("Scaling Results for Virus Spread Simulator")

g1 = sns.lineplot(ax=axes[0], data=df, x="Time", y="Lag Latency")
g1.set(xticklabels=[], xlabel=None)
g1.set(ylim=(-1,121))
g1.set(xlim=(0,730))
axes[0].set_title("QoS: Seconds to Eliminate Lag and Messages Incoming Meanwhile", fontsize=20)

g2 = sns.lineplot(ax=axes[1], data=df, x="Time", y="Parallelism", color='r')
g2.set(xticklabels=[], xlabel=None)
g2.set(xlim=(0,730))
axes[1].set_title("Flink Taskmanagers", fontsize=20)

g3 = sns.lineplot(ax=axes[2], data=df, x="Time", y="Kafka Message Rate", color='g')
g3.set(xlim=(0,730))
axes[2].set_title("Ingestion Rate by Kafka in Messages per Second", fontsize=20)


fig.savefig("results_covid.png")
