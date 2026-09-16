package br.edu.unip.siab.pipeline.liveness;

import org.bytedeco.javacpp.indexer.UByteIndexer;
import org.bytedeco.opencv.opencv_core.Mat;
import org.bytedeco.opencv.opencv_core.Scalar;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.Random;

import static org.assertj.core.api.Assertions.assertThat;
import static org.bytedeco.opencv.global.opencv_core.CV_8UC1;

/**
 * Cobre a Fase de liveness (análise textural via variância do Laplaciano,
 * ver Javadoc de LivenessService): uma imagem com textura rica (muitos
 * detalhes de alta frequência, como um rosto real capturado de perto)
 * deve ser aprovada; uma imagem lisa/uniforme (sem nenhum detalhe fino,
 * como se esperaria de uma reprodução degradada) deve ser reprovada.
 */
class LivenessServiceTest {

    private final LivenessService service = new LivenessService();

    @BeforeEach
    void setUp() {
        ReflectionTestUtils.setField(service, "varianciaMinima", 80.0); // mesmo default do application.yml
    }

    @Test
    void imagemComTexturaRicaEhAprovada() {
        Mat imagemComRuido = criarImagemComRuido(200, 42L);
        assertThat(service.ehRostoReal(imagemComRuido)).isTrue();
    }

    @Test
    void imagemLisaSemTexturaEhReprovada() {
        Mat imagemLisa = new Mat(200, 200, CV_8UC1, new Scalar(128));
        assertThat(service.ehRostoReal(imagemLisa)).isFalse();
    }

    private Mat criarImagemComRuido(int dimensao, long seed) {
        Mat mat = new Mat(dimensao, dimensao, CV_8UC1);
        UByteIndexer idx = mat.createIndexer();
        Random random = new Random(seed);
        try {
            for (int i = 0; i < dimensao; i++) {
                for (int j = 0; j < dimensao; j++) {
                    idx.put(i, j, random.nextInt(256));
                }
            }
        } finally {
            idx.release();
        }
        return mat;
    }
}
