package br.edu.unip.siab.auditlog;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

public interface AccessLogRepository extends JpaRepository<AccessLog, Long> {

    List<AccessLog> findByUsuarioIdOrderByDataHoraDesc(Long usuarioId);

    List<AccessLog> findAllByOrderByDataHoraDesc();

    List<AccessLog> findByDataHoraBetweenOrderByDataHoraDesc(LocalDateTime inicio, LocalDateTime fim);

    List<AccessLog> findByUsuarioIdAndDataHoraBetweenOrderByDataHoraDesc(Long usuarioId, LocalDateTime inicio, LocalDateTime fim);

    Optional<AccessLog> findTopByOrderByIdDesc();

    List<AccessLog> findAllByOrderByIdAsc();

    /** Exclusão de usuário (LGPD): solta a FK, mas mantém {@code usuario_ref} e a cadeia intactos. */
    @Modifying
    @Query("UPDATE AccessLog l SET l.usuario = null WHERE l.usuario.id = :usuarioId")
    int desvincularUsuario(@Param("usuarioId") Long usuarioId);
}
