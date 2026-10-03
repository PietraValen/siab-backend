package br.edu.unip.siab.terminal.dto;

/**
 * Resposta do cadastro: a única vez em que a chave é mostrada. O admin
 * copia o id e a chave para a tela de pareamento do quiosque /scan.
 */
public record TerminalCriadoResponse(TerminalResponse terminal, String chave) {
}
