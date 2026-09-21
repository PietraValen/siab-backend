package br.edu.unip.siab.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

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
 * Em produção, troque "*" pela URL real do front-end na VM (ex.: http://SEU_IP:3000).
 */
@Configuration
public class CorsConfig {

    @Bean
    public CorsConfigurationSource corsConfigurationSource() {
        CorsConfiguration configuration = new CorsConfiguration();
        configuration.setAllowedOriginPatterns(List.of("*")); // TODO: restringir em produção
        configuration.setAllowedMethods(List.of("GET", "POST", "PUT", "DELETE", "OPTIONS"));
        configuration.setAllowedHeaders(List.of("*"));

        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/api/**", configuration);
        return source;
    }
}
