package br.edu.unip.siab.terminal.dto;

import java.time.Instant;

/**
 * Desafio de uso único + o que o quiosque precisa saber da porta onde está
 * (nível exigido e se pede PIN). Não traz nada sensível.
 */
public record DesafioResponse(
        String nonce,
        Instant expiraEm,
        String terminal,
        Long nivelExigidoId,
        String nivelExigido,
        boolean exigePin
) {
}
