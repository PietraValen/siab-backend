package br.edu.unip.siab.pipeline.recognition;

import br.edu.unip.siab.accesslevel.NivelAcesso;
import br.edu.unip.siab.pipeline.feature.FaceEmbedding;
import br.edu.unip.siab.pipeline.feature.FaceEmbeddingRepository;
import br.edu.unip.siab.pipeline.feature.FeatureExtractionService;
import br.edu.unip.siab.user.Usuario;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

/**
 * Cobre a lógica de decisão da Fase 5 (comparação por distância euclidiana +
 * threshold) isoladamente, usando embeddings sintéticos — não depende de
 * OpenCV nem de imagens reais. Serve de referência para calibrar o
 * threshold real depois que a Fase 4 estiver implementada (ver CLAUDE.md).
 */
@ExtendWith(MockitoExtension.class)
class RecognitionServiceTest {

    @Mock
    private FaceEmbeddingRepository faceEmbeddingRepository;

    private final FeatureExtractionService featureExtractionService = new FeatureExtractionService();

    private RecognitionService service;

    @BeforeEach
    void setUp() {
        service = new RecognitionService(faceEmbeddingRepository, featureExtractionService);
        ReflectionTestUtils.setField(service, "threshold", 0.5); // mesmo default do application.yml
    }

    private Usuario usuario(long id) {
        Usuario u = new Usuario();
        u.setId(id);
        u.setNome("Usuário " + id);
        u.setNivelAcesso(new NivelAcesso());
        return u;
    }

    private FaceEmbedding embeddingCom(Usuario usuario, float[] vetor) {
        FaceEmbedding e = new FaceEmbedding();
        e.setUsuario(usuario);
        e.setVetor(featureExtractionService.serializar(vetor));
        e.setAlgoritmo("TESTE");
        return e;
    }

    @Test
    void reconheceUsuarioQuandoVetorEstaDentroDoThreshold() {
        Usuario alice = usuario(1L);
        float[] vetorCadastrado = { 1.0f, 1.0f, 1.0f };
        float[] vetorCapturado = { 1.05f, 0.98f, 1.02f }; // bem próximo

        when(faceEmbeddingRepository.findAll())
                .thenReturn(List.of(embeddingCom(alice, vetorCadastrado)));

        var resultado = service.reconhecer(vetorCapturado);

        assertThat(resultado.reconhecido()).isTrue();
        assertThat(resultado.usuario()).contains(alice);
    }

    @Test
    void naoReconheceQuandoDistanciaEstaAcimaDoThreshold() {
        Usuario alice = usuario(1L);
        float[] vetorCadastrado = { 1.0f, 1.0f, 1.0f };
        float[] vetorCapturado = { 10.0f, -5.0f, 8.0f }; // bem distante

        when(faceEmbeddingRepository.findAll())
                .thenReturn(List.of(embeddingCom(alice, vetorCadastrado)));

        var resultado = service.reconhecer(vetorCapturado);

        assertThat(resultado.reconhecido()).isFalse();
        assertThat(resultado.usuario()).isEmpty();
    }

    @Test
    void escolheOCandidatoMaisProximoQuandoHaMultiplosCadastrados() {
        Usuario alice = usuario(1L);
        Usuario bruno = usuario(2L);
        float[] vetorCapturado = { 1.0f, 1.0f, 1.0f };

        when(faceEmbeddingRepository.findAll()).thenReturn(List.of(
                embeddingCom(alice, new float[]{ 5.0f, 5.0f, 5.0f }),   // longe
                embeddingCom(bruno, new float[]{ 1.02f, 0.99f, 1.01f }) // perto
        ));

        var resultado = service.reconhecer(vetorCapturado);

        assertThat(resultado.usuario()).contains(bruno);
    }

    @Test
    void semNenhumEmbeddingCadastradoNaoReconheceNinguem() {
        when(faceEmbeddingRepository.findAll()).thenReturn(List.of());

        var resultado = service.reconhecer(new float[]{ 1.0f, 1.0f, 1.0f });

        assertThat(resultado.reconhecido()).isFalse();
    }
}
