package br.edu.unip.siab.accesscontrol;

import br.edu.unip.siab.terminal.Terminal;
import br.edu.unip.siab.user.Usuario;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Pattern;

/**
 * Segundo fator das portas de nível máximo (seção 1.5 do roteiro de
 * segurança): rosto <b>e</b> PIN. Rosto sozinho é "algo que você é"; o
 * PIN acrescenta "algo que você sabe" — uma foto ou máscara convincente da
 * vítima não basta.
 * <p>
 * O PIN é guardado como hash BCrypt (como a senha dos admins) e, depois de
 * {@value #MAX_FALHAS} erros seguidos, o usuário fica bloqueado nessas
 * portas por {@code siab.pin.bloqueio-minutos} (padrão 15).
 */
@Service
public class PinService {

    static final int MAX_FALHAS = 5;
    private static final Pattern FORMATO = Pattern.compile("\\d{4,8}");

    public enum Verificacao { OK, NAO_CADASTRADO, AUSENTE, INCORRETO, BLOQUEADO }

    private record Falhas(int quantidade, Instant bloqueadoAte) {
    }

    private final PasswordEncoder passwordEncoder;
    private final Map<Long, Falhas> falhasPorUsuario = new ConcurrentHashMap<>();
    private final Clock relogio = Clock.systemUTC();

    @Value("${siab.pin.nivel-minimo-que-exige:3}")
    private long nivelQueExigePin = 3;

    @Value("${siab.pin.bloqueio-minutos:15}")
    private long bloqueioMinutos = 15;

    public PinService(PasswordEncoder passwordEncoder) {
        this.passwordEncoder = passwordEncoder;
    }

    public boolean exigePin(Terminal terminal) {
        return terminal != null && terminal.getNivelExigido().getId() >= nivelQueExigePin;
    }

    /** Valida o formato (4 a 8 dígitos) e devolve o hash a gravar. */
    public String gerarHash(String pin) {
        if (pin == null || !FORMATO.matcher(pin).matches()) {
            throw new IllegalArgumentException("O PIN deve ter de 4 a 8 dígitos numéricos.");
        }
        return passwordEncoder.encode(pin);
    }

    public Verificacao verificar(Usuario usuario, String pin) {
        if (usuario.getPinHash() == null) {
            return Verificacao.NAO_CADASTRADO;
        }
        Falhas falhas = falhasPorUsuario.get(usuario.getId());
        if (falhas != null && falhas.bloqueadoAte() != null && relogio.instant().isBefore(falhas.bloqueadoAte())) {
            return Verificacao.BLOQUEADO;
        }
        if (pin == null || pin.isBlank()) {
            return Verificacao.AUSENTE;
        }
        if (passwordEncoder.matches(pin, usuario.getPinHash())) {
            falhasPorUsuario.remove(usuario.getId());
            return Verificacao.OK;
        }
        falhasPorUsuario.compute(usuario.getId(), (id, atual) -> {
            int quantidade = (atual == null || atual.bloqueadoAte() != null ? 0 : atual.quantidade()) + 1;
            Instant bloqueio = quantidade >= MAX_FALHAS ? relogio.instant().plus(Duration.ofMinutes(bloqueioMinutos)) : null;
            return new Falhas(quantidade, bloqueio);
        });
        return Verificacao.INCORRETO;
    }
}
