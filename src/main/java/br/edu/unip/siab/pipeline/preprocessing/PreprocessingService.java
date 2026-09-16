package br.edu.unip.siab.pipeline.preprocessing;

import org.bytedeco.opencv.opencv_core.Mat;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import static org.bytedeco.opencv.global.opencv_imgproc.*;

/**
 * Fase 2 — Pré-processamento (seção 3.2 e 5.2 do escopo).
 * <p>
 * Recebe a imagem bruta capturada na Fase 1 e aplica: conversão para escala
 * de cinza, equalização de histograma e normalização, reduzindo ruído e
 * padronizando as condições de iluminação antes da detecção facial.
 */
@Service
public class PreprocessingService {

    private static final Logger log = LoggerFactory.getLogger(PreprocessingService.class);

    public Mat processar(Mat imagemOriginal) {
        log.debug("Fase 2 - Pré-processamento: iniciando");

        Mat cinza = new Mat();
        cvtColor(imagemOriginal, cinza, COLOR_BGR2GRAY);

        Mat equalizada = new Mat();
        equalizeHist(cinza, equalizada);

        // TODO: normalização adicional (ex.: redimensionamento padrão,
        // filtro de suavização de ruído) conforme necessidade observada
        // durante os testes com imagens reais capturadas pelo front-end.

        log.debug("Fase 2 - Pré-processamento: concluído");
        return equalizada;
    }
}
