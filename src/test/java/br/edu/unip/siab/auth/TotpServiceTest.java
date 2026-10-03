package br.edu.unip.siab.auth;

import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;

import static org.assertj.core.api.Assertions.assertThat;

class TotpServiceTest {

    /** Vetores de teste do apêndice B da RFC 6238 (SHA-1), truncados para 6 dígitos. */
    @Test
    void bateComOsVetoresDaRfc6238() {
        TotpService totp = new TotpService();
        byte[] chave = "12345678901234567890".getBytes(StandardCharsets.US_ASCII);

        assertThat(totp.codigoNoPasso(chave, 59 / 30)).isEqualTo("287082");          // 94287082
        assertThat(totp.codigoNoPasso(chave, 1111111109L / 30)).isEqualTo("081804"); // 07081804
        assertThat(totp.codigoNoPasso(chave, 1234567890L / 30)).isEqualTo("005924"); // 89005924
    }

    @Test
    void aceitaUmPassoDeDiferencaDeRelogioENaoMais() {
        String segredo = TotpService.base32("12345678901234567890".getBytes(StandardCharsets.US_ASCII));
        TotpService totp = new TotpService(Clock.fixed(Instant.ofEpochSecond(1234567890L), ZoneOffset.UTC));
        long passo = 1234567890L / 30;
        byte[] chave = TotpService.deBase32(segredo);

        assertThat(totp.verificar(segredo, totp.codigoNoPasso(chave, passo)).getAsLong()).isEqualTo(passo);
        assertThat(totp.verificar(segredo, totp.codigoNoPasso(chave, passo - 1))).isPresent();
        assertThat(totp.verificar(segredo, totp.codigoNoPasso(chave, passo + 2))).isEmpty();
        assertThat(totp.verificar(segredo, "abc123")).isEmpty();
    }

    @Test
    void base32IdaEVolta() {
        byte[] dados = {0, 1, 2, (byte) 0xFF, 42, 7, 9, 100, (byte) 0x80, 3};
        assertThat(TotpService.deBase32(TotpService.base32(dados))).isEqualTo(dados);
    }
}
