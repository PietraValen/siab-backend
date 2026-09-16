package br.edu.unip.siab.admin.dto;

import br.edu.unip.siab.admin.Administrador;

import java.time.LocalDateTime;

/**
 * Nunca expõe a senha (nem o hash) — apenas os dados públicos do cadastro.
 */
public record AdministradorResponse(
        Long id,
        String username,
        LocalDateTime criadoEm
) {
    public static AdministradorResponse from(Administrador administrador) {
        return new AdministradorResponse(
                administrador.getId(),
                administrador.getUsername(),
                administrador.getCriadoEm()
        );
    }
}
