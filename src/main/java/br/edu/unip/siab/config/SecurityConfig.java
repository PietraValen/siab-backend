package br.edu.unip.siab.config;

import br.edu.unip.siab.auth.CookieDeSessao;
import br.edu.unip.siab.auth.JwtAuthFilter;
import br.edu.unip.siab.security.RateLimitFilter;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.config.annotation.authentication.configuration.AuthenticationConfiguration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.security.web.csrf.CookieCsrfTokenRepository;
import org.springframework.security.web.csrf.CsrfTokenRequestAttributeHandler;
import org.springframework.security.web.header.writers.ReferrerPolicyHeaderWriter;
import org.springframework.web.cors.CorsConfigurationSource;

import java.util.Arrays;
import java.util.Set;

/**
 * Regras de segurança da API.
 * <p>
 * - /api/auth/**                     -> público (login, logout, token CSRF e bootstrap do painel)
 * - /api/recognition/**               -> sem JWT, mas cada tentativa precisa vir
 *   assinada por um terminal cadastrado (ver RecognitionController)
 * - POST /api/admin/administradores   -> público na camada de filtro; o
 *   {@link br.edu.unip.siab.admin.AdministradorController} decide na marra
 *   se exige um JWT autenticado, dependendo de já existir ou não algum
 *   administrador cadastrado (bootstrap do primeiro admin do sistema).
 * - /api/admin/**  (restante) e /api/enrollment/** -> exigem JWT válido
 * <p>
 * A API é stateless (RNF03 do escopo): nenhuma sessão é mantida no servidor,
 * cada requisição autenticada carrega seu próprio token JWT — no cookie
 * HttpOnly do painel ou no header Authorization.
 * <p>
 * CSRF (seção 3 do roteiro de segurança): só faz sentido quando o navegador
 * anexa a credencial sozinho, ou seja, quando a requisição traz o cookie
 * {@value CookieDeSessao#NOME}. Nesses casos, POST/PUT/DELETE exigem o
 * header X-XSRF-TOKEN (obtido em GET /api/auth/csrf). Requisições com
 * Bearer explícito (Swagger, Postman) e os terminais (HMAC próprio) não
 * carregam credencial automática e ficam de fora.
 */
@Configuration
@EnableWebSecurity
@RequiredArgsConstructor
public class SecurityConfig {

    private static final Set<String> METODOS_SEGUROS = Set.of("GET", "HEAD", "OPTIONS", "TRACE");

    private final JwtAuthFilter jwtAuthFilter;
    private final RateLimitFilter rateLimitFilter;
    private final CorsConfigurationSource corsConfigurationSource;

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        CookieCsrfTokenRepository repositorioCsrf = CookieCsrfTokenRepository.withHttpOnlyFalse();
        repositorioCsrf.setCookieCustomizer(cookie -> cookie.sameSite("Strict").path("/"));

        http
            .cors(cors -> cors.configurationSource(corsConfigurationSource))
            .csrf(csrf -> csrf
                .csrfTokenRepository(repositorioCsrf)
                // Handler "plano" (sem a máscara XOR do padrão): o front lê o
                // token de GET /api/auth/csrf e o devolve no header como está.
                .csrfTokenRequestHandler(new CsrfTokenRequestAttributeHandler())
                .requireCsrfProtectionMatcher(SecurityConfig::exigeCsrf))
            .sessionManagement(sm -> sm.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
            .headers(headers -> headers
                .frameOptions(frame -> frame.deny())
                .referrerPolicy(referrer -> referrer.policy(ReferrerPolicyHeaderWriter.ReferrerPolicy.NO_REFERRER)))
            .authorizeHttpRequests(auth -> auth
                .requestMatchers(HttpMethod.OPTIONS, "/**").permitAll()
                .requestMatchers("/api/auth/**").permitAll()
                .requestMatchers("/api/recognition/**").permitAll()
                .requestMatchers(HttpMethod.POST, "/api/admin/administradores").permitAll()
                .requestMatchers("/swagger-ui.html", "/swagger-ui/**", "/v3/api-docs/**").permitAll()
                .requestMatchers("/actuator/health").permitAll()
                // Sem isso, qualquer exceção não tratada (mesmo em endpoint público)
                // faz o Spring Boot redespachar a requisição para /error, que por
                // sua vez passa de novo pela cadeia de segurança; como só existe
                // autenticação anônima nesse redespacho, ela falha em
                // anyRequest().authenticated() e o cliente recebe um 403 confuso
                // no lugar do erro real (400/404/500). Ver JwtAuthFilter para o
                // outro lado do mesmo problema (exceção do próprio parsing do JWT).
                .requestMatchers("/error").permitAll()
                .anyRequest().authenticated()
            )
            .addFilterBefore(jwtAuthFilter, UsernamePasswordAuthenticationFilter.class)
            .addFilterBefore(rateLimitFilter, JwtAuthFilter.class);

        return http.build();
    }

    static boolean exigeCsrf(HttpServletRequest request) {
        if (METODOS_SEGUROS.contains(request.getMethod())) {
            return false;
        }
        String authorization = request.getHeader("Authorization");
        if (authorization != null && authorization.startsWith("Bearer ")) {
            return false;
        }
        Cookie[] cookies = request.getCookies();
        return cookies != null && Arrays.stream(cookies).anyMatch(c -> CookieDeSessao.NOME.equals(c.getName()));
    }

    /**
     * BCrypt com custo 12 (era o padrão 10): ~4x mais caro por tentativa
     * para quem roubar os hashes. Hashes antigos (custo 10) continuam
     * válidos — o custo fica gravado no próprio hash.
     */
    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder(12);
    }

    @Bean
    public AuthenticationManager authenticationManager(AuthenticationConfiguration config) throws Exception {
        return config.getAuthenticationManager();
    }
}
