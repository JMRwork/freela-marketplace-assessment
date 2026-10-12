package br.com.freela.contrato.application;

import br.com.freela.contrato.domain.model.Contrato;
import br.com.freela.contrato.domain.repository.ContratoRepository;
import br.com.freela.contrato.domain.shared.DomainEvent;
import br.com.freela.contrato.infrastructure.messaging.ContratoEventOutbox;
import io.micrometer.tracing.Span;
import io.micrometer.tracing.Tracer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

@Service
public class ContratoApplicationService {
    private static final Logger log = LoggerFactory.getLogger(ContratoApplicationService.class);
    private final ContratoRepository repository;
    private final ContratoEventOutbox eventOutbox;
    private final Tracer tracer;

    public ContratoApplicationService(ContratoRepository repository, ContratoEventOutbox eventOutbox,
                                      Tracer tracer) {
        this.repository = repository;
        this.eventOutbox = eventOutbox;
        this.tracer = tracer;
    }

    @Transactional
    public Contrato criar(CriarContratoCommand cmd) {
        return criar(cmd, null, null);
    }

    @Transactional
    public Contrato criar(CriarContratoCommand cmd, String correlationId) {
        return criar(cmd, correlationId, null);
    }

    @Transactional
    public Contrato criar(CriarContratoCommand cmd, String correlationId, String traceparent) {
        String resolvedCorrelationId = resolveCorrelationId(correlationId);
        log.info("contrato.criacao.inicio correlationId={}", resolvedCorrelationId);
        Contrato contrato = Contrato.criar(cmd.clienteId(), cmd.freelancerId(), cmd.titulo(), cmd.valor());
        log.info("contrato.dominio.criado contratoId={} status={} domainEvents={} correlationId={}",
                contrato.id(), contrato.status(), contrato.domainEvents().size(), resolvedCorrelationId);
        Contrato salvo = repository.salvar(contrato);

        registrarEventos(contrato, resolvedCorrelationId, traceparentAtual(traceparent));

        log.info("contrato.criacao.sucesso contratoId={} status={} correlationId={}",
                salvo.id(), salvo.status(), resolvedCorrelationId);
        return salvo;
    }

    @Transactional
    public Contrato registrarEntrega(UUID id, String correlationId, String traceparent) {
        return atualizar(id, correlationId, traceparent, Contrato::registrarEntrega);
    }

    @Transactional
    public Contrato concluir(UUID id, String correlationId, String traceparent) {
        return atualizar(id, correlationId, traceparent, Contrato::concluir);
    }

    @Transactional
    public Contrato cancelar(UUID id, String correlationId, String traceparent) {
        return atualizar(id, correlationId, traceparent, Contrato::cancelar);
    }

    @Transactional(readOnly = true)
    public Contrato buscar(UUID id) {
        log.info("contrato.busca.inicio contratoId={}", id);
        var contrato = repository.buscarPorId(id)
                .orElseThrow(() -> new ContratoNaoEncontradoException("Contrato não encontrado: " + id));
        log.info("contrato.busca.sucesso contratoId={} status={}", id, contrato.status());
        return contrato;
    }

    @Transactional(readOnly = true)
    public List<Contrato> listar() {
        log.info("contrato.listagem.inicio");
        var contratos = repository.listar();
        log.info("contrato.listagem.sucesso quantidade={}", contratos.size());
        return contratos;
    }

    private Contrato atualizar(UUID id, String correlationId, String traceparent,
                               java.util.function.Consumer<Contrato> operacao) {
        String resolvedCorrelationId = resolveCorrelationId(correlationId);
        log.info("contrato.atualizacao.inicio contratoId={} correlationId={}", id, resolvedCorrelationId);
        Contrato contrato = repository.buscarPorId(id)
                .orElseThrow(() -> new ContratoNaoEncontradoException("Contrato não encontrado: " + id));
        operacao.accept(contrato);
        Contrato salvo = repository.salvar(contrato);
        registrarEventos(contrato, resolvedCorrelationId, traceparentAtual(traceparent));
        log.info("contrato.atualizacao.sucesso contratoId={} status={} correlationId={}",
                salvo.id(), salvo.status(), resolvedCorrelationId);
        return salvo;
    }

    private void registrarEventos(Contrato contrato, String correlationId, String traceparent) {
        for (DomainEvent event : contrato.pullDomainEvents()) {
            eventOutbox.registrar(event, correlationId, traceparent);
            log.info("contrato.evento.outbox eventId={} eventType={} contratoId={} correlationId={}",
                    event.eventId(), event.eventType(), event.contratoId(), correlationId);
        }
    }

    private String resolveCorrelationId(String correlationId) {
        return correlationId == null || correlationId.isBlank()
                ? UUID.randomUUID().toString()
                : correlationId.trim();
    }

    private String traceparentAtual(String fallback) {
        Span currentSpan = tracer.currentSpan();
        if (currentSpan == null) {
            return fallback;
        }

        var context = currentSpan.context();
        String traceId = context.traceId();
        if (traceId.length() == 16) {
            traceId = "0000000000000000" + traceId;
        }
        if (traceId.length() != 32 || context.spanId().length() != 16) {
            log.warn("contrato.trace.contexto.invalido traceIdLength={} spanIdLength={}",
                    traceId.length(), context.spanId().length());
            return fallback;
        }
        String sampledFlag = Boolean.TRUE.equals(context.sampled()) ? "01" : "00";
        return "00-" + traceId + "-" + context.spanId() + "-" + sampledFlag;
    }
}
