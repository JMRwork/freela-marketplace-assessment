package br.com.freela.contrato.infrastructure.messaging;

import br.com.freela.contrato.domain.shared.DomainEvent;
import br.com.freela.contrato.infrastructure.persistence.ContratoOutboxEntity;
import br.com.freela.contrato.infrastructure.persistence.ContratoOutboxRepository;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ContratoEventOutbox {
    private static final Logger log = LoggerFactory.getLogger(ContratoEventOutbox.class);
    private static final java.util.regex.Pattern TRACEPARENT_PATTERN =
            java.util.regex.Pattern.compile("00-([0-9a-f]{32})-([0-9a-f]{16})-0[01]");
    private final ContratoOutboxRepository repository;
    private final ObjectMapper objectMapper;

    public ContratoEventOutbox(ContratoOutboxRepository repository, ObjectMapper objectMapper) {
        this.repository = repository;
        this.objectMapper = objectMapper;
    }

    @Transactional
    public void registrar(DomainEvent event, String correlationId, String traceparent) {
        var envelope = ContratoEventEnvelope.from(event, correlationId, objectMapper);
        try {
            String payload = objectMapper.writeValueAsString(envelope);
            String validTraceparent = validarTraceparent(event, traceparent);
            repository.save(new ContratoOutboxEntity(event.eventId(), event.contratoId(),
                    event.eventType(), correlationId, validTraceparent, payload, event.occurredAt()));
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("Não foi possível serializar o evento " + event.eventId(), exception);
        }
    }

    private String validarTraceparent(DomainEvent event, String traceparent) {
        if (traceparent == null || traceparent.isBlank()) {
            return null;
        }
        var matcher = TRACEPARENT_PATTERN.matcher(traceparent);
        if (!matcher.matches() || matcher.group(1).chars().allMatch(character -> character == '0')
                || matcher.group(2).chars().allMatch(character -> character == '0')) {
            log.warn("contrato.evento.traceparent.invalido eventId={} contratoId={}",
                    event.eventId(), event.contratoId());
            return null;
        }
        return traceparent;
    }
}
