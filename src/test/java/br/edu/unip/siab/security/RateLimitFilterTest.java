package br.edu.unip.siab.security;

import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;

import static org.assertj.core.api.Assertions.assertThat;

class RateLimitFilterTest {

    private final RateLimitFilter filtro = new RateLimitFilter(Clock.fixed(Instant.parse("2026-10-03T12:00:10Z"), ZoneOffset.UTC));

    private MockHttpServletResponse enviar(String metodo, String uri, String ip) throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest(metodo, uri);
        request.setRemoteAddr(ip);
        MockHttpServletResponse response = new MockHttpServletResponse();
        filtro.doFilter(request, response, new MockFilterChain());
        return response;
    }

    @Test
    void loginAcimaDoLimiteRecebe429ComRetryAfter() throws Exception {
        ReflectionTestUtils.setField(filtro, "loginPorMinuto", 3);
        for (int i = 0; i < 3; i++) {
            assertThat(enviar("POST", "/api/auth/login", "10.0.0.1").getStatus()).isEqualTo(200);
        }
        MockHttpServletResponse bloqueada = enviar("POST", "/api/auth/login", "10.0.0.1");
        assertThat(bloqueada.getStatus()).isEqualTo(429);
        assertThat(bloqueada.getHeader("Retry-After")).isEqualTo("50");

        // Outro IP tem a própria cota.
        assertThat(enviar("POST", "/api/auth/login", "10.0.0.2").getStatus()).isEqualTo(200);
    }

    @Test
    void reconhecimentoTemLimiteProprioEOutrasRotasNaoSaoLimitadas() throws Exception {
        ReflectionTestUtils.setField(filtro, "reconhecimentoPorMinuto", 1);
        assertThat(enviar("POST", "/api/recognition/scan", "10.0.0.3").getStatus()).isEqualTo(200);
        assertThat(enviar("GET", "/api/recognition/desafio", "10.0.0.3").getStatus()).isEqualTo(429);

        for (int i = 0; i < 50; i++) {
            assertThat(enviar("GET", "/api/admin/usuarios", "10.0.0.3").getStatus()).isEqualTo(200);
        }
    }
}
