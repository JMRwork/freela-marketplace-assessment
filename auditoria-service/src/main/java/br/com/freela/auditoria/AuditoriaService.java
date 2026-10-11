package br.com.freela.auditoria;

import java.time.Instant;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AuditoriaService {
    private static final Logger log = LoggerFactory.getLogger(AuditoriaService.class);
    private final EventoAuditoriaRepository repository;

    public AuditoriaService(EventoAuditoriaRepository repository) {
        this.repository = repository;
    }

    @Transactional
    public void registrar(UUID eventId, UUID aggregateId, String eventType, String correlationId,
                          Instant occurredAt, String payload) {
        log.info("auditoria.registro.inicio eventId={} aggregateId={} eventType={} occurredAt={} correlationId={}",
                eventId, aggregateId, eventType, occurredAt, correlationId);
        if (repository.existsByEventId(eventId)) {
            log.info("auditoria.registro.duplicado eventId={} aggregateId={}", eventId, aggregateId);
            return;
        }
        var evento = repository.saveAndFlush(new EventoAuditoria(eventId, aggregateId, eventType,
                correlationId, occurredAt, payload));
        log.info("auditoria.registro.sucesso auditoriaId={} eventId={} aggregateId={} eventType={} occurredAt={} correlationId={}",
                evento.id, eventId, aggregateId, eventType, occurredAt, correlationId);
    }
}
