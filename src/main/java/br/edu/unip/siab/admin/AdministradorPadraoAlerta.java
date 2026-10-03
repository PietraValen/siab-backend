package br.edu.unip.siab.admin;

import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

/**
 * Versões antigas do data.sql semeavam o administrador "admin" com a senha
 * "TrocarSenha123!", que continua pública no histórico do repositório. Tirar
 * o INSERT não apaga a linha de bancos que já subiram com ele, então este
 * aviso no boot aponta a credencial conhecida até alguém removê-la.
 */
@Component
@RequiredArgsConstructor
class AdministradorPadraoAlerta implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(AdministradorPadraoAlerta.class);

    static final String USERNAME_ANTIGO = "admin";
    static final String SENHA_ANTIGA = "TrocarSenha123!";

    private final AdministradorRepository administradorRepository;
    private final PasswordEncoder passwordEncoder;

    @Override
    public void run(ApplicationArguments args) {
        if (usaCredencialAntiga()) {
            log.warn("O administrador '{}' ainda usa a senha padrão antiga do data.sql, que é pública. "
                    + "Crie outro administrador e remova este (DELETE FROM administradores WHERE username = '{}').",
                    USERNAME_ANTIGO, USERNAME_ANTIGO);
        }
    }

    boolean usaCredencialAntiga() {
        return administradorRepository.findByUsername(USERNAME_ANTIGO)
                .map(admin -> passwordEncoder.matches(SENHA_ANTIGA, admin.getSenha()))
                .orElse(false);
    }
}
