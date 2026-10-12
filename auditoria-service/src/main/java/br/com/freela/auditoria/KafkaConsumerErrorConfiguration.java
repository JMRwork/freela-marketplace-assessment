package br.com.freela.auditoria;

import org.apache.kafka.common.TopicPartition;
import org.apache.kafka.common.config.TopicConfig;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.config.TopicBuilder;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.listener.DeadLetterPublishingRecoverer;
import org.springframework.kafka.listener.DefaultErrorHandler;
import org.springframework.util.backoff.FixedBackOff;

@Configuration
class KafkaConsumerErrorConfiguration {
    private static final Logger log = LoggerFactory.getLogger(KafkaConsumerErrorConfiguration.class);

    @Bean
    org.apache.kafka.clients.admin.NewTopic contratoEventosDeadLetterTopic(
            @Value("${app.kafka.topics.contrato-eventos}") String sourceTopic,
            @Value("${app.kafka.topics.partitions:3}") int partitions,
            @Value("${app.kafka.listener.dead-letter-retention-ms:2592000000}") long retentionMs) {
        return TopicBuilder.name(sourceTopic + ".auditoria-service.DLT")
                .partitions(partitions)
                .replicas(1)
                .config(TopicConfig.RETENTION_MS_CONFIG, Long.toString(retentionMs))
                .build();
    }

    @Bean
    DefaultErrorHandler kafkaListenerErrorHandler(
            KafkaTemplate<Object, Object> kafkaTemplate,
            @Value("${app.kafka.topics.contrato-eventos}") String sourceTopic,
            @Value("${app.kafka.listener.retry-interval-ms:1000}") long retryIntervalMs,
            @Value("${app.kafka.listener.retry-attempts:2}") long retryAttempts) {
        String deadLetterTopic = sourceTopic + ".auditoria-service.DLT";
        DeadLetterPublishingRecoverer recoverer = new DeadLetterPublishingRecoverer(
                kafkaTemplate,
                (record, exception) -> new TopicPartition(deadLetterTopic, record.partition()));
        recoverer.setFailIfSendResultIsError(true);
        DefaultErrorHandler errorHandler = new DefaultErrorHandler((record, exception) -> {
            log.error("kafka.dead-letter.publicacao.inicio service=auditoria-service topic={} partition={} offset={} key={}",
                    record.topic(), record.partition(), record.offset(), record.key(), exception);
            recoverer.accept(record, exception);
            log.info("kafka.dead-letter.publicacao.sucesso service=auditoria-service deadLetterTopic={} partition={} offset={} key={}",
                    deadLetterTopic, record.partition(), record.offset(), record.key());
        }, new FixedBackOff(retryIntervalMs, retryAttempts));
        errorHandler.setRetryListeners((ConsumerRecord<?, ?> record, Exception exception, int deliveryAttempt) ->
                log.warn("kafka.consumo.retry service=auditoria-service topic={} partition={} offset={} key={} attempt={}",
                        record.topic(), record.partition(), record.offset(), record.key(), deliveryAttempt, exception));
        return errorHandler;
    }
}
