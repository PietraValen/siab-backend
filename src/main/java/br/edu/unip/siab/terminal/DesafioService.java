package br.edu.unip.siab.terminal;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.security.SecureRandom;
import java.time.Clock;
import java.time.Instant;
import java.util.Base64;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Desafios (nonces) de uso único para o anti-replay do /scan (seção 1.1 do
 * roteiro de segurança). O terminal pede um desafio, assina a tentativa
 * incluindo-o, e o back-end o consome na verificação: a mesma requisição
 * capturada e reenviada depois é recusada, porque o nonce já foi gasto.
 * <p>
 * Guardado em memória — suficiente para uma instância só do back-end (o
 * cenário do projeto). Com várias instâncias, mover para um armazenamento
 * compartilhado (ex.: Redis com TTL).
 */
@Service
public class DesafioService {

    private record Desafio(Long terminalId, Instant expiraEm) {
    }

    private final Map<String, Desafio> pendentes = new ConcurrentHashMap<>();
    private final SecureRandom aleatorio = new SecureRandom();
    private final Clock relogio;

    @Value("${siab.terminal.desafio-validade-segundos:60}")
    private long validadeSegundos = 60;

    public DesafioService() {
        this(Clock.systemUTC());
    }

    DesafioService(Clock relogio) {
        this.relogio = relogio;
    }

    public record DesafioEmitido(String nonce, Instant expiraEm) {
    }

    public DesafioEmitido emitir(Long terminalId) {
        removerVencidos();
        byte[] bytes = new byte[32];
        aleatorio.nextBytes(bytes);
        String nonce = Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
        Instant expiraEm = relogio.instant().plusSeconds(validadeSegundos);
        pendentes.put(nonce, new Desafio(terminalId, expiraEm));
        return new DesafioEmitido(nonce, expiraEm);
    }

    /**
     * Gasta o desafio (sempre, mesmo se inválido) e diz se ele era deste
     * terminal e ainda estava no prazo.
     */
    public boolean consumir(String nonce, Long terminalId) {
        if (nonce == null) {
            return false;
        }
        Desafio desafio = pendentes.remove(nonce);
        return desafio != null
                && desafio.terminalId().equals(terminalId)
                && relogio.instant().isBefore(desafio.expiraEm());
    }

    private void removerVencidos() {
        Instant agora = relogio.instant();
        pendentes.values().removeIf(d -> !agora.isBefore(d.expiraEm()));
    }
}
