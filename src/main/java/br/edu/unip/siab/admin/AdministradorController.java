package br.edu.unip.siab.admin;

import br.edu.unip.siab.admin.dto.AdministradorRequest;
import br.edu.unip.siab.admin.dto.AdministradorResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import static br.edu.unip.siab.config.OpenApiConfig.ESQUEMA_JWT;

/**
 * Cadastro de novos administradores do painel.
 * <p>
 * Exige um JWT válido de um administrador já autenticado (ver
 * SecurityConfig) — <strong>exceto</strong> na criação do primeiro
 * administrador do sistema: enquanto a tabela "administradores" estiver
 * vazia, o cadastro é público (é o bootstrap; não existe ainda ninguém para
 * se autenticar). Ver {@link AdministradorService#existeAdministrador()} e
 * o endpoint público GET /api/auth/existe-administrador, usado pela tela de
 * cadastro do front-end para decidir se mostra o formulário.
 */
@RestController
@RequestMapping("/api/admin/administradores")
@RequiredArgsConstructor
@Tag(name = "Administradores", description = "Cadastro de operadores do painel administrativo")
@SecurityRequirement(name = ESQUEMA_JWT)
public class AdministradorController {

    private final AdministradorService administradorService;

    @Operation(summary = "Cadastra um novo administrador", description = "A senha é sempre hasheada com BCrypt antes de ser salva; nunca é retornada na resposta. Dispensa JWT apenas quando é o primeiro administrador do sistema (bootstrap).")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Administrador criado"),
            @ApiResponse(responseCode = "400", description = "Dados inválidos (ex.: senha com menos de 8 caracteres)"),
            @ApiResponse(responseCode = "403", description = "Já existe um administrador e a requisição não trouxe um JWT autenticado"),
            @ApiResponse(responseCode = "409", description = "Já existe um administrador com esse username")
    })
    @PostMapping
    public ResponseEntity<AdministradorResponse> criar(
            @Valid @RequestBody AdministradorRequest request,
            Authentication authentication) {
        boolean bootstrap = !administradorService.existeAdministrador();
        boolean autenticado = authentication != null
                && authentication.isAuthenticated()
                && !(authentication instanceof AnonymousAuthenticationToken);

        if (!bootstrap && !autenticado) {
            throw new AutenticacaoNecessariaException();
        }

        var criado = administradorService.criar(request);
        return ResponseEntity.ok(AdministradorResponse.from(criado));
    }
}
