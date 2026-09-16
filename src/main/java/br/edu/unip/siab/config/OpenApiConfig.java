package br.edu.unip.siab.config;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.security.SecurityScheme;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Disponibiliza a documentação interativa da API em /swagger-ui.html,
 * útil tanto para o desenvolvimento do front-end em paralelo quanto para
 * evidenciar a API na dissertação (seção 5 do escopo).
 * <p>
 * Declara o esquema de autenticação Bearer JWT ({@value #ESQUEMA_JWT}) nos
 * componentes globais da API. Não é adicionado como requisito de
 * segurança global (isso marcaria TODO endpoint como protegido no Swagger
 * UI, incluindo /api/auth/login, /api/enrollment/** e /api/recognition/**,
 * que são públicos por SecurityConfig) — em vez disso, cada controller
 * protegido é anotado individualmente com
 * {@code @SecurityRequirement(name = "bearerAuth")}, refletindo fielmente
 * as regras de SecurityConfig. Basta colar o token retornado por
 * POST /api/auth/login no botão "Authorize" do Swagger UI para testar os
 * endpoints de /api/admin/**.
 */
@Configuration
public class OpenApiConfig {

    public static final String ESQUEMA_JWT = "bearerAuth";

    @Bean
    public OpenAPI siabOpenApi() {
        return new OpenAPI()
                .info(new Info()
                        .title("SIAB - Sistema de Identificação e Autenticação Biométrica")
                        .version("0.1.0")
                        .description("API do back-end do projeto de APS - PIVC - UNIP."))
                .components(new Components()
                        .addSecuritySchemes(ESQUEMA_JWT, new SecurityScheme()
                                .name(ESQUEMA_JWT)
                                .type(SecurityScheme.Type.HTTP)
                                .scheme("bearer")
                                .bearerFormat("JWT")));
    }
}
