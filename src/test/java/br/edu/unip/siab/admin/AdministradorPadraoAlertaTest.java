package br.edu.unip.siab.admin;

import org.junit.jupiter.api.Test;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Cobre o aviso de boot para bancos que ainda têm o administrador semeado
 * pelas versões antigas do data.sql.
 */
class AdministradorPadraoAlertaTest {

    private final AdministradorRepository administradorRepository = mock(AdministradorRepository.class);
    private final PasswordEncoder passwordEncoder = new BCryptPasswordEncoder();
    private final AdministradorPadraoAlerta alerta = new AdministradorPadraoAlerta(administradorRepository, passwordEncoder);

    private Administrador adminComSenha(String senha) {
        Administrador administrador = new Administrador();
        administrador.setUsername("admin");
        administrador.setSenha(passwordEncoder.encode(senha));
        return administrador;
    }

    @Test
    void detectaSenhaAntigaDoSeed() {
        when(administradorRepository.findByUsername("admin"))
                .thenReturn(Optional.of(adminComSenha("TrocarSenha123!")));
        assertThat(alerta.usaCredencialAntiga()).isTrue();
    }

    @Test
    void ignoraAdminComSenhaTrocada() {
        when(administradorRepository.findByUsername("admin"))
                .thenReturn(Optional.of(adminComSenha("OutraSenhaForte!")));
        assertThat(alerta.usaCredencialAntiga()).isFalse();
    }

    @Test
    void ignoraQuandoNaoExisteAdmin() {
        when(administradorRepository.findByUsername("admin")).thenReturn(Optional.empty());
        assertThat(alerta.usaCredencialAntiga()).isFalse();
    }
}
