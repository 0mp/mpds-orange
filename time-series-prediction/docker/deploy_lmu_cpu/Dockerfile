FROM ubuntu:18.04

RUN apt-get update && apt-get install -y --no-install-recommends \
	apt-utils \
	build-essential \
	cmake \
	git \
	curl \
	python3 \
	python3-pip \
	python3-dev \
	python3-setuptools \
	python3-wheel \
    && rm -rf /var/lib/apt/lists/*

Run python3 -m pip install --upgrade pip

COPY docker/deploy_lmu_cpu/requirements.txt /requirements.txt
RUN python3 -m pip install --no-cache-dir -r /requirements.txt

RUN mkdir /app
RUN mkdir /app/src
COPY src /app/src
COPY lmu_config.yaml /app
WORKDIR /app

CMD ["python3", "src/deploy_lmu.py"]
