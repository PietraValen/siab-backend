package br.edu.unip.siab.auth;

import br.edu.unip.siab.admin.AdministradorService;
import br.edu.unip.siab.auditlog.AuditoriaAdminService;
import br.edu.unip.siab.auth.dto.ExisteAdministradorResponse;
import br.edu.unip.siab.auth.dto.LoginRequest;
import br.edu.unip.siab.auth.dto.LoginResponse;
import br.edu.unip.siab.security.BloqueioLoginService;
import br.edu.unip.siab.security.MuitasTentativasException;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.web.csrf.CsrfToken;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;
import java.util.Map;

/**
 * Módulo "auth" (seção 5.2 do escopo): autenticação administrativa via JWT.
 * <p>
 * Autentica contra a tabela "administradores" (ver
 * {@link br.edu.unip.siab.admin.AdministradorUserDetailsService}), com a
 * senha verificada em hash BCrypt pelo {@code AuthenticationManager}
 * configurado em SecurityConfig. Endurecimento (roteiro de segurança):
 * bloqueio progressivo por username, segundo fator TOTP para quem ativou,
 * token em cookie HttpOnly, logout que revoga o token e trilha de auditoria
 * de cada tentativa.
 */
@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
@Tag(name = "Autenticação", description = "Login administrativo (público) — gera o token usado nos endpoints /api/admin/**")
public class AuthController {

    private final AuthenticationManager authenticationManager;
    private final JwtService jwtService;
    private final AdministradorService administradorService;
    private final BloqueioLoginService bloqueioLoginService;
    private final CookieDeSessao cookieDeSessao;
    private final AuditoriaAdminService auditoria;

    @Operation(summary = "Login administrativo", description = "Autentica um administrador cadastrado. Devolve o token no cookie HttpOnly SIAB_TOKEN (usado pelo painel) e também no corpo (para o header Authorization: Bearer no Swagger/Postman). Com MFA ativo, exige codigoMfa.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Autenticado com sucesso, token retornado"),
            @ApiResponse(responseCode = "401", description = "Usuário ou senha inválidos, ou código MFA ausente/errado (mfaNecessario=true)"),
            @ApiResponse(responseCode = "429", description = "Username bloqueado temporariamente por excesso de erros")
    })
    @PostMapping("/login")
    public ResponseEntity<LoginResponse> login(@Valid @RequestBody LoginRequest request) {
        bloqueioLoginService.bloqueioRestante(request.username()).ifPresent(espera -> {
            throw new MuitasTentativasException(espera);
        });

        try {
            authenticationManager.authenticate(
                    new UsernamePasswordAuthenticationToken(request.username(), request.password()));
            administradorService.verificarSegundoFator(request.username(), request.codigoMfa());
        } catch (AuthenticationException | MfaNecessarioException e) {
            boolean faltouSoOCodigo = e instanceof MfaNecessarioException
                    && (request.codigoMfa() == null || request.codigoMfa().isBlank());
            if (!faltouSoOCodigo) {
                bloqueioLoginService.registrarFalha(request.username());
                auditoria.registrarComo(request.username(), "LOGIN_FALHOU",
                        e instanceof MfaNecessarioException ? "código MFA inválido" : "credenciais inválidas");
            }
            throw e;
        }

        bloqueioLoginService.registrarSucesso(request.username());
        String token = jwtService.generateToken(request.username());
        auditoria.registrarComo(request.username(), "LOGIN", null);

        Instant expiraEm = Instant.now().plusMillis(jwtService.getExpiracaoMs());
        return ResponseEntity.ok()
                .header(HttpHeaders.SET_COOKIE, cookieDeSessao.criar(token, jwtService.getExpiracaoMs()))
                .body(LoginResponse.of(token, expiraEm));
    }

    @Operation(summary = "Logout", description = "Revoga o token atual (cookie ou Bearer) e apaga o cookie.")
    @PostMapping("/logout")
    public ResponseEntity<Void> logout(HttpServletRequest request) {
        String token = JwtAuthFilter.extrairToken(request);
        if (token != null) {
            jwtService.validar(token).ifPresent(sessao -> auditoria.registrarComo(sessao.username(), "LOGOUT", null));
            jwtService.revogar(token);
        }
        return ResponseEntity.noContent()
                .header(HttpHeaders.SET_COOKIE, cookieDeSessao.apagar())
                .build();
    }

    @Operation(summary = "Token anti-CSRF", description = "O painel envia este valor no header X-XSRF-TOKEN em toda requisição que altera dados (POST/PUT/DELETE) autenticada pelo cookie.")
    @GetMapping("/csrf")
    public Map<String, String> csrf(CsrfToken csrfToken) {
        return Map.of("headerName", csrfToken.getHeaderName(), "token", csrfToken.getToken());
    }

    @Operation(summary = "Verifica se já existe algum administrador cadastrado", description = "Endpoint público usado pela tela de cadastro do painel (bootstrap): enquanto não existir nenhum administrador, o cadastro do primeiro é liberado sem JWT (ver AdministradorController).")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Consulta realizada com sucesso")
    })
    @GetMapping("/existe-administrador")
    public ResponseEntity<ExisteAdministradorResponse> existeAdministrador() {
        return ResponseEntity.ok(new ExisteAdministradorResponse(administradorService.existeAdministrador()));
    }
}
