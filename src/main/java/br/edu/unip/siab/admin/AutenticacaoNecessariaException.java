package br.edu.unip.siab.admin;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus;

/**
 * Lançada quando já existe pelo menos um administrador cadastrado e a
 * requisição para criar mais um chega sem um JWT autenticado — a exceção de
 * bootstrap (permitir o primeiro cadastro sem token) só vale enquanto a
 * tabela "administradores" está vazia.
 */
@ResponseStatus(HttpStatus.FORBIDDEN)
public class AutenticacaoNecessariaException extends RuntimeException {

    public AutenticacaoNecessariaException() {
        super("Já existe um administrador cadastrado — autenticação é obrigatória para criar outro.");
    }
}
