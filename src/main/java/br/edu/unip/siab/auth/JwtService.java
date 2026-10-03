package br.edu.unip.siab.auth;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import jakarta.annotation.PostConstruct;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.util.Date;
import java.util.Set;
import java.util.function.Function;

/**
 * Geração e validação de tokens JWT usados para autenticar o painel
 * administrativo (RF06 / RNF03 do escopo).
 */
@Service
public class JwtService {

    @Value("${siab.security.jwt-secret}")
    private String jwtSecret;

    @Value("${siab.security.jwt-expiration-ms:86400000}") // 24h por padrão
    private long jwtExpirationMs;

    /**
     * Mínimo exigido pelo HMAC-SHA256 (256 bits); abaixo disso a JJWT recusa
     * a chave com WeakKeyException só no primeiro login, não no boot.
     */
    static final int TAMANHO_MINIMO_SEGREDO_BYTES = 32;

    /**
     * Valores de exemplo que já estiveram versionados no repositório
     * (padrão antigo do application.yml e o placeholder do .env.example).
     * Quem copia o .env.example sem trocar o valor fica com um segredo
     * público, então ele é recusado como se estivesse vazio.
     */
    static final Set<String> SEGREDOS_DE_EXEMPLO = Set.of(
            "troque-este-segredo-antes-de-ir-para-producao-min-32-chars",
            "troque-por-uma-string-aleatoria-de-pelo-menos-32-caracteres");

    /**
     * Falha a inicialização se o segredo estiver ausente, curto ou for um
     * dos exemplos públicos — melhor a aplicação não subir do que subir
     * aceitando tokens que qualquer um consegue assinar.
     */
    @PostConstruct
    void validarSegredo() {
        if (jwtSecret == null || jwtSecret.isBlank()) {
            throw new IllegalStateException(
                    "JWT_SECRET não definido. Gere um com: openssl rand -base64 48");
        }
        if (SEGREDOS_DE_EXEMPLO.contains(jwtSecret)) {
            throw new IllegalStateException(
                    "JWT_SECRET ainda é o valor de exemplo do repositório. Gere um com: openssl rand -base64 48");
        }
        if (jwtSecret.getBytes(StandardCharsets.UTF_8).length < TAMANHO_MINIMO_SEGREDO_BYTES) {
            throw new IllegalStateException(
                    "JWT_SECRET precisa ter pelo menos " + TAMANHO_MINIMO_SEGREDO_BYTES + " bytes.");
        }
    }

    private SecretKey signingKey() {
        return Keys.hmacShaKeyFor(jwtSecret.getBytes(StandardCharsets.UTF_8));
    }

    public String generateToken(String username) {
        Date now = new Date();
        Date expiry = new Date(now.getTime() + jwtExpirationMs);

        return Jwts.builder()
                .subject(username)
                .issuedAt(now)
                .expiration(expiry)
                .signWith(signingKey())
                .compact();
    }

    @SuppressWarnings("null") // Claims (jjwt) não é anotada com @NonNull/@Nullable; falso positivo do null-analysis do Eclipse
    public String extractUsername(String token) {
        return extractClaim(token, Claims::getSubject);
    }

    public boolean isTokenValid(String token, String username) {
        String extracted = extractUsername(token);
        return extracted.equals(username) && !isTokenExpired(token);
    }

    @SuppressWarnings("null") // Claims (jjwt) não é anotada com @NonNull/@Nullable; falso positivo do null-analysis do Eclipse
    private boolean isTokenExpired(String token) {
        return extractClaim(token, Claims::getExpiration).before(new Date());
    }

    private <T> T extractClaim(String token, Function<Claims, T> resolver) {
        Claims claims = Jwts.parser()
                .verifyWith(signingKey())
                .build()
                .parseSignedClaims(token)
                .getPayload();
        return resolver.apply(claims);
    }
}
