package br.edu.unip.siab.pipeline.acquisition;

import br.edu.unip.siab.pipeline.PipelineOrchestratorService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

/**
 * Endpoint consumido pela tela /scan do front-end: recebe um frame da
 * webcam e roda o pipeline completo (fases 1 a 5 + liveness + decisão de
 * acesso), retornando o resultado para exibição imediata (RF02/RF03/RF04).
 * <p>
 * Público (sem JWT) por SecurityConfig — é a própria tela de reconhecimento
 * no cofre, não uma rota administrativa.
 */
@RestController
@RequestMapping("/api/recognition")
@RequiredArgsConstructor
@Tag(name = "Reconhecimento Facial", description = "Fluxo completo de autenticação (fases 1 a 5 + liveness) — tela /scan")
public class RecognitionController {

    private final PipelineOrchestratorService pipelineOrchestratorService;

    @Operation(summary = "Tenta reconhecer um rosto e decidir o acesso", description = "Roda pré-processamento, segmentação, verificação de vivacidade, extração de características e comparação contra os embeddings cadastrados; registra a tentativa no log de auditoria independentemente do resultado.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Tentativa processada (o campo acessoConcedido indica o resultado — negado não é erro HTTP)")
    })
    @PostMapping(value = "/scan", consumes = "multipart/form-data")
    public ResponseEntity<?> reconhecer(@Parameter(description = "Frame capturado pela webcam") @RequestParam("imagem") MultipartFile imagem) throws IOException {
        var resultado = pipelineOrchestratorService.reconhecer(imagem);

        // HashMap (não Map.of): quando o rosto não é reconhecido, "usuario"
        // é null por contrato com o front-end (ver tipo ScanResult), e
        // Map.of lança NullPointerException em qualquer valor nulo.
        Map<String, Object> corpo = new HashMap<>();
        corpo.put("acessoConcedido", resultado.acessoConcedido());
        corpo.put("usuario", resultado.usuario().map(u -> Map.of(
                "id", u.getId(),
                "nome", u.getNome(),
                "nivelAcesso", u.getNivelAcesso().getNome()
        )).orElse(null));
        corpo.put("similaridade", resultado.similaridade());
        corpo.put("motivo", resultado.motivo());

        return ResponseEntity.ok(corpo);
    }
}
