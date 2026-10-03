package br.edu.unip.siab.security;

import java.time.Duration;

/** Login bloqueado temporariamente (ver {@link BloqueioLoginService}); vira 429. */
public class MuitasTentativasException extends RuntimeException {

    private final Duration espera;

    public MuitasTentativasException(Duration espera) {
        super("Muitas tentativas de login. Tente de novo em " + Math.max(1, espera.toSeconds()) + " s.");
        this.espera = espera;
    }

    public Duration getEspera() {
        return espera;
    }
}
