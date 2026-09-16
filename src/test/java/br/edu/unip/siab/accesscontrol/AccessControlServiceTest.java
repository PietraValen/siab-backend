package br.edu.unip.siab.accesscontrol;

import br.edu.unip.siab.accesslevel.NivelAcesso;
import br.edu.unip.siab.user.Usuario;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Cobre a regra de negócio dos 3 níveis de acesso (seção 5.6 do escopo).
 * Não depende de banco nem de OpenCV — puro teste de lógica.
 */
class AccessControlServiceTest {

    private final AccessControlService service = new AccessControlService();

    private Usuario usuarioComNivel(long nivelId) {
        NivelAcesso nivel = new NivelAcesso();
        nivel.setId(nivelId);
        nivel.setNome("Nível " + nivelId);

        Usuario usuario = new Usuario();
        usuario.setNivelAcesso(nivel);
        return usuario;
    }

    @Test
    void usuarioComNivelIgualAoExigidoTemPermissao() {
        Usuario usuario = usuarioComNivel(2L);
        assertThat(service.possuiPermissao(usuario, 2L)).isTrue();
    }

    @Test
    void usuarioComNivelSuperiorAoExigidoTemPermissao() {
        Usuario usuario = usuarioComNivel(3L); // Ministro
        assertThat(service.possuiPermissao(usuario, 1L)).isTrue(); // recurso de nível Geral
    }

    @Test
    void usuarioComNivelInferiorAoExigidoNaoTemPermissao() {
        Usuario usuario = usuarioComNivel(1L); // Geral
        assertThat(service.possuiPermissao(usuario, 3L)).isFalse(); // recurso de nível Ministro
    }

    @Test
    void usuarioNuloNuncaTemPermissao() {
        assertThat(service.possuiPermissao(null, 1L)).isFalse();
    }
}
