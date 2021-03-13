#! /bin/sh -

set -eu

node_ip=$(gcloud compute instances list --format="value(EXTERNAL_IP)" | head -n 1)

brokers=$(kafkacat -b "$node_ip:31090" -L | grep '  broker' | awk '{print $4}' | tr '\n' ',')
brokers=${brokers%,}
printf '%s\n' "$brokers"
