package br.edu.unip.siab.user.dto;

import br.edu.unip.siab.user.Usuario;

import java.time.LocalDateTime;

public record UsuarioResponse(
        Long id,
        String nome,
        String cargo,
        String nivelAcesso,
        LocalDateTime criadoEm
) {
    public static UsuarioResponse from(Usuario u) {
        return new UsuarioResponse(
                u.getId(),
                u.getNome(),
                u.getCargo(),
                u.getNivelAcesso().getNome(),
                u.getCriadoEm()
        );
    }
}
