package br.com.freela.contrato.infrastructure.web;

import br.com.freela.contrato.application.ContratoApplicationService;
import br.com.freela.contrato.application.CriarContratoCommand;
import jakarta.validation.Valid;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;
import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/contratos")
public class ContratoController {
    private static final Logger log = LoggerFactory.getLogger(ContratoController.class);
    private final ContratoApplicationService service;
    public ContratoController(ContratoApplicationService service){this.service=service;}

    @PostMapping @ResponseStatus(HttpStatus.CREATED)
    public ContratoResponse criar(@RequestHeader(value="X-Correlation-Id", required=false) String correlationId,
                                   @RequestHeader(value="traceparent", required=false) String traceparent,
                                   @Valid @RequestBody CriarContratoRequest request) {
        String resolvedCorrelationId = resolveCorrelationId(correlationId);
        log.info("http.contrato.criar.inicio correlationId={}", resolvedCorrelationId);
        var c = service.criar(new CriarContratoCommand(request.clienteId(), request.freelancerId(), request.titulo(), request.valor()), resolvedCorrelationId, traceparent);
        log.info("http.contrato.criar.sucesso correlationId={} contratoId={} status={}", resolvedCorrelationId, c.id(), c.status());
        return ContratoResponse.from(c);
    }

    @PostMapping("/{id}/entrega")
    public ContratoResponse registrarEntrega(
            @RequestHeader(value="X-Correlation-Id", required=false) String correlationId,
            @RequestHeader(value="traceparent", required=false) String traceparent,
            @PathVariable UUID id) {
        String resolvedCorrelationId = resolveCorrelationId(correlationId);
        log.info("http.contrato.entrega.inicio correlationId={} contratoId={}", resolvedCorrelationId, id);
        var contrato = service.registrarEntrega(id, resolvedCorrelationId, traceparent);
        log.info("http.contrato.entrega.sucesso correlationId={} contratoId={} status={}",
                resolvedCorrelationId, id, contrato.status());
        return ContratoResponse.from(contrato);
    }

    @PostMapping("/{id}/conclusao")
    public ContratoResponse concluir(
            @RequestHeader(value="X-Correlation-Id", required=false) String correlationId,
            @RequestHeader(value="traceparent", required=false) String traceparent,
            @PathVariable UUID id) {
        String resolvedCorrelationId = resolveCorrelationId(correlationId);
        log.info("http.contrato.conclusao.inicio correlationId={} contratoId={}", resolvedCorrelationId, id);
        var contrato = service.concluir(id, resolvedCorrelationId, traceparent);
        log.info("http.contrato.conclusao.sucesso correlationId={} contratoId={} status={}",
                resolvedCorrelationId, id, contrato.status());
        return ContratoResponse.from(contrato);
    }

    @PostMapping("/{id}/cancelamento")
    public ContratoResponse cancelar(
            @RequestHeader(value="X-Correlation-Id", required=false) String correlationId,
            @RequestHeader(value="traceparent", required=false) String traceparent,
            @PathVariable UUID id) {
        String resolvedCorrelationId = resolveCorrelationId(correlationId);
        log.info("http.contrato.cancelamento.inicio correlationId={} contratoId={}", resolvedCorrelationId, id);
        var contrato = service.cancelar(id, resolvedCorrelationId, traceparent);
        log.info("http.contrato.cancelamento.sucesso correlationId={} contratoId={} status={}",
                resolvedCorrelationId, id, contrato.status());
        return ContratoResponse.from(contrato);
    }
    @GetMapping("/{id}")
    public ContratoResponse buscar(@RequestHeader(value="X-Correlation-Id", required=false) String correlationId, @PathVariable UUID id) {
        String resolvedCorrelationId = resolveCorrelationId(correlationId);
        log.info("http.contrato.buscar.inicio correlationId={} contratoId={}", resolvedCorrelationId, id);
        var contrato = service.buscar(id);
        log.info("http.contrato.buscar.sucesso correlationId={} contratoId={} status={}",
                resolvedCorrelationId, id, contrato.status());
        return ContratoResponse.from(contrato);
    }
    @GetMapping
    public List<ContratoResponse> listar(@RequestHeader(value="X-Correlation-Id", required=false) String correlationId) {
        String resolvedCorrelationId = resolveCorrelationId(correlationId);
        log.info("http.contrato.listar.inicio correlationId={}", resolvedCorrelationId);
        var contratos = service.listar().stream().map(ContratoResponse::from).toList();
        log.info("http.contrato.listar.sucesso correlationId={} quantidade={}",
                resolvedCorrelationId, contratos.size());
        return contratos;
    }

    private String resolveCorrelationId(String correlationId) {
        return correlationId == null || correlationId.isBlank()
                ? UUID.randomUUID().toString()
                : correlationId.trim();
    }
}
