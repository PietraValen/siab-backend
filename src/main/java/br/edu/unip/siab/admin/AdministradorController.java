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
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import static br.edu.unip.siab.config.OpenApiConfig.ESQUEMA_JWT;

/**
 * Cadastro de novos administradores do painel. Assim como o restante de
 * "/api/admin/**", exige um token JWT válido (ver SecurityConfig) — não há
 * cadastro público de administrador.
 */
@RestController
@RequestMapping("/api/admin/administradores")
@RequiredArgsConstructor
@Tag(name = "Administradores", description = "Cadastro de operadores do painel administrativo")
@SecurityRequirement(name = ESQUEMA_JWT)
public class AdministradorController {

    private final AdministradorService administradorService;

    @Operation(summary = "Cadastra um novo administrador", description = "A senha é sempre hasheada com BCrypt antes de ser salva; nunca é retornada na resposta.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Administrador criado"),
            @ApiResponse(responseCode = "400", description = "Dados inválidos (ex.: senha com menos de 8 caracteres)"),
            @ApiResponse(responseCode = "409", description = "Já existe um administrador com esse username")
    })
    @PostMapping
    public ResponseEntity<AdministradorResponse> criar(@Valid @RequestBody AdministradorRequest request) {
        var criado = administradorService.criar(request);
        return ResponseEntity.ok(AdministradorResponse.from(criado));
    }
}
