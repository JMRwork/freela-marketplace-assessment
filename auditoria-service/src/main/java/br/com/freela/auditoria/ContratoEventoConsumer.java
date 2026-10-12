package br.com.freela.auditoria;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.time.Instant;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

@Component
class ContratoEventoConsumer {
    private static final Logger log = LoggerFactory.getLogger(ContratoEventoConsumer.class);
    private final ObjectMapper objectMapper;
    private final AuditoriaService service;

    ContratoEventoConsumer(ObjectMapper objectMapper, AuditoriaService service) {
        this.objectMapper = objectMapper;
        this.service = service;
    }

    @KafkaListener(topics = "${app.kafka.topics.contrato-eventos}",
            groupId = "${spring.kafka.consumer.group-id}",
            concurrency = "${app.kafka.listener.concurrency}")
    public void receber(String payload) throws IOException {
        String eventId = "desconhecido";
        String contratoId = "desconhecido";
        String eventType = "desconhecido";
        String correlationId = "desconhecido";
        try {
            JsonNode event = objectMapper.readTree(payload);
            eventId = required(event, "eventId");
            contratoId = required(event, "contratoId");
            eventType = required(event, "eventType");
            correlationId = required(event, "correlationId");
            Instant occurredAt = Instant.parse(required(event, "occurredAt"));

            log.info("auditoria.evento.recebido eventId={} eventType={} contratoId={} occurredAt={} correlationId={}",
                    eventId, eventType, contratoId, occurredAt, correlationId);
            service.registrar(UUID.fromString(eventId), UUID.fromString(contratoId), eventType,
                    correlationId, occurredAt, payload);
        } catch (IOException | RuntimeException exception) {
            log.error("auditoria.evento.falha eventId={} eventType={} contratoId={} correlationId={}",
                    eventId, eventType, contratoId, correlationId, exception);
            throw exception;
        }
    }

    private String required(JsonNode node, String field) {
        JsonNode value = node.get(field);
        if (value == null || value.isNull() || value.asText().isBlank()) {
            throw new IllegalArgumentException("Campo obrigatório ausente no evento: " + field);
        }
        return value.asText();
    }
}
