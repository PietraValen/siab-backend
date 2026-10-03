package br.edu.unip.siab.auth;

/**
 * Senha certa, mas o admin tem MFA ativo e o código não veio (ou veio
 * errado). Vira 401 com {@code "mfaNecessario": true} para o front pedir o
 * código.
 */
public class MfaNecessarioException extends RuntimeException {

    public MfaNecessarioException(String mensagem) {
        super(mensagem);
    }
}
