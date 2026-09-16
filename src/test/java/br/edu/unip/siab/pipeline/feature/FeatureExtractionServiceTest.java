package br.edu.unip.siab.pipeline.feature;

import org.bytedeco.javacpp.indexer.UByteIndexer;
import org.bytedeco.opencv.opencv_core.Mat;
import org.junit.jupiter.api.Test;

import java.util.Random;

import static org.assertj.core.api.Assertions.assertThat;
import static org.bytedeco.opencv.global.opencv_core.CV_8UC1;

/**
 * Testa o contrato de serialização/desserialização do vetor de
 * características (independente do algoritmo usado em extrair()) e, desde
 * que a Fase 4 foi implementada com LBPH (ver Javadoc de
 * FeatureExtractionService), o critério real de qualidade da extração:
 * duas capturas da MESMA "pessoa" (mesma textura de base, com pequeno ruído
 * simulando variação entre capturas) devem ficar mais próximas entre si
 * (menor distância euclidiana) do que uma captura de uma pessoa diferente
 * (textura de frequência espacial diferente).
 */
class FeatureExtractionServiceTest {

    private final FeatureExtractionService service = new FeatureExtractionService();

    @Test
    void serializarEDesserializarPreservaOsValores() {
        float[] original = { 0.123f, -0.045f, 0.998f, 12.5f, -3.0f };

        String serializado = service.serializar(original);
        float[] resultado = service.desserializar(serializado);

        assertThat(resultado).containsExactly(original);
    }

    @Test
    void vetorVazioNaoQuebraASerializacao() {
        float[] vazio = {};
        String serializado = service.serializar(vazio);
        assertThat(serializado).isEmpty();
    }

    @Test
    void algoritmoUtilizadoNuncaERetornaVazio() {
        assertThat(service.algoritmoUtilizado()).isNotBlank();
    }

    @Test
    void algoritmoUtilizadoEhLbph() {
        assertThat(service.algoritmoUtilizado()).isEqualTo("LBPH");
    }

    @Test
    void mesmaPessoaFicaMaisProximaDoQueImpostor() {
        Mat capturaA1 = criarRostoSintetico(4, 0, 1L);
        Mat capturaA2 = criarRostoSintetico(4, 5, 2L); // mesma "pessoa": mesma textura + ruído leve
        Mat capturaImpostor = criarRostoSintetico(16, 0, 3L); // "pessoa" diferente: textura de outra frequência

        float[] vetorA1 = service.extrair(capturaA1);
        float[] vetorA2 = service.extrair(capturaA2);
        float[] vetorImpostor = service.extrair(capturaImpostor);

        double distanciaMesmaPessoa = distanciaEuclidiana(vetorA1, vetorA2);
        double distanciaImpostor = distanciaEuclidiana(vetorA1, vetorImpostor);

        assertThat(distanciaMesmaPessoa).isLessThan(distanciaImpostor);
    }

    @Test
    void vetorExtraidoTemDimensaoFixaIndependenteDoTamanhoDoRosto() {
        Mat rostoPequeno = criarRostoSintetico(4, 0, 10L, 80);
        Mat rostoGrande = criarRostoSintetico(4, 0, 10L, 200);

        float[] vetorPequeno = service.extrair(rostoPequeno);
        float[] vetorGrande = service.extrair(rostoGrande);

        assertThat(vetorPequeno.length).isEqualTo(vetorGrande.length);
        assertThat(vetorPequeno.length).isEqualTo(64 * 59); // 8x8 células * 59 bins (padrões uniformes)
    }

    private Mat criarRostoSintetico(int tamanhoBloco, int ruidoMax, long seed) {
        return criarRostoSintetico(tamanhoBloco, ruidoMax, seed, 150);
    }

    private Mat criarRostoSintetico(int tamanhoBloco, int ruidoMax, long seed, int dimensao) {
        Mat mat = new Mat(dimensao, dimensao, CV_8UC1);
        UByteIndexer idx = mat.createIndexer();
        Random random = new Random(seed);
        try {
            for (int i = 0; i < dimensao; i++) {
                for (int j = 0; j < dimensao; j++) {
                    boolean claro = ((i / tamanhoBloco) + (j / tamanhoBloco)) % 2 == 0;
                    int base = claro ? 200 : 50;
                    int ruido = ruidoMax == 0 ? 0 : random.nextInt(ruidoMax * 2 + 1) - ruidoMax;
                    int valor = Math.max(0, Math.min(255, base + ruido));
                    idx.put(i, j, valor);
                }
            }
        } finally {
            idx.release();
        }
        return mat;
    }

    private double distanciaEuclidiana(float[] a, float[] b) {
        double soma = 0;
        for (int i = 0; i < a.length; i++) {
            double diff = a[i] - b[i];
            soma += diff * diff;
        }
        return Math.sqrt(soma);
    }
}
