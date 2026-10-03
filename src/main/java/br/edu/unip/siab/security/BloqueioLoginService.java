package br.edu.unip.siab.security;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Bloqueio progressivo de login por username (seção 1.3 do roteiro de
 * segurança): depois de {@code siab.login.falhas-antes-do-bloqueio} erros
 * seguidos (padrão 5), cada novo erro dobra a espera — 30 s, 1 min, 2 min…
 * até {@code siab.login.bloqueio-maximo-minutos} (padrão 15). Um login
 * certo zera o contador. Complementa o {@link RateLimitFilter}, que limita
 * por IP: juntos, freiam tanto um IP testando muitas senhas quanto muitos
 * IPs testando a mesma conta.
 * <p>
 * Em memória (uma instância do back-end); com várias instâncias, mover
 * para um armazenamento compartilhado.
 */
@Service
public class BloqueioLoginService {

    private record Estado(int falhas, Instant bloqueadoAte) {
    }

    private final Map<String, Estado> porUsuario = new ConcurrentHashMap<>();
    private final Clock relogio;

    @Value("${siab.login.falhas-antes-do-bloqueio:5}")
    private int falhasAntesDoBloqueio = 5;

    @Value("${siab.login.bloqueio-maximo-minutos:15}")
    private long bloqueioMaximoMinutos = 15;

    public BloqueioLoginService() {
        this(Clock.systemUTC());
    }

    BloqueioLoginService(Clock relogio) {
        this.relogio = relogio;
    }

    /** Quanto falta para o username poder tentar de novo, se estiver bloqueado. */
    public Optional<Duration> bloqueioRestante(String username) {
        Estado estado = porUsuario.get(chave(username));
        if (estado == null || estado.bloqueadoAte() == null) {
            return Optional.empty();
        }
        Duration restante = Duration.between(relogio.instant(), estado.bloqueadoAte());
        return restante.isNegative() || restante.isZero() ? Optional.empty() : Optional.of(restante);
    }

    public void registrarFalha(String username) {
        porUsuario.compute(chave(username), (k, atual) -> {
            int falhas = (atual == null ? 0 : atual.falhas()) + 1;
            Instant bloqueio = null;
            if (falhas >= falhasAntesDoBloqueio) {
                long expoente = Math.min(falhas - falhasAntesDoBloqueio, 20);
                Duration espera = Duration.ofSeconds(30L << expoente);
                Duration maximo = Duration.ofMinutes(bloqueioMaximoMinutos);
                bloqueio = relogio.instant().plus(espera.compareTo(maximo) > 0 ? maximo : espera);
            }
            return new Estado(falhas, bloqueio);
        });
    }

    public void registrarSucesso(String username) {
        porUsuario.remove(chave(username));
    }

    private static String chave(String username) {
        return username == null ? "" : username.trim().toLowerCase();
    }
}
