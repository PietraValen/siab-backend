package br.edu.unip.siab.terminal.dto;

import br.edu.unip.siab.terminal.Terminal;

import java.time.LocalDateTime;

/** Dados públicos de um terminal — nunca inclui a chave. */
public record TerminalResponse(
        Long id,
        String nome,
        Long nivelExigidoId,
        String nivelExigido,
        boolean ativo,
        LocalDateTime criadoEm,
        LocalDateTime ultimoUsoEm
) {
    public static TerminalResponse from(Terminal t) {
        return new TerminalResponse(t.getId(), t.getNome(), t.getNivelExigido().getId(),
                t.getNivelExigido().getNome(), t.isAtivo(), t.getCriadoEm(), t.getUltimoUsoEm());
    }
}
