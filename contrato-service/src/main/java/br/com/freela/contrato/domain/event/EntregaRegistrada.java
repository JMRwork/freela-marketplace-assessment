package br.com.freela.contrato.domain.event;

import br.com.freela.contrato.domain.model.Contrato;
import br.com.freela.contrato.domain.shared.DomainEvent;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public record EntregaRegistrada(UUID eventId, Instant occurredAt, UUID contratoId,
                                UUID clienteId, UUID freelancerId, String titulo, BigDecimal valor)
        implements DomainEvent {
    public static EntregaRegistrada gerar(Contrato contrato) {
        return new EntregaRegistrada(UUID.randomUUID(), Instant.now(), contrato.id(), contrato.clienteId(),
                contrato.freelancerId(), contrato.titulo(), contrato.valor());
    }
    @Override public String eventType() { return "EntregaRegistrada"; }
}
