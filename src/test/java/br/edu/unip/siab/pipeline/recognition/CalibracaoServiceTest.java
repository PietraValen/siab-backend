package br.edu.unip.siab.pipeline.recognition;

import br.edu.unip.siab.pipeline.recognition.CalibracaoService.Captura;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

/**
 * Cobre a conta da calibração com vetores sintéticos de 1 dimensão, em que a
 * distância euclidiana é só a diferença entre os números.
 */
class CalibracaoServiceTest {

    private static Captura de(long usuario, float valor) {
        return new Captura(usuario, new float[]{valor});
    }

    @Test
    void semCapturasRepetidasNaoSugereLimiar() {
        var resultado = CalibracaoService.calibrar(List.of(de(1, 0f), de(2, 1f)), 0.35);

        assertThat(resultado.limiarEer()).isNull();
        assertThat(resultado.limiarFarZero()).isNull();
        assertThat(resultado.mensagem()).contains("Dados insuficientes");
        assertThat(resultado.impostores().pares()).isEqualTo(1);
    }

    @Test
    void separaParesGenuinosDeImpostoresESugereLimiares() {
        // Alice em 0.0 e 0.2 (genuína 0.2); Bob em 1.0 e 1.3 (genuína 0.3).
        // Impostores: 1.0, 1.3, 0.8, 1.1 — o menor é 0.8.
        var capturas = List.of(de(1, 0f), de(1, 0.2f), de(2, 1f), de(2, 1.3f));

        var resultado = CalibracaoService.calibrar(capturas, 0.25);

        assertThat(resultado.usuarios()).isEqualTo(2);
        assertThat(resultado.genuinas().pares()).isEqualTo(2);
        assertThat(resultado.impostores().pares()).isEqualTo(4);
        assertThat(resultado.impostores().minima()).isCloseTo(0.8, within(1e-6));

        // Limiar atual 0.25: aceita a Alice, rejeita o Bob, nenhum impostor.
        assertThat(resultado.atual().frr()).isEqualTo(0.5);
        assertThat(resultado.atual().far()).isZero();

        // Classes separadas: o EER é zero, num limiar entre 0.3 e 0.8.
        assertThat(resultado.limiarEer().far()).isZero();
        assertThat(resultado.limiarEer().frr()).isZero();
        assertThat(resultado.limiarEer().limiar()).isBetween(0.29, 0.8);

        assertThat(resultado.limiarFarZero().limiar()).isLessThan(resultado.impostores().minima()).isCloseTo(0.8, within(1e-6));
        assertThat(resultado.limiarFarZero().far()).isZero();
        assertThat(resultado.limiarFarZero().frr()).isZero();
    }

    @Test
    void comClassesSobrepostasOEerEquilibraAsDuasTaxas() {
        // Genuínas: 0.5 e 0.5; impostores: 0.3, 0.8, 0.2 e 0.3 (as classes se sobrepõem).
        var capturas = List.of(de(1, 0f), de(1, 0.5f), de(2, 0.3f), de(2, 0.8f));

        var resultado = CalibracaoService.calibrar(capturas, 0.35);

        assertThat(resultado.mensagem()).isNull();
        assertThat(Math.abs(resultado.limiarEer().far() - resultado.limiarEer().frr()))
                .isLessThanOrEqualTo(Math.abs(resultado.atual().far() - resultado.atual().frr()));
        assertThat(resultado.limiarFarZero().far()).isZero();
    }
}
