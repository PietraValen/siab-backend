package br.edu.unip.siab.auth;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.util.Date;
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
