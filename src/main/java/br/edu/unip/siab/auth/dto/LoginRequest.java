package br.edu.unip.siab.auth.dto;

import jakarta.validation.constraints.NotBlank;

/**
 * @param codigoMfa código de 6 dígitos do app autenticador; obrigatório só
 *                  para administradores com MFA ativo.
 */
public record LoginRequest(
        @NotBlank String username,
        @NotBlank String password,
        String codigoMfa
) {
}
