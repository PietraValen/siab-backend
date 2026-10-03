package br.edu.unip.siab.auditlog;

import br.edu.unip.siab.auditlog.cadeia.CadeiaAuditoriaService;
import br.edu.unip.siab.auditlog.cadeia.CadeiaAuditoriaService.RelatorioIntegridade;
import br.edu.unip.siab.crypto.AssinaturaHibridaService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDateTime;
import java.util.List;

import static br.edu.unip.siab.config.OpenApiConfig.ESQUEMA_JWT;

/**
 * Integridade do log de auditoria (cadeia de hashes + selos com assinatura
 * híbrida Ed25519 + ML-DSA-65) e trilha de ações administrativas.
 */
@RestController
@RequestMapping("/api/admin/auditoria")
@RequiredArgsConstructor
@Tag(name = "Integridade da Auditoria", description = "Cadeia de hashes, selos assinados e ações administrativas")
@SecurityRequirement(name = ESQUEMA_JWT)
public class AuditoriaController {

    private final CadeiaAuditoriaService cadeia;
    private final AssinaturaHibridaService assinatura;
    private final AuditoriaAdminService auditoria;

    public record AcaoAdministrativaResponse(Long id, LocalDateTime dataHora, String administrador, String acao,
                                             String detalhe, String ip) {
        static AcaoAdministrativaResponse from(AcaoAdministrativa a) {
            return new AcaoAdministrativaResponse(a.getId(), a.getDataHora(), a.getAdministrador(), a.getAcao(),
                    a.getDetalhe(), a.getIp());
        }
    }

    @Operation(summary = "Recalcula as cadeias e confere todos os selos assinados")
    @GetMapping("/verificacao")
    public RelatorioIntegridade verificar() {
        return cadeia.verificar();
    }

    @Operation(summary = "Sela agora o último registro de cada cadeia (assinatura Ed25519 + ML-DSA-65)")
    @PostMapping("/selar")
    public RelatorioIntegridade selar() {
        cadeia.selar();
        auditoria.registrar("AUDITORIA_SELADA", null);
        return cadeia.verificar();
    }

    @Operation(summary = "Chaves públicas que assinam os selos, para verificação fora do sistema")
    @GetMapping("/chaves-publicas")
    public AssinaturaHibridaService.ChavesPublicas chavesPublicas() {
        return assinatura.chavesPublicas();
    }

    @Operation(summary = "Últimas 200 ações administrativas, mais recentes primeiro")
    @GetMapping("/acoes")
    public List<AcaoAdministrativaResponse> acoes() {
        return auditoria.listarRecentes().stream().map(AcaoAdministrativaResponse::from).toList();
    }
}
