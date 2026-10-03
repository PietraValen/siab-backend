package br.edu.unip.siab.pipeline;

import br.edu.unip.siab.accesscontrol.AccessControlService;
import br.edu.unip.siab.accesscontrol.PinService;
import br.edu.unip.siab.accesslevel.NivelAcesso;
import br.edu.unip.siab.auditlog.AccessLog;
import br.edu.unip.siab.auditlog.AccessLogService;
import br.edu.unip.siab.pipeline.acquisition.ValidadorDeImagem;
import br.edu.unip.siab.pipeline.feature.FaceEmbeddingImagemRepository;
import br.edu.unip.siab.pipeline.feature.FaceEmbeddingRepository;
import br.edu.unip.siab.pipeline.feature.FeatureExtractionService;
import br.edu.unip.siab.pipeline.liveness.LivenessService;
import br.edu.unip.siab.pipeline.preprocessing.PreprocessingService;
import br.edu.unip.siab.pipeline.recognition.RecognitionService;
import br.edu.unip.siab.pipeline.segmentation.SegmentationService;
import br.edu.unip.siab.terminal.Terminal;
import br.edu.unip.siab.user.Usuario;
import br.edu.unip.siab.user.UsuarioService;
import org.bytedeco.opencv.opencv_core.Mat;
import org.bytedeco.opencv.opencv_core.Rect;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Decisão de acesso do /scan com as camadas novas: vivacidade
 * multi-frame, nível exigido pela porta do terminal e PIN nas portas de
 * nível máximo — e o que vai para o terminal (mensagem genérica) versus o
 * que vai para a auditoria (motivo real).
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class PipelineOrchestratorServiceTest {

    @Mock private PreprocessingService preprocessingService;
    @Mock private SegmentationService segmentationService;
    @Mock private LivenessService livenessService;
    @Mock private FeatureExtractionService featureExtractionService;
    @Mock private RecognitionService recognitionService;
    @Mock private AccessControlService accessControlService;
    @Mock private FaceEmbeddingRepository faceEmbeddingRepository;
    @Mock private FaceEmbeddingImagemRepository faceEmbeddingImagemRepository;
    @Mock private AccessLogService accessLogService;
    @Mock private UsuarioService usuarioService;
    @Mock private ValidadorDeImagem validadorDeImagem;
    @Mock private PinService pinService;

    @InjectMocks
    private PipelineOrchestratorService orquestrador;

    private final Usuario ministro = new Usuario();
    private List<byte[]> frames;

    @BeforeEach
    void setUp() throws Exception {
        ByteArrayOutputStream saida = new ByteArrayOutputStream();
        ImageIO.write(new BufferedImage(32, 32, BufferedImage.TYPE_INT_RGB), "jpg", saida);
        frames = List.of(saida.toByteArray(), saida.toByteArray(), saida.toByteArray());

        ministro.setId(3L);
        ministro.setNome("Ministra");
        when(preprocessingService.processar(any())).thenAnswer(i -> i.getArgument(0));
        when(preprocessingService.paraCinza(any())).thenAnswer(i -> i.getArgument(0));
        when(segmentationService.localizar(any())).thenReturn(Optional.of(new Rect(0, 0, 16, 16)));
        when(livenessService.verificarSequencia(anyList(), anyList())).thenReturn(new LivenessService.Resultado(true, "ok"));
        when(featureExtractionService.extrair(any())).thenReturn(new float[]{0.1f});
        when(recognitionService.reconhecer(any())).thenReturn(
                new RecognitionService.ResultadoReconhecimento(Optional.of(ministro), 0.2));
        when(accessControlService.possuiPermissao(any(), anyLong())).thenReturn(true);
    }

    private Terminal portaDoNivel(long nivelId) {
        NivelAcesso nivel = new NivelAcesso();
        nivel.setId(nivelId);
        Terminal terminal = new Terminal();
        terminal.setId(1L);
        terminal.setNivelExigido(nivel);
        return terminal;
    }

    private AccessLogService.Tentativa tentativaRegistrada() {
        ArgumentCaptor<AccessLogService.Tentativa> captor = ArgumentCaptor.forClass(AccessLogService.Tentativa.class);
        verify(accessLogService).registrar(captor.capture());
        return captor.getValue();
    }

    @Test
    void portaDeMinistroComPinErradoNega() {
        Terminal porta = portaDoNivel(3L);
        when(pinService.exigePin(porta)).thenReturn(true);
        when(pinService.verificar(ministro, "0000")).thenReturn(PinService.Verificacao.INCORRETO);

        var resultado = orquestrador.reconhecer(new PipelineOrchestratorService.EntradaScan(frames, porta, "0000", "10.0.0.9"));

        assertThat(resultado.acessoConcedido()).isFalse();
        assertThat(resultado.mensagemPublica()).isEqualTo("PIN incorreto.");
        var registro = tentativaRegistrada();
        assertThat(registro.resultado()).isEqualTo(AccessLog.Resultado.NEGADO);
        assertThat(registro.terminal()).isSameAs(porta);
        assertThat(registro.ip()).isEqualTo("10.0.0.9");
    }

    @Test
    void portaDeMinistroComPinCertoConcede() {
        Terminal porta = portaDoNivel(3L);
        when(pinService.exigePin(porta)).thenReturn(true);
        when(pinService.verificar(ministro, "4321")).thenReturn(PinService.Verificacao.OK);

        var resultado = orquestrador.reconhecer(new PipelineOrchestratorService.EntradaScan(frames, porta, "4321", null));

        assertThat(resultado.acessoConcedido()).isTrue();
        verify(accessControlService).possuiPermissao(ministro, 3L);
    }

    @Test
    void falhaDeVivacidadeNaoContaQualVerificacaoFalhou() {
        when(livenessService.verificarSequencia(anyList(), anyList()))
                .thenReturn(new LivenessService.Resultado(false, "Nenhuma piscada detectada na sequência."));

        var resultado = orquestrador.reconhecer(new PipelineOrchestratorService.EntradaScan(frames, portaDoNivel(1L), null, null));

        assertThat(resultado.mensagemPublica()).isEqualTo(PipelineOrchestratorService.MENSAGEM_NEGADO);
        assertThat(tentativaRegistrada().motivo()).contains("piscada");
    }

    @Test
    void naoReconhecidoTambemRecebeMensagemGenerica() {
        when(recognitionService.reconhecer(any())).thenReturn(
                new RecognitionService.ResultadoReconhecimento(Optional.empty(), 0.9));

        var resultado = orquestrador.reconhecer(new PipelineOrchestratorService.EntradaScan(frames, portaDoNivel(1L), null, null));

        assertThat(resultado.mensagemPublica()).isEqualTo(PipelineOrchestratorService.MENSAGEM_NEGADO);
        assertThat(tentativaRegistrada().motivo()).isEqualTo("Usuário não reconhecido.");
    }
}
