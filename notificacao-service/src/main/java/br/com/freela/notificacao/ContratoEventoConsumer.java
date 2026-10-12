package br.com.freela.notificacao;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

@Component
class ContratoEventoConsumer {
    private static final Logger log = LoggerFactory.getLogger(ContratoEventoConsumer.class);
    private final ObjectMapper objectMapper;
    private final NotificacaoService service;

    ContratoEventoConsumer(ObjectMapper objectMapper, NotificacaoService service) {
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
            UUID clienteId = UUID.fromString(required(event.path("data"), "clienteId"));
            UUID freelancerId = UUID.fromString(required(event.path("data"), "freelancerId"));

            log.info("notificacao.evento.recebido eventId={} eventType={} contratoId={} correlationId={}",
                    eventId, eventType, contratoId, correlationId);

            UUID eventUuid = UUID.fromString(eventId);
            UUID contratoUuid = UUID.fromString(contratoId);
            if ("EntregaRegistrada".equals(eventType)) {
                registrar(eventUuid, contratoUuid, clienteId, eventType, correlationId);
            } else if ("ContratoCriado".equals(eventType)
                    || "ContratoConcluido".equals(eventType)
                    || "ContratoCancelado".equals(eventType)) {
                registrar(eventUuid, contratoUuid, clienteId, eventType, correlationId);
                registrar(eventUuid, contratoUuid, freelancerId, eventType, correlationId);
            } else {
                throw new IllegalArgumentException("Tipo de evento de contrato não suportado: " + eventType);
            }
        } catch (IOException | RuntimeException exception) {
            log.error("notificacao.evento.falha eventId={} eventType={} contratoId={} correlationId={}",
                    eventId, eventType, contratoId, correlationId, exception);
            throw exception;
        }
    }

    private void registrar(UUID eventId, UUID contratoId, UUID destinatarioId,
                           String eventType, String correlationId) {
        service.registrarEvento(eventId, contratoId, destinatarioId, eventType, correlationId,
                "O contrato " + contratoId + " sofreu a alteração " + eventType + ".");
    }

    private String required(JsonNode node, String field) {
        JsonNode value = node.get(field);
        if (value == null || value.isNull() || value.asText().isBlank()) {
            throw new IllegalArgumentException("Campo obrigatório ausente no evento: " + field);
        }
        return value.asText();
    }
}
