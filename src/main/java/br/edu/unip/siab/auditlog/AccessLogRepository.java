package br.edu.unip.siab.auditlog;

import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDateTime;
import java.util.List;

public interface AccessLogRepository extends JpaRepository<AccessLog, Long> {

    List<AccessLog> findByUsuarioIdOrderByDataHoraDesc(Long usuarioId);

    List<AccessLog> findAllByOrderByDataHoraDesc();

    List<AccessLog> findByDataHoraBetweenOrderByDataHoraDesc(LocalDateTime inicio, LocalDateTime fim);

    List<AccessLog> findByUsuarioIdAndDataHoraBetweenOrderByDataHoraDesc(Long usuarioId, LocalDateTime inicio, LocalDateTime fim);
}
