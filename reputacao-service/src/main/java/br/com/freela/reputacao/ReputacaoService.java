package br.com.freela.reputacao;
import org.slf4j.*; import org.springframework.stereotype.Service; import org.springframework.transaction.annotation.Transactional; import java.math.BigDecimal; import java.util.UUID;
@Service
public class ReputacaoService {
 private static final Logger log=LoggerFactory.getLogger(ReputacaoService.class); private final ReputacaoRepository repository; private final ContratoConcluidoProcessadoRepository processados;
 public ReputacaoService(ReputacaoRepository r,ContratoConcluidoProcessadoRepository processados){this.repository=r;this.processados=processados;}
 @Transactional public void registrarContratoConcluido(UUID contratoId,UUID freelancerId,BigDecimal valor){
  log.info("reputacao.atualizacao.inicio contratoId={} freelancerId={} valor={}",contratoId,freelancerId,valor);
  var r=repository.findById(freelancerId).orElseGet(()->new ReputacaoFreelancer(freelancerId)); r.registrarContrato(valor); repository.save(r);
  log.info("reputacao.atualizacao.sucesso contratoId={} freelancerId={} contratosConcluidos={} valorTotal={}",contratoId,freelancerId,r.contratosConcluidos,r.valorTotal);
 }
 @Transactional public void registrarContratoConcluido(UUID eventId,UUID contratoId,UUID freelancerId,BigDecimal valor){
  log.info("reputacao.evento.inicio eventId={} contratoId={} freelancerId={} valor={}",eventId,contratoId,freelancerId,valor);
  if(processados.existsById(eventId)){
   log.info("reputacao.evento.duplicado eventId={} contratoId={}",eventId,contratoId);
   return;
  }
  var r=repository.findById(freelancerId).orElseGet(()->new ReputacaoFreelancer(freelancerId));
  r.registrarContrato(valor);
  repository.save(r);
  processados.save(new ContratoConcluidoProcessado(eventId));
  log.info("reputacao.evento.sucesso eventId={} contratoId={} freelancerId={} contratosConcluidos={} valorTotal={}",
   eventId,contratoId,freelancerId,r.contratosConcluidos,r.valorTotal);
 }
}
