package br.edu.unip.siab.auditlog;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

import static br.edu.unip.siab.config.OpenApiConfig.ESQUEMA_JWT;

/**
 * Módulo "audit-log" (RF05) — consulta do histórico de tentativas de
 * autenticação, consumido pela tela /admin/logs do front-end.
 */
@RestController
@RequestMapping("/api/admin/logs")
@RequiredArgsConstructor
@Tag(name = "Logs de Auditoria", description = "Histórico de tentativas de autenticação (RF05)")
@SecurityRequirement(name = ESQUEMA_JWT)
public class AccessLogController {

    private final AccessLogService accessLogService;

    @Operation(summary = "Lista todas as tentativas de acesso, mais recentes primeiro")
    @GetMapping
    public List<AccessLogResponse> listarTodos() {
        return accessLogService.listarTodos().stream()
                .map(AccessLogResponse::from)
                .toList();
    }

    @Operation(summary = "Lista as tentativas de acesso de um usuário específico")
    @GetMapping("/usuario/{usuarioId}")
    public List<AccessLogResponse> listarPorUsuario(@Parameter(description = "Id do usuário") @PathVariable Long usuarioId) {
        return accessLogService.listarPorUsuario(usuarioId).stream()
                .map(AccessLogResponse::from)
                .toList();
    }
}
