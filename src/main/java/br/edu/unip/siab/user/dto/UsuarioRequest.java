package br.edu.unip.siab.user.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;

/**
 * @param pin opcional; 4 a 8 dígitos. Exigido nas portas de nível Ministro
 *            (segundo fator). Na atualização, nulo mantém o PIN atual.
 */
public record UsuarioRequest(
        @NotBlank String nome,
        String cargo,
        @NotNull Long nivelAcessoId,
        @Pattern(regexp = "\\d{4,8}", message = "O PIN deve ter de 4 a 8 dígitos numéricos.") String pin
) {}
