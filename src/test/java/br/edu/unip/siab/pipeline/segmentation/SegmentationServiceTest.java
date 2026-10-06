package br.edu.unip.siab.pipeline.segmentation;

import br.edu.unip.siab.pipeline.preprocessing.PreprocessingService;
import org.bytedeco.opencv.opencv_core.Mat;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.io.File;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assumptions.assumeTrue;
import static org.bytedeco.opencv.global.opencv_core.CV_8UC1;
import static org.bytedeco.opencv.global.opencv_imgcodecs.IMREAD_COLOR;
import static org.bytedeco.opencv.global.opencv_imgcodecs.imread;

/**
 * Testes da Fase 3 (Segmentação). São pulados automaticamente (em vez de
 * falhar) se o arquivo haarcascade_frontalface_default.xml ainda não foi
 * baixado — rode "scripts/download-haarcascade.sh" e eles passam a
 * executar sem precisar editar nada aqui.
 */
class SegmentationServiceTest {

    private static final String CASCADE_PATH =
            "src/main/resources/haarcascades/haarcascade_frontalface_default.xml";

    private static final String FOTO_REAL = "src/test/resources/fixtures/rosto-exemplo.jpg";

    private SegmentationService novoServico() {
        SegmentationService service = new SegmentationService();
        ReflectionTestUtils.setField(service, "cascadePath", "classpath:haarcascades/haarcascade_frontalface_default.xml");
        ReflectionTestUtils.invokeMethod(service, "init");
        return service;
    }

    @Test
    void imagemCompletamentePretaNaoTemRostoDetectado() {
        assumeTrue(new File(CASCADE_PATH).exists(),
                "haarcascade_frontalface_default.xml ainda não foi baixado — rode scripts/download-haarcascade.sh");

        SegmentationService service = novoServico();

        Mat imagemPreta = new Mat(200, 200, CV_8UC1, new org.bytedeco.opencv.opencv_core.Scalar(0));
        var resultado = service.segmentar(imagemPreta);

        assertThat(resultado).isEmpty();
    }

    /**
     * Roda só quando existe {@value #FOTO_REAL}: a foto é dado biométrico,
     * então fica fora do Git (ver .gitignore e fixtures/LEIA-ME.txt) e cada
     * integrante a coloca localmente, com consentimento de quem aparece nela.
     */
    @Test
    void fotoRealDeRostoTemRostoDetectado() {
        assumeTrue(new File(CASCADE_PATH).exists(),
                "haarcascade_frontalface_default.xml ainda não foi baixado — rode scripts/download-haarcascade.sh");
        assumeTrue(new File(FOTO_REAL).exists(),
                "Coloque uma foto de rosto em " + FOTO_REAL + " para rodar este teste");

        Mat foto = imread(FOTO_REAL, IMREAD_COLOR);
        assertThat(foto.empty()).as("a foto não pôde ser lida").isFalse();

        var resultado = novoServico().segmentar(new PreprocessingService().processar(foto));

        assertThat(resultado).isPresent();
        assertThat(resultado.get().empty()).isFalse();
    }
}
