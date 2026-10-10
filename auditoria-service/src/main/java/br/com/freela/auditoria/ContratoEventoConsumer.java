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
        JsonNode event = objectMapper.readTree(payload);
        UUID eventId = UUID.fromString(required(event, "eventId"));
        UUID contratoId = UUID.fromString(required(event, "contratoId"));
        String eventType = required(event, "eventType");
        String correlationId = required(event, "correlationId");
        Instant occurredAt = Instant.parse(required(event, "occurredAt"));

        log.info("auditoria.evento.recebido eventId={} eventType={} contratoId={} occurredAt={} correlationId={}",
                eventId, eventType, contratoId, occurredAt, correlationId);
        service.registrar(eventId, contratoId, eventType, correlationId, occurredAt, payload);
    }

    private String required(JsonNode node, String field) {
        JsonNode value = node.get(field);
        if (value == null || value.isNull() || value.asText().isBlank()) {
            throw new IllegalArgumentException("Campo obrigatório ausente no evento: " + field);
        }
        return value.asText();
    }
}
