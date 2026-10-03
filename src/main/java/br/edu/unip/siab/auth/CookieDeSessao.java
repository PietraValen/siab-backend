package br.edu.unip.siab.auth;

import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseCookie;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.Arrays;
import java.util.Optional;

/**
 * Cookie do token do painel (seção 3 do roteiro de segurança): o JWT deixa
 * de ficar no {@code localStorage} do navegador, onde qualquer XSS o lia, e
 * passa a viajar num cookie {@code HttpOnly} (inacessível a JavaScript),
 * {@code Secure} e {@code SameSite=Strict}. O header
 * {@code Authorization: Bearer} continua aceito para Swagger, Postman e
 * {@code requests.http}.
 * <p>
 * {@code siab.security.cookie-secure=false} só para desenvolvimento em
 * HTTP fora de localhost (Chrome e Firefox já aceitam cookie Secure em
 * http://localhost).
 */
@Component
public class CookieDeSessao {

    public static final String NOME = "SIAB_TOKEN";

    @Value("${siab.security.cookie-secure:true}")
    private boolean secure = true;

    public String criar(String token, long validadeMs) {
        return base(token).maxAge(Duration.ofMillis(validadeMs)).build().toString();
    }

    public String apagar() {
        return base("").maxAge(Duration.ZERO).build().toString();
    }

    public static Optional<String> ler(HttpServletRequest request) {
        Cookie[] cookies = request.getCookies();
        if (cookies == null) {
            return Optional.empty();
        }
        return Arrays.stream(cookies)
                .filter(c -> NOME.equals(c.getName()) && c.getValue() != null && !c.getValue().isBlank())
                .map(Cookie::getValue)
                .findFirst();
    }

    private ResponseCookie.ResponseCookieBuilder base(String valor) {
        return ResponseCookie.from(NOME, valor)
                .httpOnly(true)
                .secure(secure)
                .sameSite("Strict")
                .path("/api");
    }
}
