package br.edu.unip.siab.auditlog;

import java.time.LocalDateTime;

public record AccessLogResponse(
        Long id,
        Long usuarioId,
        String nomeUsuario,
        String resultado,
        Double similaridade,
        String motivo,
        String terminal,
        String ip,
        LocalDateTime dataHora
) {
    public static AccessLogResponse from(AccessLog log) {
        return new AccessLogResponse(
                log.getId(),
                log.getUsuario() != null ? log.getUsuario().getId() : log.getUsuarioRef(),
                nome(log),
                log.getResultado().name(),
                log.getSimilaridade(),
                log.getMotivo(),
                log.getTerminalNome(),
                log.getIp(),
                log.getDataHora()
        );
    }

    private static String nome(AccessLog log) {
        if (log.getUsuario() != null) {
            return log.getUsuario().getNome();
        }
        return log.getUsuarioRef() != null ? "Usuário excluído (#" + log.getUsuarioRef() + ")" : "Não identificado";
    }
}
