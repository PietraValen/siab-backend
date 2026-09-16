package br.edu.unip.siab.accesscontrol;

import br.edu.unip.siab.user.Usuario;
import org.springframework.stereotype.Service;

/**
 * Módulo "access-control" (seção 5.2 e 5.6 do escopo).
 * Aplica a regra de negócio dos três níveis de permissão. Isolado da lógica
 * de reconhecimento facial: recebe apenas "quem foi reconhecido" e decide
 * qual recurso essa pessoa pode acessar.
 */
@Service
public class AccessControlService {

    /**
     * TODO: hoje qualquer usuário reconhecido tem acesso concedido ao nível
     * associado a ele no cadastro. Se o projeto evoluir para múltiplos
     * "recursos protegidos" (ex.: /cofre/nivel1, /cofre/nivel2), esse
     * método deve receber também qual recurso está sendo solicitado e
     * comparar com o nivelAcesso do usuário.
     */
    public boolean possuiPermissao(Usuario usuario, Long nivelAcessoMinimoExigido) {
        if (usuario == null || usuario.getNivelAcesso() == null) {
            return false;
        }
        return usuario.getNivelAcesso().getId() >= nivelAcessoMinimoExigido;
    }
}
