package com.mpds.flinkautoscaler.application.scheduler;

import com.mpds.flinkautoscaler.application.mappers.PrometheusMetricMapper;
import com.mpds.flinkautoscaler.domain.model.Data;
import com.mpds.flinkautoscaler.domain.model.PrometheusMetric;
import com.mpds.flinkautoscaler.domain.model.events.DomainEvent;
import com.mpds.flinkautoscaler.domain.model.events.MetricReported;
import com.mpds.flinkautoscaler.infrastructure.config.PrometheusProps;
import com.mpds.flinkautoscaler.port.adapter.kafka.DomainEventPublisherReactive;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.reactive.function.BodyInserters;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Flux;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;

@Component
@Slf4j
@RequiredArgsConstructor
public class MetricRetrieveScheduler {

    // Used to query the metrics via REST
    private final WebClient webClient;

    private final PrometheusProps prometheusProps;

    private final DomainEventPublisherReactive domainEventPublisherReactive;

    private final PrometheusMetricMapper prometheusMetricMapper;

    private long sequenceNumber;

    private static final String KAFKA_METRIC_TOPIC ="metric";

    // Every 5 seconds
    @Scheduled(fixedDelay = 5000)
    public void scheduleMetricRetrieval() {
        log.info("Start retrieving metrics...");

        LocalDateTime currentDateTime = LocalDateTime.ofInstant(Instant.now(), ZoneOffset.UTC);

        String currentDateTimeString = currentDateTime.format(DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss'Z'"));


        // TODO 1: Retrieve metrics via webClient
        this.webClient.post()
                .uri("http://34.107.94.158:30090/api/v1/query")
//                .uri(prometheusProps.getQueryPath())
                .body(BodyInserters.fromFormData(getKafkaMessagesPerSecondsFormData(currentDateTimeString)))
                .retrieve()
                .bodyToMono(PrometheusMetric.class)
                .flatMap(prometheusMetric -> {
//                    System.out.println(prometheusMetric);
                    Data data = prometheusMetric.getData();
                    System.out.println(data.toString());

                    return Flux.fromIterable(data.getResult())
                            .flatMap(result -> {
                                this.sequenceNumber++;
                                Object[] resultObject = result.getValue();
                                int unix_timestamp = (int) resultObject[0];
                                LocalDateTime onDateTime = LocalDateTime.ofInstant(Instant.ofEpochSecond(unix_timestamp), ZoneOffset.UTC);
                                log.info("Metric retrieved at: " + onDateTime.toString());

                                Float kafkaMessagesPerSeconds = Float.parseFloat(String.valueOf(resultObject[1]));
                                log.info("Kafka Messages per seconds: " + kafkaMessagesPerSeconds);

                                DomainEvent domainEvent = new MetricReported(this.sequenceNumber, kafkaMessagesPerSeconds, onDateTime, KAFKA_METRIC_TOPIC, "", 0, 0, 0, 0, true, 0.0f, 0.0f);
                                return this.domainEventPublisherReactive.sendMessages(domainEvent);
                            }).then();


//                    return Mono.empty();
                })
                .subscribe();
        // TODO 2: Parse metric to DomainEvent (MetricReported)

        // TODO 3: Publish the domain event to Kafkausing the domainEventPublisherReactive instance
    }

    private MultiValueMap<String, String> getKafkaMessagesPerSecondsFormData(String dateTime) {

        log.info("getKafkaMessagesPerSecondsFormData for dateTime: " + dateTime);

        LinkedMultiValueMap<String, String> kafkaMessagesPerSecondsLinkedMultiValueMap = new LinkedMultiValueMap<>();
        final String PROMETHEUS_QUERY = "sum by (topic) (rate(kafka_server_brokertopicmetrics_messagesinpersec_count[2m]))";
        kafkaMessagesPerSecondsLinkedMultiValueMap.add("query", PROMETHEUS_QUERY);
        kafkaMessagesPerSecondsLinkedMultiValueMap.add("time", dateTime);

        return kafkaMessagesPerSecondsLinkedMultiValueMap;
    }
}
