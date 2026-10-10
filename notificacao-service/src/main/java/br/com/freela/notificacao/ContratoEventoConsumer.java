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
        JsonNode event = objectMapper.readTree(payload);
        UUID eventId = UUID.fromString(required(event, "eventId"));
        UUID contratoId = UUID.fromString(required(event, "contratoId"));
        UUID clienteId = UUID.fromString(required(event.path("data"), "clienteId"));
        UUID freelancerId = UUID.fromString(required(event.path("data"), "freelancerId"));
        String eventType = required(event, "eventType");
        String correlationId = required(event, "correlationId");

        log.info("notificacao.evento.recebido eventId={} eventType={} contratoId={} correlationId={}",
                eventId, eventType, contratoId, correlationId);

        if ("EntregaRegistrada".equals(eventType)) {
            registrar(eventId, contratoId, clienteId, eventType);
        } else if ("ContratoCriado".equals(eventType)
                || "ContratoConcluido".equals(eventType)
                || "ContratoCancelado".equals(eventType)) {
            registrar(eventId, contratoId, clienteId, eventType);
            registrar(eventId, contratoId, freelancerId, eventType);
        } else {
            throw new IllegalArgumentException("Tipo de evento de contrato não suportado: " + eventType);
        }
    }

    private void registrar(UUID eventId, UUID contratoId, UUID destinatarioId, String eventType) {
        service.registrarEvento(eventId, contratoId, destinatarioId, eventType,
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
