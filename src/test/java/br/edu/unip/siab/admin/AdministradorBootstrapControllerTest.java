package br.edu.unip.siab.admin;

import br.edu.unip.siab.auth.JwtService;
import br.edu.unip.siab.config.SecurityConfig;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.userdetails.User;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.time.LocalDateTime;
import java.util.Collections;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Cobre a exceção de bootstrap do primeiro administrador. Diferente de
 * {@link AdministradorControllerTest} (que desliga os filtros de segurança
 * para testar só o contrato HTTP do endpoint), esta classe mantém a cadeia
 * real do Spring Security — via {@code @Import(SecurityConfig.class)} — para
 * validar que POST /api/admin/administradores realmente fica público
 * enquanto não existe nenhum administrador, e volta a exigir um JWT
 * autenticado assim que existe um.
 */
@WebMvcTest(AdministradorController.class)
@Import(SecurityConfig.class)
class AdministradorBootstrapControllerTest {

    private static final String CORPO = """
            { "username": "primeiro-admin", "senha": "SenhaForte123!" }
            """;

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private AdministradorService administradorService;

    // Dependência do JwtAuthFilter, que entra na cadeia junto com o
    // SecurityConfig importado — precisa existir como bean para o contexto
    // subir, mesmo sem ser usado diretamente nestes testes.
    @MockitoBean
    private JwtService jwtService;

    private Administrador administradorCriado() {
        Administrador administrador = new Administrador();
        administrador.setId(1L);
        administrador.setUsername("primeiro-admin");
        administrador.setSenha("$2a$10$hashQualquerNuncaExpostoNaResposta");
        administrador.setCriadoEm(LocalDateTime.now());
        return administrador;
    }

    @Test
    void permiteCriarSemTokenQuandoNaoExisteAdministrador() throws Exception {
        when(administradorService.existeAdministrador()).thenReturn(false);
        when(administradorService.criar(any())).thenReturn(administradorCriado());

        mockMvc.perform(post("/api/admin/administradores")
                        .contentType("application/json")
                        .content(CORPO))
                .andExpect(status().isOk());
    }

    @Test
    void recusaCriarSemTokenQuandoJaExisteAdministrador() throws Exception {
        when(administradorService.existeAdministrador()).thenReturn(true);

        mockMvc.perform(post("/api/admin/administradores")
                        .contentType("application/json")
                        .content(CORPO))
                .andExpect(status().isForbidden());
    }

    @Test
    void permiteCriarComTokenValidoQuandoJaExisteAdministrador() throws Exception {
        when(administradorService.existeAdministrador()).thenReturn(true);
        when(administradorService.criar(any())).thenReturn(administradorCriado());

        var autenticacaoDeAdminJaLogado = new UsernamePasswordAuthenticationToken(
                new User("admin-logado", "", Collections.emptyList()), null, Collections.emptyList());

        mockMvc.perform(post("/api/admin/administradores")
                        .with(authentication(autenticacaoDeAdminJaLogado))
                        .contentType("application/json")
                        .content(CORPO))
                .andExpect(status().isOk());
    }
}
