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
import java.util.Map;

/**
 * Módulo "image-acquisition" (Fase 1 do pipeline) aplicado ao fluxo de
 * CADASTRO. Consumido pela tela /enroll do front-end Next.js.
 * <p>
 * Recebe o frame capturado pela webcam (getUserMedia) via multipart/form-data
 * e delega ao orquestrador, que executa as fases 2, 3 e 4 e salva o
 * embedding gerado.
 * <p>
 * Público (sem JWT) por SecurityConfig — é a própria tela de cadastro
 * facial, não uma rota administrativa.
 */
@RestController
@RequestMapping("/api/enrollment")
@RequiredArgsConstructor
@Tag(name = "Cadastro Facial", description = "Fase 1 (aquisição) do fluxo de enrollment — tela /enroll")
public class EnrollmentController {

    private final PipelineOrchestratorService pipelineOrchestratorService;

    @Operation(summary = "Cadastra a captura facial de um usuário", description = "Roda as fases 2 a 4 do pipeline (pré-processamento, segmentação, extração LBPH) sobre a imagem enviada e salva o embedding resultante, associado ao usuário informado.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Rosto cadastrado com sucesso"),
            @ApiResponse(responseCode = "400", description = "Nenhum rosto detectado na imagem enviada"),
            @ApiResponse(responseCode = "404", description = "Usuário não encontrado")
    })
    @PostMapping(consumes = "multipart/form-data")
    public ResponseEntity<?> cadastrarRosto(@Parameter(description = "Id do usuário previamente cadastrado em /api/admin/usuarios") @RequestParam Long usuarioId,
                                             @Parameter(description = "Foto do rosto capturada pela webcam") @RequestParam("imagem") MultipartFile imagem) throws IOException {
        var embedding = pipelineOrchestratorService.cadastrarRosto(usuarioId, imagem);
        return ResponseEntity.ok(Map.of(
                "embeddingId", embedding.getId(),
                "algoritmo", embedding.getAlgoritmo(),
                "mensagem", "Rosto cadastrado com sucesso."
        ));
    }
}
