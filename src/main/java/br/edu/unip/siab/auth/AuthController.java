package br.edu.unip.siab.auth;

import br.edu.unip.siab.auth.dto.LoginRequest;
import br.edu.unip.siab.auth.dto.LoginResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Módulo "auth" (seção 5.2 do escopo): autenticação administrativa via JWT.
 * <p>
 * Autentica contra a tabela "administradores" (ver
 * {@link br.edu.unip.siab.admin.AdministradorUserDetailsService}), com a
 * senha verificada em hash BCrypt pelo {@code AuthenticationManager}
 * configurado em SecurityConfig.
 */
@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
@Tag(name = "Autenticação", description = "Login administrativo (público) — gera o token usado nos endpoints /api/admin/**")
public class AuthController {

    private final AuthenticationManager authenticationManager;
    private final JwtService jwtService;

    @Operation(summary = "Login administrativo", description = "Autentica um administrador cadastrado e retorna um token JWT (válido por 24h por padrão) a ser usado no header Authorization: Bearer <token> dos endpoints /api/admin/**.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Autenticado com sucesso, token retornado"),
            @ApiResponse(responseCode = "401", description = "Usuário ou senha inválidos"),
            @ApiResponse(responseCode = "403", description = "Acesso negado")
    })
    @PostMapping("/login")
    public ResponseEntity<LoginResponse> login(@Valid @RequestBody LoginRequest request) {
        authenticationManager.authenticate(
                new UsernamePasswordAuthenticationToken(request.username(), request.password())
        );

        String token = jwtService.generateToken(request.username());
        return ResponseEntity.ok(LoginResponse.of(token));
    }
}
