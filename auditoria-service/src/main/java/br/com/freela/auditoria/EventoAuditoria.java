package br.com.freela.auditoria;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "auditoria_eventos", uniqueConstraints = @UniqueConstraint(columnNames = "eventId"))
class EventoAuditoria {
    @Id
    UUID id;
    @Column(nullable = false, unique = true)
    UUID eventId;
    @Column(nullable = false)
    UUID aggregateId;
    @Column(nullable = false)
    String eventType;
    @Column(nullable = false)
    String correlationId;
    @Column(nullable = false)
    Instant occurredAt;
    @Column(nullable = false)
    Instant recebidoEm;
    @Column(nullable = false, columnDefinition = "text")
    String payload;

    protected EventoAuditoria() {}

    EventoAuditoria(UUID eventId, UUID aggregateId, String eventType, String correlationId,
                    Instant occurredAt, String payload) {
        this.id = UUID.randomUUID();
        this.eventId = eventId;
        this.aggregateId = aggregateId;
        this.eventType = eventType;
        this.correlationId = correlationId;
        this.occurredAt = occurredAt;
        this.payload = payload;
        this.recebidoEm = Instant.now();
    }

    public UUID getId() { return id; }
    public UUID getEventId() { return eventId; }
    public UUID getAggregateId() { return aggregateId; }
    public String getEventType() { return eventType; }
    public String getCorrelationId() { return correlationId; }
    public Instant getOccurredAt() { return occurredAt; }
    public Instant getRecebidoEm() { return recebidoEm; }
    public String getPayload() { return payload; }
}
