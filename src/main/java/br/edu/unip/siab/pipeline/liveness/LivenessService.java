package br.edu.unip.siab.pipeline.liveness;

import org.bytedeco.javacpp.indexer.DoubleIndexer;
import org.bytedeco.opencv.opencv_core.Mat;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import static org.bytedeco.opencv.global.opencv_core.CV_64F;
import static org.bytedeco.opencv.global.opencv_core.meanStdDev;
import static org.bytedeco.opencv.global.opencv_imgproc.Laplacian;

/**
 * Módulo "liveness" (seção 3.3 e 5.2 do escopo) — RF07.
 * <p>
 * Verifica se o rosto capturado pertence a uma pessoa real diante da
 * câmera, dificultando fraudes por apresentação de fotografia ou vídeo.
 * <p>
 * Técnica escolhida: <b>análise textural via variância do Laplaciano</b>
 * (seção 3.3 do escopo: "análise textural da imagem para identificar
 * padrões típicos de superfícies impressas ou telas"), medida clássica de
 * nitidez/foco descrita por Pech-Pacheco et al. ("Diatom Autofocusing in
 * Brightfield Microscopy: A Comparative Study", ICPR 2000). A escolha, em
 * vez de detecção de piscar multi-frame (a outra alternativa do escopo),
 * foi feita porque o pipeline atual (ver PipelineOrchestratorService)
 * processa uma única imagem por tentativa de reconhecimento — vivacidade
 * por piscar exigiria receber uma sequência de frames, mudando o contrato
 * de toda a Fase 1 (aquisição) e dos endpoints REST.
 * <p>
 * Fundamento: reapresentar um rosto por meio de uma foto impressa ou tela
 * ao invés do rosto real tende a produzir uma imagem com menos detalhe de
 * alta frequência (perda de nitidez por causa da dupla captura — impressão
 * ou exibição, e depois a foto dessa reprodução pela câmera do sistema, ou
 * padrões de moiré no caso de telas). O filtro Laplaciano realça bordas e
 * transições bruscas de intensidade; a variância da resposta do Laplaciano
 * é, portanto, uma medida da quantidade de detalhe fino presente na
 * imagem. Uma variância baixa sugere uma imagem "lisa demais" para ser um
 * rosto real capturado diretamente pela câmera.
 */
@Service
public class LivenessService {

    private static final Logger log = LoggerFactory.getLogger(LivenessService.class);

    /**
     * TODO: assim como o threshold da Fase 5 (ver RecognitionService), este
     * valor precisa ser calibrado experimentalmente com o dataset real do
     * grupo (capturas de rostos reais vs. fotos/telas reapresentadas à
     * câmera) antes da entrega — o valor atual é um chute inicial baseado
     * na ordem de grandeza típica citada na literatura de detecção de
     * blur, não em dados do projeto.
     */
    @Value("${siab.pipeline.liveness-variance-threshold:80.0}")
    private double varianciaMinima;

    public boolean ehRostoReal(Mat imagemCapturada) {
        double variancia = varianciaDoLaplaciano(imagemCapturada);
        boolean aprovado = variancia >= varianciaMinima;

        log.debug("Liveness: variância do Laplaciano = {} (mínimo exigido = {}) -> {}",
                variancia, varianciaMinima, aprovado ? "aprovado" : "reprovado");

        return aprovado;
    }

    private double varianciaDoLaplaciano(Mat imagem) {
        Mat laplaciano = new Mat();
        Laplacian(imagem, laplaciano, CV_64F);

        Mat media = new Mat();
        Mat desvioPadrao = new Mat();
        meanStdDev(laplaciano, media, desvioPadrao);

        DoubleIndexer idx = desvioPadrao.createIndexer();
        double desvio;
        try {
            desvio = idx.get(0);
        } finally {
            idx.release();
        }

        return desvio * desvio;
    }
}
