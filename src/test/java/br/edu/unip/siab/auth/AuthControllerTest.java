package br.edu.unip.siab.auth;

import br.edu.unip.siab.admin.Administrador;
import br.edu.unip.siab.admin.AdministradorRepository;
import br.edu.unip.siab.admin.AdministradorService;
import br.edu.unip.siab.admin.AdministradorUserDetailsService;
import br.edu.unip.siab.config.SecurityConfig;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.Optional;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Valida o login administrativo (módulo "auth") através da cadeia real do
 * Spring Security (AuthenticationManager -> AdministradorUserDetailsService
 * -> BCryptPasswordEncoder), com o repositório mockado (sem banco real).
 * {@code @Import} traz o AdministradorUserDetailsService real para dentro
 * da fatia @WebMvcTest — ele não é um @Controller/@Filter, então não entra
 * automaticamente. O JwtService é mockado porque a emissão do token em si
 * não é o que está sendo verificado aqui, só a autenticação.
 * <p>
 * SecurityConfig também precisa ser importado explicitamente: o Boot só
 * detecta automaticamente configurações de segurança no estilo antigo
 * (WebSecurityConfigurer); o estilo atual, baseado em @Bean
 * SecurityFilterChain, não é auto-incluído na fatia @WebMvcTest — sem ele,
 * não existe bean de AuthenticationManager e o AuthController (que depende
 * dele no construtor) nem instancia.
 */
@WebMvcTest(AuthController.class)
@Import({SecurityConfig.class, AdministradorUserDetailsService.class, AdministradorService.class})
class AuthControllerTest {

    private static final String SENHA_CORRETA = "SenhaCorreta123!";

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private AdministradorRepository administradorRepository;

    @MockitoBean
    private JwtService jwtService;

    private Administrador administradorDeTeste() {
        Administrador administrador = new Administrador();
        administrador.setId(1L);
        administrador.setUsername("admin");
        administrador.setSenha(new BCryptPasswordEncoder().encode(SENHA_CORRETA));
        return administrador;
    }

    @Test
    void loginComCredenciaisCorretasRetornaTokenComStatus200() throws Exception {
        when(administradorRepository.findByUsername("admin"))
                .thenReturn(Optional.of(administradorDeTeste()));
        when(jwtService.generateToken("admin")).thenReturn("token-fake-de-teste");

        String corpo = """
                { "username": "admin", "password": "%s" }
                """.formatted(SENHA_CORRETA);

        mockMvc.perform(post("/api/auth/login")
                        .contentType("application/json")
                        .content(corpo))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.token").value("token-fake-de-teste"))
                .andExpect(jsonPath("$.tokenType").value("Bearer"));
    }

    @Test
    void loginComSenhaErradaNaoAutoriza() throws Exception {
        when(administradorRepository.findByUsername("admin"))
                .thenReturn(Optional.of(administradorDeTeste()));

        String corpo = """
                { "username": "admin", "password": "senha-errada-qualquer" }
                """;

        mockMvc.perform(post("/api/auth/login")
                        .contentType("application/json")
                        .content(corpo))
                .andExpect(status().is4xxClientError());
    }

    @Test
    void loginComUsuarioInexistenteNaoAutoriza() throws Exception {
        when(administradorRepository.findByUsername("fantasma"))
                .thenReturn(Optional.empty());

        String corpo = """
                { "username": "fantasma", "password": "qualquer-coisa" }
                """;

        mockMvc.perform(post("/api/auth/login")
                        .contentType("application/json")
                        .content(corpo))
                .andExpect(status().is4xxClientError());
    }

    @Test
    void existeAdministradorRetornaFalseQuandoTabelaVazia() throws Exception {
        when(administradorRepository.count()).thenReturn(0L);

        mockMvc.perform(get("/api/auth/existe-administrador"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.existe").value(false));
    }

    @Test
    void existeAdministradorRetornaTrueQuandoJaHaAdministrador() throws Exception {
        when(administradorRepository.count()).thenReturn(1L);

        mockMvc.perform(get("/api/auth/existe-administrador"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.existe").value(true));
    }
}
