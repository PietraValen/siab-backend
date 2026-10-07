package br.edu.unip.siab.pipeline.segmentation;

import jakarta.annotation.PostConstruct;
import org.bytedeco.opencv.opencv_core.Mat;
import org.bytedeco.opencv.opencv_core.Rect;
import org.bytedeco.opencv.opencv_core.RectVector;
import org.bytedeco.opencv.opencv_core.Size;
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

    /**
     * O rosto precisa ocupar pelo menos 1/{@value} do menor lado do quadro.
     * Sem esse piso, o Haar Cascade aceitava texturas pequenas do fundo
     * como "rosto" quando a pessoa estava de perfil (visto nas fotos de
     * calibração do grupo: recortes de 82–87 px de parede numa foto de
     * 899 px de largura). Na frente do terminal o rosto ocupa bem mais que
     * isso, então o piso não rejeita capturas legítimas.
     */
    static final int FRACAO_MINIMA_DO_ROSTO = 5;

    @PostConstruct
    public void init() {
        // NOTA: baixe o arquivo haarcascade_frontalface_default.xml (disponível
        // no repositório oficial do OpenCV) e coloque em
        // src/main/resources/haarcascades/ antes de rodar o back-end.
        faceDetector = CarregadorDeCascata.carregar(cascadePath);
        if (faceDetector.empty()) {
            log.warn("Classificador Haar não carregado. Verifique se o arquivo .xml está em resources/haarcascades/");
        }
    }

    /**
     * @return o recorte (ROI) do maior rosto detectado, ou vazio se
     * nenhum rosto foi encontrado na imagem.
     */
    public Optional<Mat> segmentar(Mat imagemPreProcessada) {
        return localizar(imagemPreProcessada).map(rosto -> new Mat(imagemPreProcessada, rosto));
    }

    /**
     * Mesma detecção de {@link #segmentar(Mat)}, mas devolve só o retângulo
     * do maior rosto — para recortar a mesma região de outra versão do
     * frame (ex.: a imagem em cinza sem equalização, usada pelo liveness).
     */
    public Optional<Rect> localizar(Mat imagemPreProcessada) {
        log.debug("Fase 3 - Segmentação: iniciando detecção facial");

        RectVector faces = new RectVector();
        int ladoMinimo = Math.min(imagemPreProcessada.cols(), imagemPreProcessada.rows()) / FRACAO_MINIMA_DO_ROSTO;
        faceDetector.detectMultiScale(imagemPreProcessada, faces, 1.1, 3, 0,
                new Size(ladoMinimo, ladoMinimo), new Size());

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

        log.debug("Fase 3 - Segmentação: rosto isolado com sucesso");
        return Optional.of(maiorRosto);
    }
}
