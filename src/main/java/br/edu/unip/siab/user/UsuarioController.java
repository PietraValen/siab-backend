package br.edu.unip.siab.user;

import br.edu.unip.siab.user.dto.UsuarioRequest;
import br.edu.unip.siab.user.dto.UsuarioResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

import static br.edu.unip.siab.config.OpenApiConfig.ESQUEMA_JWT;

/**
 * Endpoints consumidos pelas telas /admin (listar/editar) e /enroll
 * (criação, junto com o pipeline de cadastro facial) do front-end.
 */
@RestController
@RequestMapping("/api/admin/usuarios")
@RequiredArgsConstructor
@Tag(name = "Usuários", description = "CRUD dos usuários cadastrados e seus níveis de acesso")
@SecurityRequirement(name = ESQUEMA_JWT)
public class UsuarioController {

    private final UsuarioService usuarioService;

    @Operation(summary = "Lista todos os usuários cadastrados")
    @GetMapping
    public List<UsuarioResponse> listar() {
        return usuarioService.listarTodos().stream()
                .map(UsuarioResponse::from)
                .toList();
    }

    @Operation(summary = "Busca um usuário por id")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Usuário encontrado"),
            @ApiResponse(responseCode = "404", description = "Usuário não encontrado")
    })
    @GetMapping("/{id}")
    public UsuarioResponse buscar(@Parameter(description = "Id do usuário") @PathVariable Long id) {
        return UsuarioResponse.from(usuarioService.buscarPorId(id));
    }

    @Operation(summary = "Cadastra um novo usuário", description = "Cria o registro do usuário e seu nível de acesso; a captura facial em si é feita separadamente via POST /api/enrollment.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Usuário criado"),
            @ApiResponse(responseCode = "400", description = "Dados inválidos"),
            @ApiResponse(responseCode = "404", description = "Nível de acesso informado não existe")
    })
    @PostMapping
    public ResponseEntity<UsuarioResponse> criar(@Valid @RequestBody UsuarioRequest request) {
        var criado = usuarioService.criar(request);
        return ResponseEntity.ok(UsuarioResponse.from(criado));
    }

    @Operation(summary = "Atualiza um usuário existente")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Usuário atualizado"),
            @ApiResponse(responseCode = "400", description = "Dados inválidos"),
            @ApiResponse(responseCode = "404", description = "Usuário ou nível de acesso não encontrado")
    })
    @PutMapping("/{id}")
    public UsuarioResponse atualizar(@Parameter(description = "Id do usuário") @PathVariable Long id,
                                      @Valid @RequestBody UsuarioRequest request) {
        return UsuarioResponse.from(usuarioService.atualizar(id, request));
    }

    @Operation(summary = "Remove um usuário cadastrado")
    @ApiResponses({
            @ApiResponse(responseCode = "204", description = "Usuário removido"),
            @ApiResponse(responseCode = "404", description = "Usuário não encontrado")
    })
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> excluir(@Parameter(description = "Id do usuário") @PathVariable Long id) {
        usuarioService.excluir(id);
        return ResponseEntity.noContent().build();
    }
}
