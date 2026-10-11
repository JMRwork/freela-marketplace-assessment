package br.com.freela.notificacao;
import org.slf4j.*; import org.springframework.stereotype.Service; import org.springframework.transaction.annotation.Transactional; import java.util.UUID;
@Service
public class NotificacaoService {
 private static final Logger log=LoggerFactory.getLogger(NotificacaoService.class); private final NotificacaoRepository repository;
 public NotificacaoService(NotificacaoRepository r){this.repository=r;}
 @Transactional public void registrar(UUID contratoId, UUID destinatarioId, String tipo, String mensagem){
  log.info("notificacao.registro.inicio contratoId={} destinatarioId={} tipo={}",contratoId,destinatarioId,tipo);
  var n=repository.save(new Notificacao(contratoId,destinatarioId,tipo,mensagem));
  log.info("notificacao.registro.sucesso notificacaoId={} contratoId={} destinatarioId={}",n.id,contratoId,destinatarioId);
 }
 @Transactional public void registrarEvento(UUID eventId, UUID contratoId, UUID destinatarioId, String tipo, String mensagem){
  log.info("notificacao.evento.inicio eventId={} contratoId={} destinatarioId={} tipo={}",eventId,contratoId,destinatarioId,tipo);
  if(repository.existsByEventIdAndDestinatarioId(eventId,destinatarioId)){
   log.info("notificacao.evento.duplicado eventId={} destinatarioId={}",eventId,destinatarioId);
   return;
  }
  var n=repository.saveAndFlush(new Notificacao(eventId,contratoId,destinatarioId,tipo,mensagem));
  log.info("notificacao.evento.sucesso eventId={} notificacaoId={} contratoId={} destinatarioId={}",eventId,n.id,contratoId,destinatarioId);
 }
}
