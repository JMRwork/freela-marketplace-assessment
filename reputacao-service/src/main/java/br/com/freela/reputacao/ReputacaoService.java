package br.com.freela.reputacao;
import org.slf4j.*; import org.springframework.stereotype.Service; import org.springframework.transaction.annotation.Transactional; import java.math.BigDecimal; import java.util.UUID;
@Service
public class ReputacaoService {
 private static final Logger log=LoggerFactory.getLogger(ReputacaoService.class); private final ReputacaoRepository repository; private final ContratoConcluidoProcessadoRepository processados;
 public ReputacaoService(ReputacaoRepository r,ContratoConcluidoProcessadoRepository processados){this.repository=r;this.processados=processados;}
 @Transactional public void registrarContratoConcluido(UUID eventId,UUID contratoId,UUID freelancerId,BigDecimal valor,String correlationId){
  log.info("reputacao.evento.inicio eventId={} contratoId={} freelancerId={} correlationId={}",eventId,contratoId,freelancerId,correlationId);
  if(processados.existsById(eventId)){
   log.info("reputacao.evento.duplicado eventId={} contratoId={} correlationId={}",eventId,contratoId,correlationId);
   return;
  }
  processados.saveAndFlush(new ContratoConcluidoProcessado(eventId));
  var r=repository.findById(freelancerId).orElseGet(()->new ReputacaoFreelancer(freelancerId));
  r.registrarContrato(valor);
  repository.save(r);
  log.info("reputacao.evento.sucesso eventId={} contratoId={} freelancerId={} contratosConcluidos={} correlationId={}",
   eventId,contratoId,freelancerId,r.contratosConcluidos,correlationId);
 }
}
