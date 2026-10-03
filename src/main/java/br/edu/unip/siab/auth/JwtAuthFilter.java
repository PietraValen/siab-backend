package br.edu.unip.siab.auth;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.jspecify.annotations.NonNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.List;

/**
 * Intercepta toda requisição, extrai o token JWT (header
 * "Authorization: Bearer &lt;token&gt;" ou cookie HttpOnly do painel) e, se
 * válido, autentica o administrador no contexto de segurança do Spring.
 */
@Component
@RequiredArgsConstructor
public class JwtAuthFilter extends OncePerRequestFilter {

    private static final Logger log = LoggerFactory.getLogger(JwtAuthFilter.class);

    private static final List<SimpleGrantedAuthority> ADMIN = List.of(new SimpleGrantedAuthority("ROLE_ADMIN"));

    private final JwtService jwtService;

    /**
     * Header {@code Authorization: Bearer} (Swagger, Postman) tem prioridade;
     * sem ele, o cookie HttpOnly do painel (ver {@link CookieDeSessao}).
     */
    static String extrairToken(HttpServletRequest request) {
        String authHeader = request.getHeader("Authorization");
        if (authHeader != null && authHeader.startsWith("Bearer ")) {
            return authHeader.substring(7);
        }
        return CookieDeSessao.ler(request).orElse(null);
    }

    @Override
    protected void doFilterInternal(@NonNull HttpServletRequest request,
                                     @NonNull HttpServletResponse response,
                                     @NonNull FilterChain filterChain) throws ServletException, IOException {

        String token = extrairToken(request);
        if (token != null && SecurityContextHolder.getContext().getAuthentication() == null) {
            // validar() nunca lança: token expirado, com assinatura inválida
            // (ex.: emitido com um JWT_SECRET antigo), malformado, de outro
            // emissor/audiência ou revogado no logout vira simplesmente "não
            // autenticado", e a regra de autorização normal decide a resposta.
            jwtService.validar(token).ifPresentOrElse(sessao -> {
                User principal = new User(sessao.username(), "", ADMIN);
                var authToken = new UsernamePasswordAuthenticationToken(principal, null, ADMIN);
                authToken.setDetails(new WebAuthenticationDetailsSource().buildDetails(request));
                SecurityContextHolder.getContext().setAuthentication(authToken);
            }, () -> log.debug("Token JWT inválido, expirado ou revogado em {}", request.getRequestURI()));
        }

        filterChain.doFilter(request, response);
    }
}
