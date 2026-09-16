package br.edu.unip.siab.user.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public record UsuarioRequest(
        @NotBlank String nome,
        String cargo,
        @NotNull Long nivelAcessoId
) {
}
