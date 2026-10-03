package br.edu.unip.siab.pipeline.acquisition;

import br.edu.unip.siab.accesscontrol.PinService;
import br.edu.unip.siab.auditlog.AccessLog;
import br.edu.unip.siab.auditlog.AccessLogService;
import br.edu.unip.siab.pipeline.PipelineOrchestratorService;
import br.edu.unip.siab.pipeline.PipelineOrchestratorService.EntradaScan;
import br.edu.unip.siab.terminal.DesafioService;
import br.edu.unip.siab.terminal.Terminal;
import br.edu.unip.siab.terminal.TerminalAutenticacaoService;
import br.edu.unip.siab.terminal.TerminalNaoAutorizadoException;
import br.edu.unip.siab.terminal.dto.DesafioResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Endpoints consumidos pela tela /scan do front-end (RF02/RF03/RF04).
 * <p>
 * Públicos para o JWT do painel (o quiosque não tem admin logado), mas
 * <b>não anônimos</b>: toda tentativa precisa vir assinada por um terminal
 * cadastrado (ver {@link TerminalAutenticacaoService}) — sem isso,
 * qualquer um podia mandar uma foto direto para a API, sem passar pela
 * câmera, e o liveness não servia de nada.
 * <p>
 * Fluxo do quiosque: GET /desafio → captura a sequência de frames (pede
 * "pisque") → POST /scan com os frames, os headers de assinatura e, nas
 * portas de nível máximo, o PIN.
 */
@RestController
@RequestMapping("/api/recognition")
@RequiredArgsConstructor
@Tag(name = "Reconhecimento Facial", description = "Fluxo completo de autenticação (fases 1 a 5 + liveness) — tela /scan")
public class RecognitionController {

    private final PipelineOrchestratorService pipelineOrchestratorService;
    private final TerminalAutenticacaoService terminalAutenticacaoService;
    private final DesafioService desafioService;
    private final AccessLogService accessLogService;
    private final PinService pinService;

    @Value("${siab.pipeline.frames-maximos:12}")
    private int framesMaximos = 12;

    @Operation(summary = "Emite um desafio de uso único para o terminal",
            description = "O terminal inclui o nonce na assinatura HMAC do próximo POST /scan. Vale por 60 s e só uma vez.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Desafio emitido"),
            @ApiResponse(responseCode = "401", description = "Terminal desconhecido ou revogado")
    })
    @GetMapping("/desafio")
    public DesafioResponse desafio(@RequestHeader(value = "X-Terminal-Id", required = false) String terminalId) {
        Terminal terminal = terminalAutenticacaoService.buscarAtivo(terminalId);
        var desafio = desafioService.emitir(terminal.getId());
        return new DesafioResponse(desafio.nonce(), desafio.expiraEm(), terminal.getNome(),
                terminal.getNivelExigido().getId(), terminal.getNivelExigido().getNome(), pinService.exigePin(terminal));
    }

    @Operation(summary = "Tenta reconhecer um rosto e decidir o acesso",
            description = "Recebe a sequência de frames capturada enquanto o quiosque pede uma piscada; roda pré-processamento, segmentação, vivacidade (textura + piscada), extração de características e comparação; registra a tentativa no log de auditoria independentemente do resultado. A resposta traz só o necessário para a tela — similaridade e motivo detalhado ficam apenas na auditoria.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Tentativa processada (acessoConcedido indica o resultado — negado não é erro HTTP)"),
            @ApiResponse(responseCode = "400", description = "Imagem inválida ou número de frames fora do limite"),
            @ApiResponse(responseCode = "401", description = "Assinatura do terminal ausente ou inválida")
    })
    @PostMapping(value = "/scan", consumes = "multipart/form-data")
    public ResponseEntity<Map<String, Object>> reconhecer(
            @Parameter(description = "Frames capturados pela webcam, na ordem de captura") @RequestParam(value = "imagens", required = false) List<MultipartFile> imagens,
            @Parameter(description = "Frame único (compatibilidade; reprovado no liveness se a piscada for exigida)") @RequestParam(value = "imagem", required = false) MultipartFile imagem,
            @Parameter(description = "PIN, nas portas que exigem segundo fator") @RequestParam(value = "pin", required = false) String pin,
            @RequestHeader(value = "X-Terminal-Id", required = false) String terminalId,
            @RequestHeader(value = "X-Desafio", required = false) String nonce,
            @RequestHeader(value = "X-Timestamp", required = false) String timestamp,
            @RequestHeader(value = "X-Assinatura", required = false) String assinatura,
            HttpServletRequest request) throws IOException {

        List<byte[]> frames = lerFrames(imagens, imagem);
        String ip = request.getRemoteAddr();

        Terminal terminal;
        try {
            terminal = terminalAutenticacaoService.autenticar(
                    new TerminalAutenticacaoService.Credenciais(terminalId, nonce, timestamp, assinatura), frames, pin);
        } catch (TerminalNaoAutorizadoException e) {
            accessLogService.registrar(new AccessLogService.Tentativa(Optional.empty(), AccessLog.Resultado.NEGADO,
                    null, "Terminal não autorizado: " + e.getMessage(), null, ip));
            throw e;
        }

        var resultado = pipelineOrchestratorService.reconhecer(new EntradaScan(frames, terminal, pin, ip));

        // HashMap (não Map.of): "usuario" é null por contrato quando o acesso
        // é negado (ver tipo ScanResult do front-end), e Map.of não aceita null.
        Map<String, Object> corpo = new HashMap<>();
        corpo.put("acessoConcedido", resultado.acessoConcedido());
        corpo.put("usuario", resultado.acessoConcedido()
                ? resultado.usuario().map(u -> Map.of("nome", u.getNome(), "nivelAcesso", u.getNivelAcesso().getNome())).orElse(null)
                : null);
        corpo.put("mensagem", resultado.mensagemPublica());
        return ResponseEntity.ok(corpo);
    }

    private List<byte[]> lerFrames(List<MultipartFile> imagens, MultipartFile imagem) throws IOException {
        List<MultipartFile> arquivos = new ArrayList<>();
        if (imagens != null) {
            arquivos.addAll(imagens);
        }
        if (imagem != null) {
            arquivos.add(imagem);
        }
        if (arquivos.isEmpty() || arquivos.size() > framesMaximos) {
            throw new IllegalArgumentException("Envie de 1 a " + framesMaximos + " frames.");
        }
        List<byte[]> frames = new ArrayList<>();
        for (MultipartFile arquivo : arquivos) {
            frames.add(arquivo.getBytes());
        }
        return frames;
    }
}
