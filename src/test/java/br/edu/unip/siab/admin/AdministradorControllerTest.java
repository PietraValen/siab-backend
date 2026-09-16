package br.edu.unip.siab.admin;

import br.edu.unip.siab.admin.dto.AdministradorRequest;
import br.edu.unip.siab.auth.JwtAuthFilter;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.time.LocalDateTime;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * Valida o contrato HTTP de criação de administradores — em especial, que a
 * senha (texto puro ou hash) nunca aparece na resposta da API, e que um
 * username duplicado é rejeitado.
 */
@WebMvcTest(AdministradorController.class)
@AutoConfigureMockMvc(addFilters = false)
class AdministradorControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private AdministradorService administradorService;

    @MockitoBean
    private JwtAuthFilter jwtAuthFilter;

    private Administrador administradorCriado() {
        Administrador administrador = new Administrador();
        administrador.setId(1L);
        administrador.setUsername("novo-admin");
        administrador.setSenha("$2a$10$hashQualquerNuncaExpostoNaResposta");
        administrador.setCriadoEm(LocalDateTime.now());
        return administrador;
    }

    @Test
    @WithMockUser
    void criarNaoExpoeSenhaNaResposta() throws Exception {
        when(administradorService.criar(any(AdministradorRequest.class))).thenReturn(administradorCriado());

        String corpo = """
                { "username": "novo-admin", "senha": "SenhaForte123!" }
                """;

        mockMvc.perform(post("/api/admin/administradores")
                        .contentType("application/json")
                        .content(corpo))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.username").value("novo-admin"))
                .andExpect(jsonPath("$.senha").doesNotExist())
                .andExpect(content().string(org.hamcrest.Matchers.not(
                        org.hamcrest.Matchers.containsString("SenhaForte123!"))));
    }

    @Test
    @WithMockUser
    void criarComUsernameDuplicadoFalha() throws Exception {
        when(administradorService.criar(any(AdministradorRequest.class)))
                .thenThrow(new UsernameJaCadastradoException("novo-admin"));

        String corpo = """
                { "username": "novo-admin", "senha": "SenhaForte123!" }
                """;

        mockMvc.perform(post("/api/admin/administradores")
                        .contentType("application/json")
                        .content(corpo))
                .andExpect(status().isConflict());
    }

    @Test
    @WithMockUser
    void criarComSenhaCurtaRetorna400() throws Exception {
        String corpo = """
                { "username": "novo-admin", "senha": "curta" }
                """;

        mockMvc.perform(post("/api/admin/administradores")
                        .contentType("application/json")
                        .content(corpo))
                .andExpect(status().isBadRequest());
    }
}
