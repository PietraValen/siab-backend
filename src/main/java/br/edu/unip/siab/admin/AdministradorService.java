package br.edu.unip.siab.admin;

import br.edu.unip.siab.admin.dto.AdministradorRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

/**
 * Módulo "auth" (seção 5.2 do escopo): gestão dos administradores com
 * acesso ao painel. A senha nunca é persistida em texto puro — é sempre
 * hasheada com o {@link PasswordEncoder} (BCrypt) já configurado em
 * SecurityConfig antes de ser salva.
 */
@Service
@RequiredArgsConstructor
public class AdministradorService {

    private final AdministradorRepository administradorRepository;
    private final PasswordEncoder passwordEncoder;

    public Administrador criar(AdministradorRequest request) {
        if (administradorRepository.existsByUsername(request.username())) {
            throw new UsernameJaCadastradoException(request.username());
        }

        Administrador administrador = new Administrador();
        administrador.setUsername(request.username());
        administrador.setSenha(passwordEncoder.encode(request.senha()));

        return administradorRepository.save(administrador);
    }

    /**
     * Usado tanto pelo endpoint público de bootstrap (GET
     * /api/auth/existe-administrador) quanto pelo próprio
     * {@link AdministradorController}, que dispensa JWT na criação apenas
     * enquanto isto for {@code false}.
     */
    public boolean existeAdministrador() {
        return administradorRepository.count() > 0;
    }
}
