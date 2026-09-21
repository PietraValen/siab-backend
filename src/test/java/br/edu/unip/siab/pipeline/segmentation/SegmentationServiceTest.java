package br.edu.unip.siab.pipeline.segmentation;

import org.bytedeco.opencv.opencv_core.Mat;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.io.File;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assumptions.assumeTrue;
import static org.bytedeco.opencv.global.opencv_core.CV_8UC1;

/**
 * Testes da Fase 3 (Segmentação). São pulados automaticamente (em vez de
 * falhar) se o arquivo haarcascade_frontalface_default.xml ainda não foi
 * baixado — rode "scripts/download-haarcascade.sh" e eles passam a
 * executar sem precisar editar nada aqui.
 */
class SegmentationServiceTest {

    private static final String CASCADE_PATH =
            "src/main/resources/haarcascades/haarcascade_frontalface_default.xml";

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

    // TODO (grupo): depois de ter o haarcascade baixado, adicionar um teste
    // com uma foto real de rosto em src/test/resources/fixtures/ e validar
    // que service.segmentar(...) retorna um Mat não vazio (Optional presente).
}
