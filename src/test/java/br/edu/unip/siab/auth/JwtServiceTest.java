package br.edu.unip.siab.auth;

import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Garante que a aplicação não sobe sem um JWT_SECRET de verdade: sem valor,
 * curto demais ou igual a um dos exemplos que já estiveram no repositório.
 */
class JwtServiceTest {

    private static final String SEGREDO_VALIDO = "um-segredo-de-teste-com-mais-de-32-bytes-de-tamanho";

    private JwtService servicoCom(String segredo) {
        JwtService service = new JwtService();
        ReflectionTestUtils.setField(service, "jwtSecret", segredo);
        ReflectionTestUtils.setField(service, "jwtExpirationMs", 60_000L);
        return service;
    }

    @Test
    void recusaSegredoVazio() {
        assertThatThrownBy(() -> servicoCom("  ").validarSegredo())
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("não definido");
    }

    @Test
    void recusaSegredoCurto() {
        assertThatThrownBy(() -> servicoCom("curto-demais").validarSegredo())
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("32 bytes");
    }

    @Test
    void recusaSegredosDeExemploDoRepositorio() {
        for (String exemplo : JwtService.SEGREDOS_DE_EXEMPLO) {
            assertThatThrownBy(() -> servicoCom(exemplo).validarSegredo())
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining("exemplo");
        }
    }

    @Test
    void aceitaSegredoValidoEEmiteTokenVerificavel() {
        JwtService service = servicoCom(SEGREDO_VALIDO);
        assertThatCode(service::validarSegredo).doesNotThrowAnyException();

        String token = service.generateToken("admin");
        assertThat(service.isTokenValid(token, "admin")).isTrue();
    }
}
