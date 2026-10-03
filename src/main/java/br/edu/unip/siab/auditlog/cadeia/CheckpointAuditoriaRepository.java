package br.edu.unip.siab.auditlog.cadeia;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface CheckpointAuditoriaRepository extends JpaRepository<CheckpointAuditoria, Long> {

    Optional<CheckpointAuditoria> findTopByTabelaOrderByIdDesc(String tabela);

    List<CheckpointAuditoria> findAllByOrderByIdAsc();
}
