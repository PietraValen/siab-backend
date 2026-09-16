package br.edu.unip.siab.admin;

import br.edu.unip.siab.admin.dto.AdministradorRequest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * Cobre a criação de administradores (módulo "auth"): unicidade de
 * username e hashing da senha antes de persistir — nunca em texto puro.
 */
@ExtendWith(MockitoExtension.class)
class AdministradorServiceTest {

    @Mock
    private AdministradorRepository administradorRepository;

    private final PasswordEncoder passwordEncoder = new BCryptPasswordEncoder();

    private AdministradorService service;

    private AdministradorService novoServico() {
        return new AdministradorService(administradorRepository, passwordEncoder);
    }

    @Test
    void criaAdministradorComSenhaHasheada() {
        service = novoServico();
        when(administradorRepository.existsByUsername("admin")).thenReturn(false);
        when(administradorRepository.save(any(Administrador.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        Administrador criado = service.criar(new AdministradorRequest("admin", "SenhaForte123!"));

        assertThat(criado.getUsername()).isEqualTo("admin");
        assertThat(criado.getSenha()).isNotEqualTo("SenhaForte123!");
        assertThat(passwordEncoder.matches("SenhaForte123!", criado.getSenha())).isTrue();
    }

    @Test
    void naoPermiteCriarComUsernameJaCadastrado() {
        service = novoServico();
        when(administradorRepository.existsByUsername("admin")).thenReturn(true);

        assertThatThrownBy(() -> service.criar(new AdministradorRequest("admin", "SenhaForte123!")))
                .isInstanceOf(UsernameJaCadastradoException.class);

        verify(administradorRepository, never()).save(any());
    }
}
