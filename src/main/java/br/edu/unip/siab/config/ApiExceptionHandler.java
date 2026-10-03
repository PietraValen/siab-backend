package br.edu.unip.siab.config;

import br.edu.unip.siab.auth.MfaNecessarioException;
import br.edu.unip.siab.security.MuitasTentativasException;
import br.edu.unip.siab.terminal.TerminalNaoAutorizadoException;
import jakarta.persistence.EntityNotFoundException;
import org.springframework.http.HttpHeaders;
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

    /** Mensagem genérica: não conta ao chamador qual verificação do terminal falhou (o detalhe vai para a auditoria). */
    @ExceptionHandler(TerminalNaoAutorizadoException.class)
    public ResponseEntity<Map<String, String>> tratarTerminalNaoAutorizado(TerminalNaoAutorizadoException ex) {
        return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(Map.of("mensagem", "Terminal não autorizado."));
    }

    @ExceptionHandler(MfaNecessarioException.class)
    public ResponseEntity<Map<String, Object>> tratarMfaNecessario(MfaNecessarioException ex) {
        return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                .body(Map.of("mensagem", ex.getMessage(), "mfaNecessario", true));
    }

    @ExceptionHandler(MuitasTentativasException.class)
    public ResponseEntity<Map<String, String>> tratarMuitasTentativas(MuitasTentativasException ex) {
        return ResponseEntity.status(HttpStatus.TOO_MANY_REQUESTS)
                .header(HttpHeaders.RETRY_AFTER, String.valueOf(Math.max(1, ex.getEspera().toSeconds())))
                .body(Map.of("mensagem", ex.getMessage()));
    }
}
