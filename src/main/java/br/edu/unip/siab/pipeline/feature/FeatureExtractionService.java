package br.edu.unip.siab.pipeline.feature;

import org.bytedeco.javacpp.indexer.UByteIndexer;
import org.bytedeco.opencv.opencv_core.Mat;
import org.bytedeco.opencv.opencv_core.Size;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.Arrays;
import java.util.stream.Collectors;

import static org.bytedeco.opencv.global.opencv_imgproc.INTER_LINEAR;
import static org.bytedeco.opencv.global.opencv_imgproc.resize;

/**
 * Fase 4 — Extração de Características (seção 3.2 e 5.2 do escopo).
 * <p>
 * Gera um vetor numérico (descritor) que representa de forma compacta as
 * características discriminantes do rosto segmentado na Fase 3.
 * <p>
 * Técnica escolhida: <b>Local Binary Patterns Histograms (LBPH)</b>, descrita
 * por Ojala, Pietikäinen &amp; Mäenpää ("Multiresolution Gray-Scale and
 * Rotation Invariant Texture Classification with Local Binary Patterns",
 * 2002) e aplicada ao reconhecimento facial por Ahonen, Hadid &amp;
 * Pietikäinen ("Face Recognition with Local Binary Patterns", ECCV 2004) —
 * ver seção 3.1 do escopo. Foi escolhida em vez de um embedding via rede
 * neural pré-treinada (ONNX) por não depender de baixar um modelo externo,
 * por ser mais simples de justificar tecnicamente na dissertação e por já
 * ter todo o suporte necessário (Mat, indexers) via o JavaCV já presente
 * no projeto.
 * <p>
 * O algoritmo, implementado manualmente pixel a pixel (sem usar a classe
 * {@code org.bytedeco.opencv.opencv_face.LBPHFaceRecognizer}, que expõe uma
 * API de treino/predição interna e não um vetor comparável por distância):
 * <ol>
 *   <li>Redimensiona o rosto segmentado para um tamanho fixo
 *       ({@value #TAMANHO_PADRAO}x{@value #TAMANHO_PADRAO}px), para que o
 *       descritor tenha sempre a mesma dimensão independente do tamanho do
 *       recorte da Fase 3;</li>
 *   <li>Para cada pixel interno, calcula o código LBP (raio 1, 8 vizinhos):
 *       compara a intensidade de cada vizinho com a do pixel central,
 *       gerando um código binário de 8 bits;</li>
 *   <li>Mapeia os 256 códigos possíveis para os 58 padrões "uniformes"
 *       (no máximo 2 transições 0/1 na sequência circular de bits) + 1 bin
 *       residual que agrupa os padrões não-uniformes — reduzindo a
 *       dimensionalidade sem perda significativa de poder discriminativo,
 *       como descrito por Ojala et al.;</li>
 *   <li>Divide a imagem em uma grade {@value #CELULAS_POR_LADO}x{@value
 *       #CELULAS_POR_LADO} de células e calcula o histograma normalizado de
 *       padrões de cada célula, concatenando tudo no vetor final — essa
 *       divisão espacial preserva informação de onde cada padrão de textura
 *       ocorre no rosto (olhos, nariz, boca), não só a frequência global.</li>
 * </ol>
 */
@Service
public class FeatureExtractionService {

    private static final Logger log = LoggerFactory.getLogger(FeatureExtractionService.class);
    private static final String ALGORITMO_ATUAL = "LBPH";

    private static final int TAMANHO_PADRAO = 128;
    private static final int CELULAS_POR_LADO = 8;
    private static final int BINS_POR_CELULA = 59; // 58 padrões uniformes + 1 bin residual

    private static final int[] MAPA_PADROES_UNIFORMES = construirMapaPadroesUniformes();

    public float[] extrair(Mat rostoSegmentado) {
        log.debug("Fase 4 - Extração de características: iniciando ({})", ALGORITMO_ATUAL);

        Mat redimensionado = new Mat();
        resize(rostoSegmentado, redimensionado, new Size(TAMANHO_PADRAO, TAMANHO_PADRAO), 0, 0, INTER_LINEAR);

        int[][] codigosLbp = calcularCodigosLbp(redimensionado);
        float[] vetor = agruparEmHistogramasPorCelula(codigosLbp);

        log.debug("Fase 4 - Extração de características: vetor gerado com {} dimensões", vetor.length);
        return vetor;
    }

    public String algoritmoUtilizado() {
        return ALGORITMO_ATUAL;
    }

    public String serializar(float[] vetor) {
        return Arrays.stream(toDoubleArray(vetor))
                .mapToObj(String::valueOf)
                .collect(Collectors.joining(","));
    }

    public float[] desserializar(String vetorSerializado) {
        if (vetorSerializado == null || vetorSerializado.isEmpty()) {
            return new float[0];
        }
        String[] partes = vetorSerializado.split(",");
        float[] vetor = new float[partes.length];
        for (int i = 0; i < partes.length; i++) {
            vetor[i] = Float.parseFloat(partes[i]);
        }
        return vetor;
    }

    /**
     * Calcula o código LBP (raio 1, 8 vizinhos, sentido horário a partir do
     * canto superior esquerdo) de cada pixel interno da imagem. Pixels de
     * borda (sem 8 vizinhos completos) ficam com código 0 e são ignorados
     * na contagem dos histogramas — prática padrão na literatura de LBP.
     */
    private int[][] calcularCodigosLbp(Mat imagemCinza) {
        UByteIndexer idx = imagemCinza.createIndexer();
        int linhas = imagemCinza.rows();
        int colunas = imagemCinza.cols();
        int[][] codigos = new int[linhas][colunas];

        try {
            for (int i = 1; i < linhas - 1; i++) {
                for (int j = 1; j < colunas - 1; j++) {
                    int centro = idx.get(i, j);
                    int codigo = 0;
                    codigo |= (idx.get(i - 1, j - 1) >= centro ? 1 : 0) << 7;
                    codigo |= (idx.get(i - 1, j) >= centro ? 1 : 0) << 6;
                    codigo |= (idx.get(i - 1, j + 1) >= centro ? 1 : 0) << 5;
                    codigo |= (idx.get(i, j + 1) >= centro ? 1 : 0) << 4;
                    codigo |= (idx.get(i + 1, j + 1) >= centro ? 1 : 0) << 3;
                    codigo |= (idx.get(i + 1, j) >= centro ? 1 : 0) << 2;
                    codigo |= (idx.get(i + 1, j - 1) >= centro ? 1 : 0) << 1;
                    codigo |= (idx.get(i, j - 1) >= centro ? 1 : 0);
                    codigos[i][j] = codigo;
                }
            }
        } finally {
            idx.release();
        }
        return codigos;
    }

    /**
     * Divide a imagem em uma grade CELULAS_POR_LADO x CELULAS_POR_LADO e
     * concatena o histograma (padrões uniformes) de cada célula, formando o
     * descritor final. Cada histograma de célula é normalizado (soma = 1)
     * para reduzir o efeito de diferenças de iluminação residual entre
     * regiões do rosto e manter os vetores comparáveis por distância
     * euclidiana independente do tamanho da célula.
     */
    private float[] agruparEmHistogramasPorCelula(int[][] codigosLbp) {
        int linhas = codigosLbp.length;
        int colunas = codigosLbp[0].length;
        int alturaCelula = linhas / CELULAS_POR_LADO;
        int larguraCelula = colunas / CELULAS_POR_LADO;

        float[] vetor = new float[CELULAS_POR_LADO * CELULAS_POR_LADO * BINS_POR_CELULA];

        int celulaIndex = 0;
        for (int cy = 0; cy < CELULAS_POR_LADO; cy++) {
            for (int cx = 0; cx < CELULAS_POR_LADO; cx++) {
                int[] histograma = new int[BINS_POR_CELULA];
                int total = 0;

                int inicioLinha = cy * alturaCelula;
                int fimLinha = (cy == CELULAS_POR_LADO - 1) ? linhas : inicioLinha + alturaCelula;
                int inicioColuna = cx * larguraCelula;
                int fimColuna = (cx == CELULAS_POR_LADO - 1) ? colunas : inicioColuna + larguraCelula;

                for (int i = Math.max(inicioLinha, 1); i < Math.min(fimLinha, linhas - 1); i++) {
                    for (int j = Math.max(inicioColuna, 1); j < Math.min(fimColuna, colunas - 1); j++) {
                        int bin = MAPA_PADROES_UNIFORMES[codigosLbp[i][j]];
                        histograma[bin]++;
                        total++;
                    }
                }

                int offset = celulaIndex * BINS_POR_CELULA;
                for (int b = 0; b < BINS_POR_CELULA; b++) {
                    vetor[offset + b] = total > 0 ? (float) histograma[b] / total : 0f;
                }
                celulaIndex++;
            }
        }

        return vetor;
    }

    /**
     * Constrói o mapeamento dos 256 padrões LBP possíveis para os 58 padrões
     * "uniformes" (no máximo 2 transições 0/1 na sequência circular de 8
     * bits) + 1 bin residual (último índice) que agrupa todos os padrões
     * não-uniformes, conforme Ojala, Pietikäinen &amp; Mäenpää (2002).
     */
    private static int[] construirMapaPadroesUniformes() {
        int[] mapa = new int[256];
        int proximoIndice = 0;

        for (int padrao = 0; padrao < 256; padrao++) {
            if (contarTransicoes(padrao) <= 2) {
                mapa[padrao] = proximoIndice++;
            } else {
                mapa[padrao] = BINS_POR_CELULA - 1;
            }
        }

        return mapa;
    }

    private static int contarTransicoes(int padrao) {
        int transicoes = 0;
        for (int bit = 0; bit < 8; bit++) {
            int atual = (padrao >> bit) & 1;
            int proximo = (padrao >> ((bit + 1) % 8)) & 1;
            if (atual != proximo) {
                transicoes++;
            }
        }
        return transicoes;
    }

    private double[] toDoubleArray(float[] vetor) {
        double[] out = new double[vetor.length];
        for (int i = 0; i < vetor.length; i++) out[i] = vetor[i];
        return out;
    }
}
