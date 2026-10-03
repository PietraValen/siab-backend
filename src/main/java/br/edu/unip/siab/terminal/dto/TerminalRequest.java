package br.edu.unip.siab.terminal.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record TerminalRequest(
        @NotBlank @Size(max = 100) String nome,
        @NotNull Long nivelExigidoId
) {
}
