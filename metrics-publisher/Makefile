# List all available Kafka topics.
kafka-list-topics:
	docker-compose exec kafka kafka-topics.sh --list --zookeeper zookeeper

# Show all messages from topic "metrics".
kafka-show-topic-metrics:
	docker-compose exec kafka kafka-console-consumer.sh --bootstrap-server :9092 --topic metrics --from-beginning
