package br.com.freela.contrato.infrastructure.messaging;

import br.com.freela.contrato.domain.shared.DomainEvent;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record ContratoEventEnvelope(
        UUID eventId,
        String eventType,
        String aggregateType,
        UUID contratoId,
        Instant occurredAt,
        String correlationId,
        JsonNode data) {

    public static ContratoEventEnvelope from(DomainEvent event, String correlationId, ObjectMapper mapper) {
        ObjectNode data = mapper.valueToTree(event);
        data.remove(List.of("eventId", "occurredAt", "contratoId"));
        return new ContratoEventEnvelope(event.eventId(), event.eventType(), "Contrato",
                event.contratoId(), event.occurredAt(), correlationId, data);
    }
}
