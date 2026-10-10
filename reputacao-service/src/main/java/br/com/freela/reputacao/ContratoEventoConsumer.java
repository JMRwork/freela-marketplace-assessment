package br.com.freela.reputacao;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.math.BigDecimal;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

@Component
class ContratoEventoConsumer {
    private static final Logger log = LoggerFactory.getLogger(ContratoEventoConsumer.class);
    private final ObjectMapper objectMapper;
    private final ReputacaoService service;

    ContratoEventoConsumer(ObjectMapper objectMapper, ReputacaoService service) {
        this.objectMapper = objectMapper;
        this.service = service;
    }

    @KafkaListener(topics = "${app.kafka.topics.contrato-eventos}",
            groupId = "${spring.kafka.consumer.group-id}", concurrency = "3")
    public void receber(String payload) throws IOException {
        JsonNode event = objectMapper.readTree(payload);
        String eventType = required(event, "eventType");
        if (!"ContratoConcluido".equals(eventType)) {
            return;
        }

        UUID eventId = UUID.fromString(required(event, "eventId"));
        UUID contratoId = UUID.fromString(required(event, "contratoId"));
        JsonNode data = event.path("data");
        UUID freelancerId = UUID.fromString(required(data, "freelancerId"));
        BigDecimal valor = new BigDecimal(required(data, "valor"));
        String correlationId = required(event, "correlationId");

        log.info("reputacao.evento.recebido eventId={} eventType={} contratoId={} freelancerId={} correlationId={}",
                eventId, eventType, contratoId, freelancerId, correlationId);
        service.registrarContratoConcluido(eventId, contratoId, freelancerId, valor);
    }

    private String required(JsonNode node, String field) {
        JsonNode value = node.get(field);
        if (value == null || value.isNull() || value.asText().isBlank()) {
            throw new IllegalArgumentException("Campo obrigatório ausente no evento: " + field);
        }
        return value.asText();
    }
}
