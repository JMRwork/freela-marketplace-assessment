package br.com.freela.contrato.infrastructure.persistence;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "contrato_event_outbox")
public class ContratoOutboxEntity {
    @Id
    private UUID eventId;

    @Column(nullable = false)
    private UUID contratoId;

    private String eventType;

    private String correlationId;

    @Column(length = 55)
    private String traceparent;

    @Column(nullable = false, length = 200)
    private String messageKey;

    @Column(nullable = false, columnDefinition = "text")
    private String payload;

    @Column(nullable = false)
    private Instant createdAt;

    private Instant publishedAt;

    protected ContratoOutboxEntity() {}

    public ContratoOutboxEntity(UUID eventId, UUID contratoId, String eventType,
                                String correlationId, String traceparent,
                                String payload, Instant createdAt) {
        this.eventId = eventId;
        this.contratoId = contratoId;
        this.eventType = eventType;
        this.correlationId = correlationId;
        this.traceparent = traceparent;
        this.messageKey = contratoId.toString();
        this.payload = payload;
        this.createdAt = createdAt;
    }

    public void markPublished(Instant publishedAt) {
        this.publishedAt = publishedAt;
    }

    public UUID getEventId() { return eventId; }
    public UUID getContratoId() { return contratoId; }
    public String getEventType() { return eventType; }
    public String getCorrelationId() { return correlationId; }
    public String getTraceparent() { return traceparent; }
    public String getMessageKey() { return messageKey; }
    public String getPayload() { return payload; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getPublishedAt() { return publishedAt; }
}
