package br.edu.unip.siab.terminal;

import br.edu.unip.siab.auditlog.AuditoriaAdminService;
import br.edu.unip.siab.terminal.dto.TerminalCriadoResponse;
import br.edu.unip.siab.terminal.dto.TerminalRequest;
import br.edu.unip.siab.terminal.dto.TerminalResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;

import static br.edu.unip.siab.config.OpenApiConfig.ESQUEMA_JWT;

@RestController
@RequestMapping("/api/admin/terminais")
@RequiredArgsConstructor
@Tag(name = "Terminais", description = "Quiosques /scan autorizados a pedir reconhecimento")
@SecurityRequirement(name = ESQUEMA_JWT)
public class TerminalController {

    private final TerminalService terminalService;
    private final AuditoriaAdminService auditoria;

    @Operation(summary = "Lista os terminais cadastrados")
    @GetMapping
    public List<TerminalResponse> listar() {
        return terminalService.listar().stream().map(TerminalResponse::from).toList();
    }

    @Operation(summary = "Cadastra um terminal e devolve a chave dele (mostrada uma única vez)")
    @PostMapping
    public TerminalCriadoResponse criar(@Valid @RequestBody TerminalRequest request) {
        var criado = terminalService.criar(request);
        auditoria.registrar("TERMINAL_CRIADO", "terminal=" + criado.terminal().getId() + " nome=" + criado.terminal().getNome()
                + " nivel=" + criado.terminal().getNivelExigido().getId());
        return new TerminalCriadoResponse(TerminalResponse.from(criado.terminal()), criado.chave());
    }

    @Operation(summary = "Revoga um terminal (as tentativas assinadas com a chave dele passam a ser recusadas)")
    @DeleteMapping("/{id}")
    public TerminalResponse revogar(@Parameter(description = "Id do terminal") @PathVariable Long id) {
        var revogado = terminalService.revogar(id);
        auditoria.registrar("TERMINAL_REVOGADO", "terminal=" + id);
        return TerminalResponse.from(revogado);
    }
}
