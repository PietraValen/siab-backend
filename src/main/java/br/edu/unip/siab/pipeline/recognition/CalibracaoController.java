package br.edu.unip.siab.pipeline.recognition;

import br.edu.unip.siab.auditlog.AuditoriaAdminService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import static br.edu.unip.siab.config.OpenApiConfig.ESQUEMA_JWT;

/**
 * Relatório de calibração do limiar da Fase 5 (ver {@link CalibracaoService}).
 * Fica em /api/admin/**, então exige JWT de administrador como o resto do
 * painel. Só devolve distâncias agregadas, nunca vetores ou fotos.
 */
@RestController
@RequestMapping("/api/admin/calibracao")
@RequiredArgsConstructor
@Tag(name = "Calibração", description = "Sugestão do limiar de reconhecimento a partir dos cadastros reais")
@SecurityRequirement(name = ESQUEMA_JWT)
public class CalibracaoController {

    private final CalibracaoService calibracaoService;
    private final AuditoriaAdminService auditoria;

    @Operation(summary = "Mede as distâncias entre os cadastros e sugere o limiar",
            description = "Compara todas as capturas cadastradas entre si (mesma pessoa vs. pessoas diferentes) e devolve FAR/FRR do limiar atual, o limiar do Equal Error Rate e o maior limiar sem nenhuma falsa aceitação.")
    @GetMapping
    public CalibracaoService.Resultado calibrar() {
        auditoria.registrar("CALIBRACAO_CONSULTADA", "");
        return calibracaoService.calibrar();
    }
}
