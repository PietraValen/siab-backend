package br.edu.unip.siab.config;

import br.edu.unip.siab.auth.JwtAuthFilter;
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
import org.springframework.web.cors.CorsConfigurationSource;

/**
 * Regras de segurança da API.
 * <p>
 * - /api/auth/**                     -> público (login e bootstrap do painel administrativo)
 * - /api/enrollment/**                -> público (cadastro facial, Fase 1 do pipeline)
 * - /api/recognition/**                -> público (tela de reconhecimento /scan)
 * - POST /api/admin/administradores   -> público na camada de filtro; o
 *   {@link br.edu.unip.siab.admin.AdministradorController} decide na marra
 *   se exige um JWT autenticado, dependendo de já existir ou não algum
 *   administrador cadastrado (bootstrap do primeiro admin do sistema).
 * - /api/admin/**  (restante)          -> exige token JWT válido (painel administrativo)
 * <p>
 * A API é stateless (RNF03 do escopo): nenhuma sessão é mantida no servidor,
 * cada requisição autenticada carrega seu próprio token JWT.
 */
@Configuration
@EnableWebSecurity
@RequiredArgsConstructor
public class SecurityConfig {

    private final JwtAuthFilter jwtAuthFilter;
    private final CorsConfigurationSource corsConfigurationSource;

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http
            .cors(cors -> cors.configurationSource(corsConfigurationSource))
            .csrf(csrf -> csrf.disable())
            .sessionManagement(sm -> sm.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
            .authorizeHttpRequests(auth -> auth
                .requestMatchers(HttpMethod.OPTIONS, "/**").permitAll()
                .requestMatchers("/api/auth/**").permitAll()
                .requestMatchers("/api/enrollment/**").permitAll()
                .requestMatchers("/api/recognition/**").permitAll()
                .requestMatchers(HttpMethod.POST, "/api/admin/administradores").permitAll()
                .requestMatchers("/swagger-ui/**", "/v3/api-docs/**").permitAll()
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
            .addFilterBefore(jwtAuthFilter, UsernamePasswordAuthenticationFilter.class);

        return http.build();
    }

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    @Bean
    public AuthenticationManager authenticationManager(AuthenticationConfiguration config) throws Exception {
        return config.getAuthenticationManager();
    }
}
