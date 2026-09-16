package br.edu.unip.siab.admin;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus;

@ResponseStatus(HttpStatus.CONFLICT)
public class UsernameJaCadastradoException extends RuntimeException {

    public UsernameJaCadastradoException(String username) {
        super("Já existe um administrador com o username: " + username);
    }
}
