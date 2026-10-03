package br.edu.unip.siab.pipeline.liveness;

import br.edu.unip.siab.pipeline.segmentation.CarregadorDeCascata;
import jakarta.annotation.PostConstruct;
import org.bytedeco.opencv.opencv_core.Mat;
import org.bytedeco.opencv.opencv_core.Rect;
import org.bytedeco.opencv.opencv_core.RectVector;
import org.bytedeco.opencv.opencv_core.Size;
import org.bytedeco.opencv.opencv_objdetect.CascadeClassifier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * Detecção de piscada para o liveness multi-frame (seção 1.4 do roteiro de
 * segurança), feita "na mão" com o classificador Haar de olhos do próprio
 * OpenCV ({@code haarcascade_eye.xml}, mesmo autor/licença do de rosto).
 * <p>
 * Ideia: o {@code haarcascade_eye} foi treinado com olhos abertos e
 * praticamente não dispara em olho fechado. Numa sequência de frames
 * capturada enquanto a tela pede "pisque", uma pessoa real produz o padrão
 * <b>aberto → fechado → aberto</b>; uma foto impressa ou parada na tela
 * produz olhos sempre abertos (ou sempre não detectados).
 * <p>
 * Limitação conhecida (para a dissertação): um vídeo da vítima piscando,
 * reproduzido numa tela, passa por esta checagem — é ela somada à análise
 * textural do {@link LivenessService} e à assinatura do terminal (que
 * impede enviar frames que não vieram da câmera do quiosque) que tornam o
 * ataque difícil.
 */
@Component
public class DetectorDePiscada {

    private static final Logger log = LoggerFactory.getLogger(DetectorDePiscada.class);

    @Value("${siab.pipeline.eye-cascade-path:classpath:haarcascades/haarcascade_eye.xml}")
    private String cascadePath;

    private CascadeClassifier detectorDeOlhos;

    @PostConstruct
    public void init() {
        detectorDeOlhos = CarregadorDeCascata.carregar(cascadePath);
        if (detectorDeOlhos.empty()) {
            log.warn("Classificador Haar de olhos não carregado ({}): a checagem de piscada vai reprovar tudo.", cascadePath);
        }
    }

    /** Procura ao menos um olho na metade superior do recorte do rosto. */
    public boolean olhosAbertos(Mat rosto) {
        if (detectorDeOlhos == null || detectorDeOlhos.empty()) {
            return false;
        }
        int alturaRegiao = Math.max(1, (int) (rosto.rows() * 0.6));
        Mat regiaoDosOlhos = new Mat(rosto, new Rect(0, 0, rosto.cols(), alturaRegiao));
        int minimo = Math.max(8, rosto.cols() / 10);

        RectVector olhos = new RectVector();
        detectorDeOlhos.detectMultiScale(regiaoDosOlhos, olhos, 1.1, 4, 0, new Size(minimo, minimo), new Size());
        return olhos.size() > 0;
    }

    /** {@code true} se a sequência tem olhos abertos, depois fechados, depois abertos de novo. */
    public boolean houvePiscada(List<Mat> rostos) {
        boolean[] abertos = new boolean[rostos.size()];
        for (int i = 0; i < rostos.size(); i++) {
            abertos[i] = olhosAbertos(rostos.get(i));
        }
        boolean piscou = padraoAbertoFechadoAberto(abertos);
        log.debug("Liveness: olhos por frame = {} -> piscada {}", java.util.Arrays.toString(abertos),
                piscou ? "detectada" : "não detectada");
        return piscou;
    }

    static boolean padraoAbertoFechadoAberto(boolean[] abertos) {
        int estado = 0; // 0 = esperando aberto, 1 = esperando fechado, 2 = esperando reabrir
        for (boolean aberto : abertos) {
            if (estado == 0 && aberto) {
                estado = 1;
            } else if (estado == 1 && !aberto) {
                estado = 2;
            } else if (estado == 2 && aberto) {
                return true;
            }
        }
        return false;
    }
}
