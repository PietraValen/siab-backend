package br.edu.unip.siab.pipeline.recognition;

import br.edu.unip.siab.pipeline.feature.FaceEmbedding;
import br.edu.unip.siab.pipeline.feature.FaceEmbeddingRepository;
import br.edu.unip.siab.pipeline.feature.FeatureExtractionService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;

/**
 * Calibração do limiar da Fase 5 a partir dos cadastros reais (pendência 1
 * do CLAUDE.md).
 * <p>
 * Cada cadastro feito em /api/enrollment vira um vetor LBPH no banco. Com
 * duas ou mais capturas da mesma pessoa, dá para medir:
 * <ul>
 *   <li><b>distâncias genuínas</b>: entre capturas da <i>mesma</i> pessoa;</li>
 *   <li><b>distâncias de impostor</b>: entre capturas de pessoas <i>diferentes</i>.</li>
 * </ul>
 * Para um limiar {@code t}, a taxa de falsa aceitação (FAR) é a fração de
 * pares de impostor com distância {@code <= t} e a de falsa rejeição (FRR) é
 * a fração de pares genuínos com distância {@code > t}. O serviço devolve
 * dois limiares candidatos:
 * <ul>
 *   <li>o do <b>Equal Error Rate</b> (EER), onde FAR e FRR se cruzam — o ponto
 *       de equilíbrio clássico da literatura biométrica;</li>
 *   <li>o maior limiar com <b>FAR = 0</b> nos dados medidos — o mais adequado
 *       a um cofre de segurança máxima, onde aceitar um impostor é muito pior
 *       do que pedir para a pessoa tentar de novo.</li>
 * </ul>
 * Só lê o banco: o valor escolhido vai para
 * {@code siab.pipeline.recognition-threshold} à mão, depois de o grupo
 * analisar os números.
 */
@Service
@RequiredArgsConstructor
public class CalibracaoService {

    private final FaceEmbeddingRepository faceEmbeddingRepository;
    private final FeatureExtractionService featureExtractionService;
    private final RecognitionService recognitionService;

    public record Estatisticas(int pares, double minima, double media, double maxima) {
        static Estatisticas de(List<Double> distancias) {
            if (distancias.isEmpty()) {
                return new Estatisticas(0, 0, 0, 0);
            }
            double soma = 0;
            double min = Double.MAX_VALUE;
            double max = 0;
            for (double d : distancias) {
                soma += d;
                min = Math.min(min, d);
                max = Math.max(max, d);
            }
            return new Estatisticas(distancias.size(), min, soma / distancias.size(), max);
        }
    }

    public record Taxas(double limiar, double far, double frr) {
    }

    /**
     * {@code limiarEer} e {@code limiarFarZero} ficam nulos quando ainda não
     * há dados suficientes (ver {@code mensagem}).
     */
    public record Resultado(int usuarios, int capturas, Estatisticas genuinas, Estatisticas impostores,
                            Taxas atual, Taxas limiarEer, Taxas limiarFarZero, String mensagem) {
    }

    /** Uma captura cadastrada: de quem é e o vetor da Fase 4. */
    record Captura(Long usuarioId, float[] vetor) {
    }

    @Transactional(readOnly = true)
    public Resultado calibrar() {
        List<Captura> capturas = new ArrayList<>();
        for (FaceEmbedding embedding : faceEmbeddingRepository.findAll()) {
            capturas.add(new Captura(embedding.getUsuario().getId(),
                    featureExtractionService.desserializar(embedding.getVetor())));
        }
        return calibrar(capturas, recognitionService.getThreshold());
    }

    static Resultado calibrar(List<Captura> capturas, double limiarAtual) {
        List<Double> genuinas = new ArrayList<>();
        List<Double> impostores = new ArrayList<>();
        for (int i = 0; i < capturas.size(); i++) {
            for (int j = i + 1; j < capturas.size(); j++) {
                double distancia = RecognitionService.distanciaEuclidiana(capturas.get(i).vetor(), capturas.get(j).vetor());
                if (capturas.get(i).usuarioId().equals(capturas.get(j).usuarioId())) {
                    genuinas.add(distancia);
                } else {
                    impostores.add(distancia);
                }
            }
        }
        int usuarios = (int) capturas.stream().map(Captura::usuarioId).distinct().count();

        if (genuinas.isEmpty() || impostores.isEmpty()) {
            return new Resultado(usuarios, capturas.size(), Estatisticas.de(genuinas), Estatisticas.de(impostores),
                    null, null, null,
                    "Dados insuficientes: cadastre ao menos 2 pessoas, com 2 ou mais fotos de pelo menos uma delas.");
        }

        Taxas atual = taxas(limiarAtual, genuinas, impostores);

        // Os únicos pontos em que FAR/FRR mudam são as próprias distâncias medidas.
        List<Double> candidatos = new ArrayList<>(genuinas);
        candidatos.addAll(impostores);
        candidatos.sort(Double::compare);
        Taxas eer = null;
        for (double limiar : candidatos) {
            Taxas t = taxas(limiar, genuinas, impostores);
            if (eer == null || Math.abs(t.far() - t.frr()) < Math.abs(eer.far() - eer.frr())) {
                eer = t;
            }
        }

        // Logo abaixo do impostor mais parecido: nenhum par de impostor é aceito.
        double menorImpostor = impostores.stream().min(Double::compare).orElseThrow();
        Taxas farZero = taxas(Math.nextDown(menorImpostor), genuinas, impostores);

        return new Resultado(usuarios, capturas.size(), Estatisticas.de(genuinas), Estatisticas.de(impostores),
                atual, eer, farZero, null);
    }

    private static Taxas taxas(double limiar, List<Double> genuinas, List<Double> impostores) {
        long aceitosIndevidamente = impostores.stream().filter(d -> d <= limiar).count();
        long rejeitadosIndevidamente = genuinas.stream().filter(d -> d > limiar).count();
        return new Taxas(limiar, (double) aceitosIndevidamente / impostores.size(),
                (double) rejeitadosIndevidamente / genuinas.size());
    }
}
