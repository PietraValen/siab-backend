package br.edu.unip.siab.pipeline.liveness;

import org.bytedeco.javacpp.indexer.UByteIndexer;
import org.bytedeco.opencv.opencv_core.Mat;
import org.bytedeco.opencv.opencv_core.Scalar;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;
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

    /** Piscada controlada pelo teste — a detecção real de olhos depende do classificador Haar. */
    private boolean piscadaDetectada = true;

    private final LivenessService service = new LivenessService(new DetectorDePiscada() {
        @Override
        public boolean houvePiscada(List<Mat> rostos) {
            return piscadaDetectada;
        }
    });

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

    @Test
    void sequenciaComTexturaEPiscadaEhAprovada() {
        List<Mat> rostos = List.of(criarImagemComRuido(100, 1L), criarImagemComRuido(100, 2L), criarImagemComRuido(100, 3L));
        assertThat(service.verificarSequencia(rostos).aprovado()).isTrue();
    }

    @Test
    void sequenciaSemPiscadaEhReprovada() {
        piscadaDetectada = false; // foto parada diante da câmera: olhos sempre iguais
        List<Mat> rostos = List.of(criarImagemComRuido(100, 1L), criarImagemComRuido(100, 2L), criarImagemComRuido(100, 3L));
        assertThat(service.verificarSequencia(rostos).aprovado()).isFalse();
    }

    @Test
    void frameUnicoEhReprovadoQuandoAPiscadaEhExigida() {
        assertThat(service.verificarSequencia(List.of(criarImagemComRuido(100, 1L))).aprovado()).isFalse();
    }

    @Test
    void sequenciaLisaEhReprovadaMesmoComPiscada() {
        Mat lisa = new Mat(100, 100, CV_8UC1, new Scalar(128));
        assertThat(service.verificarSequencia(List.of(lisa, lisa, lisa)).aprovado()).isFalse();
    }

    @Test
    void padraoDePiscadaExigeAbertoFechadoAberto() {
        assertThat(DetectorDePiscada.padraoAbertoFechadoAberto(new boolean[]{true, false, true})).isTrue();
        assertThat(DetectorDePiscada.padraoAbertoFechadoAberto(new boolean[]{true, true, false, false, true})).isTrue();
        assertThat(DetectorDePiscada.padraoAbertoFechadoAberto(new boolean[]{true, true, true})).isFalse();
        assertThat(DetectorDePiscada.padraoAbertoFechadoAberto(new boolean[]{false, false, false})).isFalse();
        assertThat(DetectorDePiscada.padraoAbertoFechadoAberto(new boolean[]{false, true, false})).isFalse();
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
