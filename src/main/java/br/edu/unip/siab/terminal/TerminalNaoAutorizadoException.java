package br.edu.unip.siab.terminal;

/**
 * Tentativa de reconhecimento sem uma assinatura válida de terminal
 * cadastrado (terminal desconhecido/revogado, desafio vencido ou já usado,
 * relógio fora da janela, HMAC errado). Vira 401 no ApiExceptionHandler —
 * com mensagem genérica, sem dizer qual verificação falhou.
 */
public class TerminalNaoAutorizadoException extends RuntimeException {

    public TerminalNaoAutorizadoException(String motivoInterno) {
        super(motivoInterno);
    }
}
