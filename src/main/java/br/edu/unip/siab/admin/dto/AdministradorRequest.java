package br.edu.unip.siab.admin.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record AdministradorRequest(
        @NotBlank String username,
        @NotBlank @Size(min = 8, message = "senha deve ter no mínimo 8 caracteres") String senha
) {
}
