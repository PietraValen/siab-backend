package br.edu.unip.siab.admin;

import br.edu.unip.siab.auditlog.AuditoriaAdminService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import static br.edu.unip.siab.config.OpenApiConfig.ESQUEMA_JWT;

/**
 * Sessão do admin logado e configuração do segundo fator (TOTP).
 * <p>
 * GET /sessao é o que o guard do painel usa agora que o token está num
 * cookie HttpOnly e o JavaScript do front não consegue mais lê-lo.
 */
@RestController
@RequestMapping("/api/admin")
@RequiredArgsConstructor
@Tag(name = "Sessão e MFA", description = "Admin logado e segundo fator (TOTP)")
@SecurityRequirement(name = ESQUEMA_JWT)
public class SessaoController {

    private final AdministradorService administradorService;
    private final AuditoriaAdminService auditoria;

    public record SessaoResponse(String username, boolean mfaAtivo) {
    }

    public record CodigoRequest(@NotBlank String codigo) {
    }

    @Operation(summary = "Admin autenticado nesta requisição")
    @GetMapping("/sessao")
    public SessaoResponse sessao(Authentication authentication) {
        return new SessaoResponse(authentication.getName(), administradorService.mfaAtivo(authentication.getName()));
    }

    @Operation(summary = "Gera o segredo TOTP (ainda inativo) e a URI otpauth:// para o app autenticador")
    @PostMapping("/mfa/configurar")
    public AdministradorService.ConfiguracaoMfa configurar(Authentication authentication) {
        var configuracao = administradorService.configurarMfa(authentication.getName());
        auditoria.registrar("MFA_CONFIGURADO", null);
        return configuracao;
    }

    @Operation(summary = "Ativa o MFA confirmando um código do app")
    @PostMapping("/mfa/ativar")
    public SessaoResponse ativar(Authentication authentication, @Valid @RequestBody CodigoRequest request) {
        administradorService.ativarMfa(authentication.getName(), request.codigo());
        auditoria.registrar("MFA_ATIVADO", null);
        return new SessaoResponse(authentication.getName(), true);
    }

    @Operation(summary = "Desativa o MFA (exige um código válido)")
    @DeleteMapping("/mfa")
    public SessaoResponse desativar(Authentication authentication, @Valid @RequestBody CodigoRequest request) {
        administradorService.desativarMfa(authentication.getName(), request.codigo());
        auditoria.registrar("MFA_DESATIVADO", null);
        return new SessaoResponse(authentication.getName(), false);
    }
}
