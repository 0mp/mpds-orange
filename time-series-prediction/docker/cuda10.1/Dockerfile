FROM nvidia/cuda:10.1-cudnn7-devel-ubuntu18.04

RUN apt-get update && apt-get upgrade -y

RUN apt-get install -y --no-install-recommends\
	apt-utils \
	build-essential \
	cmake \
	git \
	curl \
	iputils-ping \
	python3 \
	python3-pip \
	python3-dev \
	python3-setuptools \
	python3-wheel

Run python3 -m pip install --upgrade pip

COPY requirements.txt /requirements.txt
RUN python3 -m pip install --no-cache-dir -r /requirements.txt

#RUN useradd --create-home --shell /bin/bash --gid root caesar

#ENV HOME=/home/caesar
#RUN mkdir -p /home/caesar && chmod 777 /home/caesar
#WORKDIR /home/caeser


EXPOSE 8888

RUN mkdir /mount
RUN chmod 777 /mount
WORKDIR /mount

RUN mkdir /external
RUN cd /external && git clone https://github.com/HazyResearch/hippo-code.git
ENV PYTHONPATH "${PYTHONPATH}:/external/hippo-code"

CMD ["jupyter", "notebook", "--allow-root", "--notebook-dir=/mount", "--ip=0.0.0.0", "--port=8888", "--no-browser", "--NotebookApp.token=timeseries"]
