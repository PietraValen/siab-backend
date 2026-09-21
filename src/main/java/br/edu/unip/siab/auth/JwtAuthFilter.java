package br.edu.unip.siab.auth;

import io.jsonwebtoken.JwtException;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.jspecify.annotations.NonNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.Collections;

/**
 * Intercepta toda requisição, extrai o header "Authorization: Bearer <token>"
 * e, se válido, autentica o usuário no contexto de segurança do Spring.
 */
@Component
@RequiredArgsConstructor
public class JwtAuthFilter extends OncePerRequestFilter {

    private static final Logger log = LoggerFactory.getLogger(JwtAuthFilter.class);

    private final JwtService jwtService;

    @Override
    protected void doFilterInternal(@NonNull HttpServletRequest request,
                                     @NonNull HttpServletResponse response,
                                     @NonNull FilterChain filterChain) throws ServletException, IOException {

        String authHeader = request.getHeader("Authorization");

        if (authHeader == null || !authHeader.startsWith("Bearer ")) {
            filterChain.doFilter(request, response);
            return;
        }

        String token = authHeader.substring(7);

        // Um token expirado, com assinatura inválida (ex.: emitido com um
        // JWT_SECRET antigo) ou malformado faz o parser da JJWT lançar
        // exceção em vez de simplesmente retornar "inválido". Sem este
        // try/catch, essa exceção escapava do filtro e o dispatch de erro do
        // Spring Boot (que também passa pela cadeia de segurança) mascarava
        // o 500 real como um 403 confuso — em vez disso, tratamos como
        // "não autenticado" e deixamos a regra de autorização normal
        // (401/403 explícito) decidir a resposta.
        try {
            String username = jwtService.extractUsername(token);

            if (username != null && SecurityContextHolder.getContext().getAuthentication() == null) {
                if (jwtService.isTokenValid(token, username)) {
                    // TODO: carregar roles reais do usuário administrador quando o
                    // módulo de gestão de administradores for implementado.
                    User principal = new User(username, "", Collections.emptyList());

                    var authToken = new UsernamePasswordAuthenticationToken(
                            principal, null, Collections.emptyList());
                    authToken.setDetails(new WebAuthenticationDetailsSource().buildDetails(request));
                    SecurityContextHolder.getContext().setAuthentication(authToken);
                }
            }
        } catch (JwtException | IllegalArgumentException e) {
            log.debug("Token JWT inválido ou expirado em {}: {}", request.getRequestURI(), e.getMessage());
        }

        filterChain.doFilter(request, response);
    }
}
