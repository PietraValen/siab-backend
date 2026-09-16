package br.edu.unip.siab.pipeline.segmentation;

import jakarta.annotation.PostConstruct;
import org.bytedeco.opencv.opencv_core.Mat;
import org.bytedeco.opencv.opencv_core.Rect;
import org.bytedeco.opencv.opencv_core.RectVector;
import org.bytedeco.opencv.opencv_objdetect.CascadeClassifier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.Optional;

/**
 * Fase 3 — Segmentação (seção 3.2 e 5.2 do escopo).
 * <p>
 * Detecta e recorta a região do rosto (Region of Interest) dentro da imagem
 * pré-processada, usando um classificador em cascata Haar do OpenCV.
 * <p>
 * TODO: avaliar substituir o Haar Cascade por um detector baseado em rede
 * neural leve (ex.: modelo DNN do próprio OpenCV) caso a acurácia de
 * detecção em diferentes ângulos/iluminações se mostre insuficiente nos
 * testes com o grupo.
 */
@Service
public class SegmentationService {

    private static final Logger log = LoggerFactory.getLogger(SegmentationService.class);

    @Value("${siab.pipeline.haarcascade-path:classpath:haarcascades/haarcascade_frontalface_default.xml}")
    private String cascadePath;

    private CascadeClassifier faceDetector;

    @PostConstruct
    public void init() {
        // NOTA: baixe o arquivo haarcascade_frontalface_default.xml (disponível
        // no repositório oficial do OpenCV) e coloque em
        // src/main/resources/haarcascades/ antes de rodar o back-end.
        faceDetector = new CascadeClassifier(resolvePath(cascadePath));
        if (faceDetector.empty()) {
            log.warn("Classificador Haar não carregado. Verifique se o arquivo .xml está em resources/haarcascades/");
        }
    }

    private String resolvePath(String path) {
        return path.replace("classpath:", "src/main/resources/");
    }

    /**
     * @return o recorte (ROI) do primeiro rosto detectado, ou vazio se
     * nenhum rosto foi encontrado na imagem.
     */
    public Optional<Mat> segmentar(Mat imagemPreProcessada) {
        log.debug("Fase 3 - Segmentação: iniciando detecção facial");

        RectVector faces = new RectVector();
        faceDetector.detectMultiScale(imagemPreProcessada, faces);

        if (faces.size() == 0) {
            log.debug("Fase 3 - Segmentação: nenhum rosto detectado");
            return Optional.empty();
        }

        Rect maiorRosto = faces.get(0);
        for (long i = 1; i < faces.size(); i++) {
            Rect candidato = faces.get(i);
            if (candidato.area() > maiorRosto.area()) {
                maiorRosto = candidato;
            }
        }

        Mat rosto = new Mat(imagemPreProcessada, maiorRosto);
        log.debug("Fase 3 - Segmentação: rosto isolado com sucesso");
        return Optional.of(rosto);
    }
}
