package br.com.freela.notificacao;
import jakarta.persistence.*; import java.time.Instant; import java.util.UUID;
@Entity @Table(name="notificacoes", uniqueConstraints=@UniqueConstraint(columnNames={"eventId", "destinatarioId"}))
class Notificacao {
 @Id UUID id; UUID eventId; UUID contratoId; UUID destinatarioId; String tipo; @Column(length=500) String mensagem; Instant criadaEm;
 protected Notificacao(){} Notificacao(UUID contratoId, UUID destinatarioId, String tipo, String mensagem){this.id=UUID.randomUUID();this.contratoId=contratoId;this.destinatarioId=destinatarioId;this.tipo=tipo;this.mensagem=mensagem;this.criadaEm=Instant.now();}
 Notificacao(UUID eventId, UUID contratoId, UUID destinatarioId, String tipo, String mensagem){this(contratoId,destinatarioId,tipo,mensagem);this.eventId=eventId;}
}
