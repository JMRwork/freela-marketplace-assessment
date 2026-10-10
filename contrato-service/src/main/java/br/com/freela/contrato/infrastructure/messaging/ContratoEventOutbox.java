package br.com.freela.contrato.infrastructure.messaging;

import br.com.freela.contrato.domain.shared.DomainEvent;
import br.com.freela.contrato.infrastructure.persistence.ContratoOutboxEntity;
import br.com.freela.contrato.infrastructure.persistence.ContratoOutboxRepository;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ContratoEventOutbox {
    private final ContratoOutboxRepository repository;
    private final ObjectMapper objectMapper;

    public ContratoEventOutbox(ContratoOutboxRepository repository, ObjectMapper objectMapper) {
        this.repository = repository;
        this.objectMapper = objectMapper;
    }

    @Transactional
    public void registrar(DomainEvent event, String correlationId) {
        var envelope = ContratoEventEnvelope.from(event, correlationId, objectMapper);
        try {
            String payload = objectMapper.writeValueAsString(envelope);
            repository.save(new ContratoOutboxEntity(event.eventId(), event.contratoId(), payload,
                    event.occurredAt()));
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("Não foi possível serializar o evento " + event.eventId(), exception);
        }
    }
}
