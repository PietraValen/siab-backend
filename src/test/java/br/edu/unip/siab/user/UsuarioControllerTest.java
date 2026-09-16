package br.edu.unip.siab.user;

import br.edu.unip.siab.accesslevel.NivelAcesso;
import br.edu.unip.siab.auth.JwtAuthFilter;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * Valida o contrato HTTP do módulo "user-management" (RF06) sem precisar
 * de banco real — a camada de serviço é mockada.
 * <p>
 * {@code addFilters = false} desliga a cadeia de filtros do Spring Security
 * (incluindo o {@link JwtAuthFilter}, mockado só para satisfazer a injeção
 * de dependência do {@code SecurityConfig} neste contexto fatiado). Sem
 * isso, o mock do filtro nunca chama {@code filterChain.doFilter(...)} e a
 * requisição nunca chega ao controller — {@code @WithMockUser} já é
 * suficiente para simular o usuário autenticado no contrato testado aqui.
 */
@WebMvcTest(UsuarioController.class)
@AutoConfigureMockMvc(addFilters = false)
class UsuarioControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private UsuarioService usuarioService;

    // O filtro JWT real seria carregado pelo contexto de segurança; como
    // este teste foca no contrato HTTP, mockamos para evitar configurar
    // toda a cadeia de autenticação aqui.
    @MockitoBean
    private JwtAuthFilter jwtAuthFilter;

    private Usuario usuarioDeTeste() {
        NivelAcesso nivel = new NivelAcesso();
        nivel.setId(1L);
        nivel.setNome("Acesso Geral");

        Usuario usuario = new Usuario();
        usuario.setId(1L);
        usuario.setNome("Beatriz Novais");
        usuario.setCargo("Analista");
        usuario.setNivelAcesso(nivel);
        return usuario;
    }

    @Test
    @WithMockUser
    void listarRetornaUsuariosCadastrados() throws Exception {
        when(usuarioService.listarTodos()).thenReturn(List.of(usuarioDeTeste()));

        mockMvc.perform(get("/api/admin/usuarios"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].nome").value("Beatriz Novais"))
                .andExpect(jsonPath("$[0].nivelAcesso").value("Acesso Geral"));
    }

    @Test
    @WithMockUser
    void criarComDadosInvalidosRetorna400() throws Exception {
        // "nome" em branco deve falhar na validação (@NotBlank do UsuarioRequest)
        String corpoInvalido = """
                { "nome": "", "cargo": "Analista", "nivelAcessoId": 1 }
                """;

        mockMvc.perform(post("/api/admin/usuarios")
                        .contentType("application/json")
                        .content(corpoInvalido))
                .andExpect(status().isBadRequest());
    }
}
