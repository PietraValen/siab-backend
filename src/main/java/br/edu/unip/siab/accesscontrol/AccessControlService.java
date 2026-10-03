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
     * Concede acesso à área solicitada quando o nível do usuário é igual ou
     * superior ao nível mínimo exigido por ela (ver {@link AreaCofre}).
     */
    public boolean possuiPermissao(Usuario usuario, AreaCofre area) {
        return possuiPermissao(usuario, area.getNivelMinimoExigido());
    }

    public boolean possuiPermissao(Usuario usuario, Long nivelAcessoMinimoExigido) {
        if (usuario == null || usuario.getNivelAcesso() == null) {
            return false;
        }
        return usuario.getNivelAcesso().getId() >= nivelAcessoMinimoExigido;
    }
}
