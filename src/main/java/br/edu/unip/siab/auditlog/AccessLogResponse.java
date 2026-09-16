package br.edu.unip.siab.auditlog;

import java.time.LocalDateTime;

public record AccessLogResponse(
        Long id,
        Long usuarioId,
        String nomeUsuario,
        String resultado,
        Double similaridade,
        LocalDateTime dataHora
) {
    public static AccessLogResponse from(AccessLog log) {
        return new AccessLogResponse(
                log.getId(),
                log.getUsuario() != null ? log.getUsuario().getId() : null,
                log.getUsuario() != null ? log.getUsuario().getNome() : "Não identificado",
                log.getResultado().name(),
                log.getSimilaridade(),
                log.getDataHora()
        );
    }
}
