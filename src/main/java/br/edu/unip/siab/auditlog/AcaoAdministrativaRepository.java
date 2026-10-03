package br.edu.unip.siab.auditlog;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface AcaoAdministrativaRepository extends JpaRepository<AcaoAdministrativa, Long> {

    Optional<AcaoAdministrativa> findTopByOrderByIdDesc();

    List<AcaoAdministrativa> findAllByOrderByIdAsc();

    List<AcaoAdministrativa> findTop200ByOrderByIdDesc();
}
