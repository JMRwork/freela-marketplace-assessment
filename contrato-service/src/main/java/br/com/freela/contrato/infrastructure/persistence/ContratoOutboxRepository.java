package br.com.freela.contrato.infrastructure.persistence;

import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
import java.util.UUID;

public interface ContratoOutboxRepository extends JpaRepository<ContratoOutboxEntity, UUID> {
    List<ContratoOutboxEntity> findTop100ByPublishedAtIsNullOrderByCreatedAtAsc();
}
