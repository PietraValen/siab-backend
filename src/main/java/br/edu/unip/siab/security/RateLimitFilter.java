package br.edu.unip.siab.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.jspecify.annotations.NonNull;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.time.Clock;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Limite de requisições por IP (seção 1.3 do roteiro de segurança), em
 * janela fixa de 1 minuto:
 * <ul>
 *   <li>{@code POST /api/auth/login}: {@code siab.rate-limit.login-por-minuto}
 *       (padrão 10) — freia quem testa senhas em massa;</li>
 *   <li>{@code /api/recognition/**}: {@code siab.rate-limit.reconhecimento-por-minuto}
 *       (padrão 30, ~15 tentativas, já que cada uma usa desafio + scan) —
 *       freia tentativas com fotos e protege a CPU, porque o pipeline
 *       OpenCV é caro e viraria vetor de negação de serviço.</li>
 * </ul>
 * Acima do limite: 429 com {@code Retry-After}. Implementação própria e em
 * memória (sem Bucket4j/Redis), proporcional a uma instância do back-end.
 * O IP vem de {@code getRemoteAddr()}; atrás do proxy reverso, ligue
 * {@code FORWARD_HEADERS_STRATEGY=framework} para ele refletir o cliente
 * real (sem proxy, deixe {@code none}, senão o header X-Forwarded-For
 * vira uma forma de burlar o limite).
 */
@Component
public class RateLimitFilter extends OncePerRequestFilter {

    private record Janela(long minuto, int contagem) {
    }

    private final Map<String, Janela> janelas = new ConcurrentHashMap<>();
    private final Clock relogio;

    @Value("${siab.rate-limit.login-por-minuto:10}")
    private int loginPorMinuto = 10;

    @Value("${siab.rate-limit.reconhecimento-por-minuto:30}")
    private int reconhecimentoPorMinuto = 30;

    public RateLimitFilter() {
        this(Clock.systemUTC());
    }

    RateLimitFilter(Clock relogio) {
        this.relogio = relogio;
    }

    @Override
    protected void doFilterInternal(@NonNull HttpServletRequest request, @NonNull HttpServletResponse response,
                                    @NonNull FilterChain filterChain) throws ServletException, IOException {
        String uri = request.getRequestURI();
        String grupo = null;
        int limite = 0;
        if ("POST".equals(request.getMethod()) && uri.equals("/api/auth/login")) {
            grupo = "login";
            limite = loginPorMinuto;
        } else if (uri.startsWith("/api/recognition/")) {
            grupo = "reconhecimento";
            limite = reconhecimentoPorMinuto;
        }

        if (grupo != null && !permitir(grupo + "|" + request.getRemoteAddr(), limite)) {
            long segundosAteVirar = 60 - (relogio.millis() / 1000) % 60;
            response.setStatus(HttpStatus.TOO_MANY_REQUESTS.value());
            response.setHeader("Retry-After", String.valueOf(segundosAteVirar));
            response.setContentType(MediaType.APPLICATION_JSON_VALUE);
            response.setCharacterEncoding("UTF-8");
            response.getWriter().write("{\"mensagem\":\"Muitas tentativas. Aguarde " + segundosAteVirar + " s.\"}");
            return;
        }
        filterChain.doFilter(request, response);
    }

    boolean permitir(String chave, int limite) {
        long minuto = relogio.millis() / 60_000;
        if (janelas.size() > 10_000) {
            janelas.values().removeIf(j -> j.minuto() < minuto);
        }
        Janela janela = janelas.merge(chave, new Janela(minuto, 1),
                (atual, nova) -> atual.minuto() == minuto ? new Janela(minuto, atual.contagem() + 1) : nova);
        return janela.contagem() <= limite;
    }
}
