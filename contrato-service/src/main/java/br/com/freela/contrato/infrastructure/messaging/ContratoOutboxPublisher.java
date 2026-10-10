package br.com.freela.contrato.infrastructure.messaging;

import br.com.freela.contrato.infrastructure.persistence.ContratoOutboxEntity;
import br.com.freela.contrato.infrastructure.persistence.ContratoOutboxRepository;
import java.time.Instant;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
public class ContratoOutboxPublisher {
    private static final Logger log = LoggerFactory.getLogger(ContratoOutboxPublisher.class);

    private final ContratoOutboxRepository repository;
    private final KafkaTemplate<String, String> kafkaTemplate;
    private final String topic;

    public ContratoOutboxPublisher(ContratoOutboxRepository repository,
                                   KafkaTemplate<String, String> kafkaTemplate,
                                   @Value("${app.kafka.topics.contrato-eventos}") String topic) {
        this.repository = repository;
        this.kafkaTemplate = kafkaTemplate;
        this.topic = topic;
    }

    @Scheduled(fixedDelayString = "${app.kafka.outbox.fixed-delay:1000}")
    @Transactional
    public void publicarPendentes() {
        for (ContratoOutboxEntity event : repository.findTop100ByPublishedAtIsNullOrderByCreatedAtAsc()) {
            try {
                kafkaTemplate.send(topic, event.getMessageKey(), event.getPayload())
                        .get(10, TimeUnit.SECONDS);
                event.markPublished(Instant.now());
                repository.save(event);
                log.info("contrato.evento.publicado eventId={} contratoId={} topic={} key={}",
                        event.getEventId(), event.getContratoId(), topic, event.getMessageKey());
            } catch (InterruptedException exception) {
                Thread.currentThread().interrupt();
                log.error("contrato.evento.publicacao.interrompida eventId={} contratoId={}",
                        event.getEventId(), event.getContratoId(), exception);
                return;
            } catch (ExecutionException | TimeoutException | RuntimeException exception) {
                log.error("contrato.evento.publicacao.falhou eventId={} contratoId={} topic={}",
                        event.getEventId(), event.getContratoId(), topic, exception);
                return;
            }
        }
    }
}
