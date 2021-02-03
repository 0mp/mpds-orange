FROM nvidia/cuda:10.1-cudnn7-devel-ubuntu18.04

RUN apt-get update && apt-get upgrade -y

RUN apt-get install -y --no-install-recommends\
	apt-utils \
	build-essential \
	cmake \
	git \
	curl \
	python3 \
	python3-pip \
	python3-dev \
	python3-setuptools \
	python3-wheel

Run pip3 install --upgrade pip
	
COPY requirements.txt /requirements.txt

RUN pip3 install --no-cache-dir -r requirements.txt

EXPOSE 8888

RUN useradd --create-home --shell /bin/bash --gid root caeser

USER caeser

WORKDIR /home/caeser

RUN mkdir mount

CMD ["jupyter", "notebook", "--allow-root", "--notebook-dir=/home/caeser/mount", "--ip=0.0.0.0", "--port=8888", "--no-browser", "--NotebookApp.token=timeseries"]
