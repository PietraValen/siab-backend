package br.edu.unip.siab.auth;

import br.edu.unip.siab.admin.Administrador;
import br.edu.unip.siab.admin.AdministradorRepository;
import br.edu.unip.siab.admin.AdministradorService;
import br.edu.unip.siab.admin.AdministradorUserDetailsService;
import br.edu.unip.siab.auditlog.AuditoriaAdminService;
import br.edu.unip.siab.config.SecurityConfig;
import br.edu.unip.siab.security.BloqueioLoginService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.Optional;

import static org.hamcrest.Matchers.allOf;
import static org.hamcrest.Matchers.containsString;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
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
@Import({SecurityConfig.class, AdministradorUserDetailsService.class, AdministradorService.class,
        TotpService.class, BloqueioLoginService.class, CookieDeSessao.class})
class AuthControllerTest {

    private static final String SENHA_CORRETA = "SenhaCorreta123!";

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private AdministradorRepository administradorRepository;

    @MockitoBean
    private JwtService jwtService;

    @MockitoBean
    private AuditoriaAdminService auditoriaAdminService;

    @Autowired
    private TotpService totpService;

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
                .andExpect(jsonPath("$.tokenType").value("Bearer"))
                // O painel usa o cookie: HttpOnly (fora do alcance de XSS), Secure e SameSite=Strict.
                .andExpect(header().string("Set-Cookie", allOf(
                        containsString("SIAB_TOKEN=token-fake-de-teste"),
                        containsString("HttpOnly"),
                        containsString("Secure"),
                        containsString("SameSite=Strict"))));

        verify(auditoriaAdminService).registrarComo("admin", "LOGIN", null);
    }

    @Test
    void bloqueiaUsernameDepoisDeCincoSenhasErradas() throws Exception {
        when(administradorRepository.findByUsername("alvo"))
                .thenReturn(Optional.of(administradorDeTeste()));

        String errado = """
                { "username": "alvo", "password": "chute-errado" }
                """;
        for (int i = 0; i < 5; i++) {
            mockMvc.perform(post("/api/auth/login").contentType("application/json").content(errado))
                    .andExpect(status().is4xxClientError());
        }

        // Nem a senha certa entra enquanto o bloqueio durar.
        String certo = """
                { "username": "alvo", "password": "%s" }
                """.formatted(SENHA_CORRETA);
        mockMvc.perform(post("/api/auth/login").contentType("application/json").content(certo))
                .andExpect(status().isTooManyRequests())
                .andExpect(header().exists("Retry-After"));
    }

    @Test
    void adminComMfaAtivoPrecisaDoCodigo() throws Exception {
        Administrador comMfa = administradorDeTeste();
        comMfa.setUsername("com-mfa");
        String segredo = totpService.gerarSegredo();
        comMfa.setTotpSegredo(segredo);
        comMfa.setTotpAtivo(true);
        when(administradorRepository.findByUsername("com-mfa")).thenReturn(Optional.of(comMfa));
        when(administradorRepository.save(org.mockito.ArgumentMatchers.any())).thenAnswer(i -> i.getArgument(0));
        when(jwtService.generateToken(anyString())).thenReturn("token-com-mfa");

        String semCodigo = """
                { "username": "com-mfa", "password": "%s" }
                """.formatted(SENHA_CORRETA);
        mockMvc.perform(post("/api/auth/login").contentType("application/json").content(semCodigo))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.mfaNecessario").value(true));

        long passo = java.time.Instant.now().getEpochSecond() / 30;
        String codigo = totpService.codigoNoPasso(TotpService.deBase32(segredo), passo);
        String comCodigo = """
                { "username": "com-mfa", "password": "%s", "codigoMfa": "%s" }
                """.formatted(SENHA_CORRETA, codigo);
        mockMvc.perform(post("/api/auth/login").contentType("application/json").content(comCodigo))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.token").value("token-com-mfa"));

        // O mesmo código não serve duas vezes (anti-replay do TOTP).
        mockMvc.perform(post("/api/auth/login").contentType("application/json").content(comCodigo))
                .andExpect(status().isUnauthorized());
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
