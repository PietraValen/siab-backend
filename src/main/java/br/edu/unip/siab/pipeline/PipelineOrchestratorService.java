package br.edu.unip.siab.pipeline;

import br.edu.unip.siab.accesscontrol.AccessControlService;
import br.edu.unip.siab.accesscontrol.PinService;
import br.edu.unip.siab.auditlog.AccessLog;
import br.edu.unip.siab.auditlog.AccessLogService;
import br.edu.unip.siab.pipeline.acquisition.ValidadorDeImagem;
import br.edu.unip.siab.pipeline.feature.FaceEmbedding;
import br.edu.unip.siab.pipeline.feature.FaceEmbeddingImagem;
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
import lombok.RequiredArgsConstructor;
import org.bytedeco.opencv.opencv_core.Mat;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
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
    private final ValidadorDeImagem validadorDeImagem;
    private final PinService pinService;

    /** Mensagem genérica de negação: não diz ao terminal qual verificação falhou (seção 1.2 do roteiro). */
    public static final String MENSAGEM_NEGADO = "Acesso negado.";

    /**
     * Uma tentativa de reconhecimento já autenticada pelo terminal: os
     * frames na ordem de captura, o terminal que os enviou, o PIN digitado
     * (se a porta pede) e o IP de origem.
     */
    public record EntradaScan(List<byte[]> frames, Terminal terminal, String pin, String ip) {
    }

    /**
     * {@code motivo} e {@code similaridade} são só para o log de auditoria;
     * o terminal recebe apenas {@code mensagemPublica} (ver
     * RecognitionController).
     */
    public record ResultadoScan(boolean acessoConcedido, Optional<Usuario> usuario,
                                 double similaridade, String motivo, String mensagemPublica) {
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
        validadorDeImagem.validar(bytesOriginais);

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
     * Fluxo de RECONHECIMENTO (tela /scan): processa cada frame pelas fases
     * 1 a 3, verifica a vivacidade na sequência inteira (textura + piscada),
     * leva o frame mais nítido pelas fases 4 e 5, decide o acesso à porta do
     * terminal (nível exigido + PIN, se for porta de nível máximo) e
     * registra a tentativa no log de auditoria.
     */
    public ResultadoScan reconhecer(EntradaScan entrada) {
        List<Mat> rostos = new ArrayList<>();
        // Mesmo recorte de cada rosto, mas da imagem em cinza SEM
        // equalização: é nela que o liveness mede a textura (ver
        // PreprocessingService#paraCinza).
        List<Mat> rostosSemEqualizacao = new ArrayList<>();
        for (byte[] frame : entrada.frames()) {
            validadorDeImagem.validar(frame);
            Mat imagemBruta = decodificar(frame); // Fase 1 - Aquisição
            Mat preProcessada = preprocessingService.processar(imagemBruta); // Fase 2
            segmentationService.localizar(preProcessada).ifPresent(regiao -> { // Fase 3
                rostos.add(new Mat(preProcessada, regiao));
                rostosSemEqualizacao.add(new Mat(preprocessingService.paraCinza(imagemBruta), regiao));
            });
        }

        if (rostos.isEmpty()) {
            return negar(entrada, Optional.empty(), 0.0, "Nenhum rosto detectado.",
                    "Nenhum rosto detectado. Posicione o rosto no centro da câmera.");
        }

        LivenessService.Resultado vivacidade = livenessService.verificarSequencia(rostos, rostosSemEqualizacao);
        if (!vivacidade.aprovado()) {
            return negar(entrada, Optional.empty(), 0.0, "Falha na verificação de vivacidade: " + vivacidade.motivo(), MENSAGEM_NEGADO);
        }

        Mat rosto = rostos.get(livenessService.indiceMaisNitido(rostosSemEqualizacao));
        float[] vetor = featureExtractionService.extrair(rosto); // Fase 4
        var resultado = recognitionService.reconhecer(vetor); // Fase 5

        if (!resultado.reconhecido()) {
            return negar(entrada, Optional.empty(), resultado.similaridade(), "Usuário não reconhecido.", MENSAGEM_NEGADO);
        }

        Usuario usuario = resultado.usuario().get();
        Long nivelExigido = entrada.terminal() != null ? entrada.terminal().getNivelExigido().getId() : 1L;
        if (!accessControlService.possuiPermissao(usuario, nivelExigido)) {
            return negar(entrada, Optional.of(usuario), resultado.similaridade(),
                    "Nível de acesso insuficiente (exigido " + nivelExigido + ").",
                    "Nível de acesso insuficiente para esta porta.");
        }

        if (pinService.exigePin(entrada.terminal())) {
            PinService.Verificacao pin = pinService.verificar(usuario, entrada.pin());
            if (pin != PinService.Verificacao.OK) {
                return negar(entrada, Optional.of(usuario), resultado.similaridade(), "Segundo fator: PIN " + pin + ".",
                        switch (pin) {
                            case AUSENTE -> "Digite o PIN para esta porta.";
                            case INCORRETO -> "PIN incorreto.";
                            case BLOQUEADO -> "PIN bloqueado por excesso de tentativas. Procure a administração.";
                            default -> MENSAGEM_NEGADO;
                        });
            }
        }

        accessLogService.registrar(new AccessLogService.Tentativa(Optional.of(usuario), AccessLog.Resultado.CONCEDIDO,
                resultado.similaridade(), "Acesso concedido.", entrada.terminal(), entrada.ip()));
        return new ResultadoScan(true, Optional.of(usuario), resultado.similaridade(), "Acesso concedido.", "Acesso concedido.");
    }

    private ResultadoScan negar(EntradaScan entrada, Optional<Usuario> usuario, double similaridade,
                                String motivo, String mensagemPublica) {
        accessLogService.registrar(new AccessLogService.Tentativa(usuario, AccessLog.Resultado.NEGADO, similaridade,
                motivo, entrada.terminal(), entrada.ip()));
        return new ResultadoScan(false, usuario, similaridade, motivo, mensagemPublica);
    }

    private Mat decodificar(byte[] bytes) {
        Mat buffer = new Mat(bytes);
        Mat imagem = imdecode(buffer, IMREAD_COLOR);
        if (imagem == null || imagem.empty()) {
            throw new IllegalArgumentException("Imagem corrompida ou ilegível.");
        }
        return imagem;
    }
}
