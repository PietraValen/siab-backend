package br.edu.unip.siab.pipeline.recognition;

import br.edu.unip.siab.pipeline.feature.FaceEmbedding;
import br.edu.unip.siab.pipeline.feature.FaceEmbeddingRepository;
import br.edu.unip.siab.pipeline.feature.FeatureExtractionService;
import br.edu.unip.siab.user.Usuario;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Optional;

/**
 * Fase 5 — Reconhecimento e Classificação (seção 3.2 e 5.2 do escopo).
 * <p>
 * Compara o vetor de características extraído na Fase 4 com os vetores
 * previamente cadastrados no banco (tabela face_embeddings), usando uma
 * métrica de similaridade e um limiar (threshold) de decisão.
 */
@Service
@RequiredArgsConstructor
public class RecognitionService {

    private static final Logger log = LoggerFactory.getLogger(RecognitionService.class);

    private final FaceEmbeddingRepository faceEmbeddingRepository;
    private final FeatureExtractionService featureExtractionService;

    /**
     * Limiar de decisão: quanto MENOR a distância, mais parecido o rosto.
     * TODO: calibrar este valor experimentalmente com o dataset real do
     * grupo (poucos usuários de teste vs. impostores) antes da entrega.
     */
    @Value("${siab.pipeline.recognition-threshold:0.35}")
    private double threshold;

    public record ResultadoReconhecimento(Optional<Usuario> usuario, double similaridade) {
        public boolean reconhecido() {
            return usuario.isPresent();
        }
    }

    public ResultadoReconhecimento reconhecer(float[] vetorCapturado) {
        log.debug("Fase 5 - Reconhecimento: comparando com {} embeddings cadastrados",
                faceEmbeddingRepository.count());

        List<FaceEmbedding> candidatos = faceEmbeddingRepository.findAll();

        Usuario melhorCandidato = null;
        double menorDistancia = Double.MAX_VALUE;

        for (FaceEmbedding candidato : candidatos) {
            float[] vetorCandidato = featureExtractionService.desserializar(candidato.getVetor());
            double distancia = distanciaEuclidiana(vetorCapturado, vetorCandidato);

            if (distancia < menorDistancia) {
                menorDistancia = distancia;
                melhorCandidato = candidato.getUsuario();
            }
        }

        if (melhorCandidato != null && menorDistancia <= threshold) {
            log.info("Fase 5 - Reconhecimento: usuário {} reconhecido (distância={})",
                    melhorCandidato.getId(), menorDistancia);
            return new ResultadoReconhecimento(Optional.of(melhorCandidato), menorDistancia);
        }

        log.info("Fase 5 - Reconhecimento: nenhum usuário reconhecido (menor distância={})", menorDistancia);
        return new ResultadoReconhecimento(Optional.empty(), menorDistancia);
    }

    private double distanciaEuclidiana(float[] a, float[] b) {
        int tamanho = Math.min(a.length, b.length);
        double soma = 0;
        for (int i = 0; i < tamanho; i++) {
            double diff = a[i] - b[i];
            soma += diff * diff;
        }
        return Math.sqrt(soma);
    }
}
