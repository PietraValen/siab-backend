package br.edu.unip.siab.pipeline;

import br.edu.unip.siab.accesscontrol.AccessControlService;
import br.edu.unip.siab.auditlog.AccessLog;
import br.edu.unip.siab.auditlog.AccessLogService;
import br.edu.unip.siab.pipeline.feature.FaceEmbedding;
import br.edu.unip.siab.pipeline.feature.FaceEmbeddingImagem;
import br.edu.unip.siab.pipeline.feature.FaceEmbeddingImagemRepository;
import br.edu.unip.siab.pipeline.feature.FaceEmbeddingRepository;
import br.edu.unip.siab.pipeline.feature.FeatureExtractionService;
import br.edu.unip.siab.pipeline.liveness.LivenessService;
import br.edu.unip.siab.pipeline.preprocessing.PreprocessingService;
import br.edu.unip.siab.pipeline.recognition.RecognitionService;
import br.edu.unip.siab.pipeline.segmentation.SegmentationService;
import br.edu.unip.siab.user.Usuario;
import br.edu.unip.siab.user.UsuarioService;
import lombok.RequiredArgsConstructor;
import org.bytedeco.opencv.opencv_core.Mat;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.Optional;

import static org.bytedeco.opencv.global.opencv_imgcodecs.IMREAD_COLOR;
import static org.bytedeco.opencv.global.opencv_imgcodecs.imdecode;

/**
 * Amarra as 5 fases do pipeline exatamente na ordem descrita na seção 5.5
 * do escopo ("Fluxo Completo de Reconhecimento, Ponta a Ponta"):
 * <p>
 * 1. Aquisição -> 2. Pré-processamento -> 3. Segmentação -> [liveness] ->
 * 4. Extração de Características -> 5. Reconhecimento/Classificação ->
 * decisão de acesso -> registro em log.
 * <p>
 * Os controllers (EnrollmentController / RecognitionController) não devem
 * conhecer OpenCV nem os detalhes de cada fase — eles só chamam os métodos
 * públicos desta classe.
 */
@Service
@RequiredArgsConstructor
public class PipelineOrchestratorService {

    private static final Logger log = LoggerFactory.getLogger(PipelineOrchestratorService.class);

    private final PreprocessingService preprocessingService;
    private final SegmentationService segmentationService;
    private final LivenessService livenessService;
    private final FeatureExtractionService featureExtractionService;
    private final RecognitionService recognitionService;
    private final AccessControlService accessControlService;
    private final FaceEmbeddingRepository faceEmbeddingRepository;
    private final FaceEmbeddingImagemRepository faceEmbeddingImagemRepository;
    private final AccessLogService accessLogService;
    private final UsuarioService usuarioService;

    public record ResultadoScan(boolean acessoConcedido, Optional<Usuario> usuario,
                                 double similaridade, String motivo) {
    }

    /**
     * Fluxo de CADASTRO (tela /enroll): processa a imagem até a Fase 4,
     * salva o embedding gerado e a foto original de referência (ver
     * Javadoc de {@link FaceEmbeddingImagem}), associados ao usuário
     * informado. Transacional para que o embedding e sua imagem sejam
     * salvos atomicamente — nunca um sem o outro.
     */
    @Transactional
    public FaceEmbedding cadastrarRosto(Long usuarioId, MultipartFile imagem) throws IOException {
        Usuario usuario = usuarioService.buscarPorId(usuarioId);
        byte[] bytesOriginais = imagem.getBytes();

        Mat imagemBruta = decodificar(bytesOriginais); // Fase 1 - Aquisição
        Mat preProcessada = preprocessingService.processar(imagemBruta); // Fase 2

        Mat rosto = segmentationService.segmentar(preProcessada) // Fase 3
                .orElseThrow(() -> new IllegalArgumentException("Nenhum rosto detectado na imagem enviada."));

        float[] vetor = featureExtractionService.extrair(rosto); // Fase 4

        FaceEmbedding embedding = new FaceEmbedding();
        embedding.setUsuario(usuario);
        embedding.setVetor(featureExtractionService.serializar(vetor));
        embedding.setAlgoritmo(featureExtractionService.algoritmoUtilizado());
        embedding = faceEmbeddingRepository.save(embedding);

        salvarImagemDeReferencia(embedding, bytesOriginais, imagem.getContentType());

        return embedding;
    }

    private void salvarImagemDeReferencia(FaceEmbedding embedding, byte[] bytes, String contentType) {
        FaceEmbeddingImagem imagemDeReferencia = new FaceEmbeddingImagem();
        imagemDeReferencia.setFaceEmbedding(embedding);
        imagemDeReferencia.setImagem(bytes);
        imagemDeReferencia.setContentType(contentType != null ? contentType : "application/octet-stream");
        faceEmbeddingImagemRepository.save(imagemDeReferencia);
    }

    /**
     * Fluxo de RECONHECIMENTO (tela /scan): processa a imagem por todas as
     * 5 fases, decide o acesso e registra a tentativa no log de auditoria.
     */
    public ResultadoScan reconhecer(MultipartFile imagem) throws IOException {
        Mat imagemBruta = decodificar(imagem.getBytes()); // Fase 1 - Aquisição
        Mat preProcessada = preprocessingService.processar(imagemBruta); // Fase 2

        Optional<Mat> rostoOpt = segmentationService.segmentar(preProcessada); // Fase 3
        if (rostoOpt.isEmpty()) {
            accessLogService.registrar(Optional.empty(), AccessLog.Resultado.NEGADO, 0.0);
            return new ResultadoScan(false, Optional.empty(), 0.0, "Nenhum rosto detectado.");
        }
        Mat rosto = rostoOpt.get();

        if (!livenessService.ehRostoReal(rosto)) {
            accessLogService.registrar(Optional.empty(), AccessLog.Resultado.NEGADO, 0.0);
            return new ResultadoScan(false, Optional.empty(), 0.0, "Falha na verificação de vivacidade.");
        }

        float[] vetor = featureExtractionService.extrair(rosto); // Fase 4
        var resultado = recognitionService.reconhecer(vetor); // Fase 5

        if (!resultado.reconhecido()) {
            accessLogService.registrar(Optional.empty(), AccessLog.Resultado.NEGADO, resultado.similaridade());
            return new ResultadoScan(false, Optional.empty(), resultado.similaridade(), "Usuário não reconhecido.");
        }

        Usuario usuario = resultado.usuario().get();
        // TODO: quando houver múltiplos recursos protegidos por nível, passar
        // aqui o nível mínimo exigido pelo recurso solicitado em vez de 1L.
        boolean autorizado = accessControlService.possuiPermissao(usuario, 1L);

        var resultadoLog = autorizado ? AccessLog.Resultado.CONCEDIDO : AccessLog.Resultado.NEGADO;
        accessLogService.registrar(Optional.of(usuario), resultadoLog, resultado.similaridade());

        String motivo = autorizado ? "Acesso concedido." : "Nível de acesso insuficiente.";
        return new ResultadoScan(autorizado, Optional.of(usuario), resultado.similaridade(), motivo);
    }

    private Mat decodificar(byte[] bytes) {
        Mat buffer = new Mat(bytes);
        return imdecode(buffer, IMREAD_COLOR);
    }
}
