package br.edu.unip.siab.admin;

import lombok.RequiredArgsConstructor;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;

/**
 * Ponte entre o Spring Security e a tabela "administradores". Substitui o
 * antigo InMemoryUserDetailsManager (usuário fixo via variável de
 * ambiente) que existia em SecurityConfig.
 */
@Service
@RequiredArgsConstructor
public class AdministradorUserDetailsService implements UserDetailsService {

    private final AdministradorRepository administradorRepository;

    @Override
    public UserDetails loadUserByUsername(String username) throws UsernameNotFoundException {
        Administrador administrador = administradorRepository.findByUsername(username)
                .orElseThrow(() -> new UsernameNotFoundException("Administrador não encontrado: " + username));

        return User.builder()
                .username(administrador.getUsername())
                .password(administrador.getSenha())
                .roles("ADMIN")
                .build();
    }
}
