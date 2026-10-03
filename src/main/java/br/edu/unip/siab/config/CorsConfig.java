package br.edu.unip.siab.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import java.util.Arrays;
import java.util.List;

/**
 * Libera o front-end Next.js (rodando em outra origem/porta) a consumir a API.
 * <p>
 * Exposta como {@link CorsConfigurationSource} (em vez de apenas um
 * {@code WebMvcConfigurer}) para que o {@code SecurityConfig} possa plugar
 * essa configuração diretamente na cadeia de filtros do Spring Security via
 * {@code http.cors(...)}. Isso é necessário porque o CORS do
 * {@code WebMvcConfigurer} só é aplicado dentro do {@code DispatcherServlet},
 * ou seja, depois dos filtros de segurança — como o preflight (OPTIONS) não
 * carrega Authorization, ele era barrado por {@code anyRequest().authenticated()}
 * com 403 antes de qualquer header de CORS ser adicionado à resposta.
 * <p>
 * Origens vêm de {@code CORS_ALLOWED_ORIGINS} (lista separada por vírgula,
 * padrão http://localhost:3000) — antes era "*", que deixava qualquer site
 * chamar a API pelo navegador de um admin. Com credenciais (cookie HttpOnly
 * do painel) a especificação de CORS nem permite "*". Métodos e headers
 * também ficam explícitos.
 */
@Configuration
public class CorsConfig {

    @Value("${siab.security.cors-allowed-origins:http://localhost:3000}")
    private String origensPermitidas;

    @Bean
    public CorsConfigurationSource corsConfigurationSource() {
        CorsConfiguration configuration = new CorsConfiguration();
        configuration.setAllowedOrigins(Arrays.stream(origensPermitidas.split(","))
                .map(String::trim)
                .filter(origem -> !origem.isEmpty())
                .toList());
        configuration.setAllowedMethods(List.of("GET", "POST", "PUT", "DELETE", "OPTIONS"));
        configuration.setAllowedHeaders(List.of("Authorization", "Content-Type", "X-XSRF-TOKEN",
                "X-Terminal-Id", "X-Desafio", "X-Timestamp", "X-Assinatura"));
        configuration.setExposedHeaders(List.of("Retry-After", "Content-Disposition"));
        configuration.setAllowCredentials(true);
        configuration.setMaxAge(3600L);

        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/api/**", configuration);
        return source;
    }
}
