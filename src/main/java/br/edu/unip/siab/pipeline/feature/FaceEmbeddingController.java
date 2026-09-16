package br.edu.unip.siab.pipeline.feature;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import static br.edu.unip.siab.config.OpenApiConfig.ESQUEMA_JWT;

/**
 * Consulta administrativa das fotos de referência salvas no cadastro (ver
 * Javadoc de {@link FaceEmbeddingImagem}). Protegido pelas mesmas regras de
 * "/api/admin/**" (JWT obrigatório) já definidas em SecurityConfig — não é
 * um endpoint público, exatamente porque a imagem é dado biométrico
 * sensível (RNF02 do escopo).
 */
@RestController
@RequestMapping("/api/admin/rostos")
@RequiredArgsConstructor
@Tag(name = "Embeddings Faciais", description = "Consulta da foto de referência salva em cada cadastro (Fase 4)")
@SecurityRequirement(name = ESQUEMA_JWT)
public class FaceEmbeddingController {

    private final FaceEmbeddingImagemRepository faceEmbeddingImagemRepository;

    @Operation(summary = "Obtém a foto de referência de um cadastro facial", description = "O id é o mesmo 'embeddingId' retornado por POST /api/enrollment no momento do cadastro.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Imagem retornada"),
            @ApiResponse(responseCode = "404", description = "Nenhuma imagem salva para esse embedding")
    })
    @GetMapping("/{embeddingId}/imagem")
    public ResponseEntity<byte[]> obterImagem(@Parameter(description = "Id do FaceEmbedding") @PathVariable Long embeddingId) {
        return faceEmbeddingImagemRepository.findByFaceEmbeddingId(embeddingId)
                .map(imagem -> ResponseEntity.ok()
                        .contentType(MediaType.parseMediaType(imagem.getContentType()))
                        .body(imagem.getImagem()))
                .orElseGet(() -> ResponseEntity.notFound().build());
    }
}
