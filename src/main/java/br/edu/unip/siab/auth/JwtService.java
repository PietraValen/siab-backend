package br.edu.unip.siab.auth;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Date;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Function;

/**
 * Geração e validação de tokens JWT usados para autenticar o painel
 * administrativo (RF06 / RNF03 do escopo).
 * <p>
 * Endurecimento (seção 2 do roteiro de segurança):
 * <ul>
 *   <li>validade curta (30 min por padrão, era 24 h);</li>
 *   <li>{@code iss}, {@code aud} e {@code jti} em todo token, com
 *       {@code iss}/{@code aud} conferidos na validação — um JWT assinado
 *       com o mesmo segredo para outro fim não serve aqui;</li>
 *   <li>logout de verdade: o {@code jti} entra numa lista de revogados até
 *       o token expirar.</li>
 * </ul>
 * O algoritmo continua <b>HS256</b>: HMAC-SHA256 é simétrico e, com chave
 * de 256 bits, não é afetado pelo algoritmo de Shor — já é resistente a
 * computador quântico (seção 4.4 do roteiro).
 */
@Service
public class JwtService {

    static final String EMISSOR = "siab-backend";
    static final String AUDIENCIA = "siab-painel";

    @Value("${siab.security.jwt-secret}")
    private String jwtSecret;

    @Value("${siab.security.jwt-expiration-ms:1800000}") // 30 min por padrão
    private long jwtExpirationMs;

    /** jti revogado -> instante em que o token expiraria (depois disso pode sair da lista). */
    private final Map<String, Instant> revogados = new ConcurrentHashMap<>();

    /** Dados de um token válido e não revogado. */
    public record Sessao(String username, String jti, Instant expiraEm) {
    }

    private SecretKey signingKey() {
        return Keys.hmacShaKeyFor(jwtSecret.getBytes(StandardCharsets.UTF_8));
    }

    public String generateToken(String username) {
        Date now = new Date();
        Date expiry = new Date(now.getTime() + jwtExpirationMs);

        return Jwts.builder()
                .id(UUID.randomUUID().toString())
                .issuer(EMISSOR)
                .audience().add(AUDIENCIA).and()
                .subject(username)
                .issuedAt(now)
                .expiration(expiry)
                .signWith(signingKey())
                .compact();
    }

    public long getExpiracaoMs() {
        return jwtExpirationMs;
    }

    /**
     * Valida assinatura, expiração, emissor, audiência e revogação.
     * Vazio para qualquer token inválido (nunca lança).
     */
    @SuppressWarnings("null") // Claims (jjwt) não é anotada com @NonNull/@Nullable; falso positivo do null-analysis do Eclipse
    public Optional<Sessao> validar(String token) {
        try {
            Claims claims = parse(token);
            if (claims.getId() == null || revogados.containsKey(claims.getId())) {
                return Optional.empty();
            }
            return Optional.of(new Sessao(claims.getSubject(), claims.getId(), claims.getExpiration().toInstant()));
        } catch (JwtException | IllegalArgumentException e) {
            return Optional.empty();
        }
    }

    /** Logout: o token deixa de valer imediatamente, mesmo antes de expirar. */
    public void revogar(String token) {
        validar(token).ifPresent(sessao -> {
            limparRevogadosVencidos();
            revogados.put(sessao.jti(), sessao.expiraEm());
        });
    }

    @SuppressWarnings("null") // Claims (jjwt) não é anotada com @NonNull/@Nullable; falso positivo do null-analysis do Eclipse
    public String extractUsername(String token) {
        return extractClaim(token, Claims::getSubject);
    }

    public boolean isTokenValid(String token, String username) {
        return validar(token).map(s -> s.username().equals(username)).orElse(false);
    }

    private void limparRevogadosVencidos() {
        Instant agora = Instant.now();
        revogados.values().removeIf(expira -> expira.isBefore(agora));
    }

    private Claims parse(String token) {
        return Jwts.parser()
                .verifyWith(signingKey())
                .requireIssuer(EMISSOR)
                .requireAudience(AUDIENCIA)
                .build()
                .parseSignedClaims(token)
                .getPayload();
    }

    private <T> T extractClaim(String token, Function<Claims, T> resolver) {
        return resolver.apply(parse(token));
    }
}
