package br.com.freela.reputacao;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.util.UUID;

@Entity
@Table(name = "reputacao_eventos_processados")
class ContratoConcluidoProcessado {
    @Id
    private UUID eventId;

    protected ContratoConcluidoProcessado() {}

    ContratoConcluidoProcessado(UUID eventId) {
        this.eventId = eventId;
    }
}
