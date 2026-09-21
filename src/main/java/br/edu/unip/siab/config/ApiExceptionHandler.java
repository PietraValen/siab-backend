package br.edu.unip.siab.config;

import jakarta.persistence.EntityNotFoundException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.util.Map;

/**
 * Sem isso, exceções como {@link IllegalArgumentException} (ex.: "nenhum
 * rosto detectado" em EnrollmentController) e {@link EntityNotFoundException}
 * (usuário/nível de acesso inexistente) escapavam sem tratamento e viravam
 * um 500 genérico — mesmo os endpoints já prometendo 400/404 nas anotações
 * do Swagger. Mantém esses dois casos, que aparecem em vários módulos
 * (usuários, cadastro facial), com o status e a mensagem que a API já
 * documentava.
 */
@RestControllerAdvice
public class ApiExceptionHandler {

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<Map<String, String>> tratarArgumentoInvalido(IllegalArgumentException ex) {
        return ResponseEntity.badRequest().body(Map.of("mensagem", ex.getMessage()));
    }

    @ExceptionHandler(EntityNotFoundException.class)
    public ResponseEntity<Map<String, String>> tratarNaoEncontrado(EntityNotFoundException ex) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of("mensagem", ex.getMessage()));
    }
}
