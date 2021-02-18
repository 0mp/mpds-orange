//package com.mpds.flinkautoscaler.port.adapter.kafka;
//
//import com.fasterxml.jackson.core.JsonProcessingException;
//import com.fasterxml.jackson.databind.ObjectMapper;
//import com.mpds.flinkautoscaler.domain.model.events.DomainEvent;
//import com.mpds.flinkautoscaler.infrastructure.config.KafkaProducerProps;
//import lombok.extern.slf4j.Slf4j;
//import org.apache.kafka.clients.producer.KafkaProducer;
//import org.apache.kafka.clients.producer.ProducerConfig;
//import org.apache.kafka.clients.producer.ProducerRecord;
//import org.apache.kafka.common.serialization.ByteArraySerializer;
//import org.apache.kafka.common.serialization.StringSerializer;
//import org.springframework.stereotype.Component;
//
//import java.util.HashMap;
//import java.util.Map;
//
//@Slf4j
//@Component
//public class DomainEventPublisher {
//    private final KafkaProducerProps kafkaProducerProps;
//
//    private final ObjectMapper objectMapper;
//
//    private final KafkaProducer<String, byte[]> producer;
//
//    public DomainEventPublisher(KafkaProducerProps kafkaProducerProps, ObjectMapper objectMapper) {
//        this.kafkaProducerProps = kafkaProducerProps;
//        this.objectMapper = objectMapper;
//
//        String BOOTSTRAP_SERVERS = this.kafkaProducerProps.getBootstrapServer();
//        String CLIENT_ID_CONFIG = this.kafkaProducerProps.getClientIdConfig();
//        String ACK_CONFIG = this.kafkaProducerProps.getAcksConfig();
//
//        Map<String, Object> props = new HashMap<>();
//        props.put(ProducerConfig.BOOTSTRAP_SERVERS_CONFIG, BOOTSTRAP_SERVERS);
//        props.put(ProducerConfig.CLIENT_ID_CONFIG, CLIENT_ID_CONFIG);
//        props.put(ProducerConfig.ACKS_CONFIG, ACK_CONFIG);
//        props.put(ProducerConfig.COMPRESSION_TYPE_CONFIG, "snappy");
//        props.put(ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, StringSerializer.class);
//        props.put(ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, ByteArraySerializer.class);
//
//        producer = new KafkaProducer<>(props);
//
//    }
//
//    public void publishEvent(DomainEvent domainEvent) {
//        ProducerRecord<String, byte[]> producerRecord = new ProducerRecord<>(kafkaProducerProps.getTopic(), domainEvent.getUuid().toString(), toBytes(domainEvent));
//        this.producer.send(producerRecord);
//    }
//
//
//    private byte[] toBytes(DomainEvent domainEvent) {
//        byte[] payload = new byte[domainEvent.toString().getBytes().length];
//        try {
//            payload = objectMapper.writeValueAsString(domainEvent).getBytes();
//        } catch (JsonProcessingException e) {
//            e.printStackTrace();
//        }
//        return payload;
//    }
//}
