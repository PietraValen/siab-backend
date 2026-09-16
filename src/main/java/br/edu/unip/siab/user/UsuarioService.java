package br.edu.unip.siab.user;

import br.edu.unip.siab.accesslevel.NivelAcesso;
import br.edu.unip.siab.accesslevel.NivelAcessoRepository;
import br.edu.unip.siab.user.dto.UsuarioRequest;
import jakarta.persistence.EntityNotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * Módulo "user-management" (seção 5.2 do escopo): CRUD de usuários
 * cadastrados e seus níveis de acesso. Consumido pelo painel /admin do
 * front-end (RF06).
 */
@Service
@RequiredArgsConstructor
public class UsuarioService {

    private final UsuarioRepository usuarioRepository;
    private final NivelAcessoRepository nivelAcessoRepository;

    public List<Usuario> listarTodos() {
        return usuarioRepository.findAll();
    }

    public Usuario buscarPorId(Long id) {
        return usuarioRepository.findById(id)
                .orElseThrow(() -> new EntityNotFoundException("Usuário não encontrado: " + id));
    }

    public Usuario criar(UsuarioRequest request) {
        NivelAcesso nivel = nivelAcessoRepository.findById(request.nivelAcessoId())
                .orElseThrow(() -> new EntityNotFoundException("Nível de acesso não encontrado: " + request.nivelAcessoId()));

        Usuario usuario = new Usuario();
        usuario.setNome(request.nome());
        usuario.setCargo(request.cargo());
        usuario.setNivelAcesso(nivel);

        return usuarioRepository.save(usuario);
    }

    public Usuario atualizar(Long id, UsuarioRequest request) {
        Usuario usuario = buscarPorId(id);
        NivelAcesso nivel = nivelAcessoRepository.findById(request.nivelAcessoId())
                .orElseThrow(() -> new EntityNotFoundException("Nível de acesso não encontrado: " + request.nivelAcessoId()));

        usuario.setNome(request.nome());
        usuario.setCargo(request.cargo());
        usuario.setNivelAcesso(nivel);

        return usuarioRepository.save(usuario);
    }

    public void excluir(Long id) {
        usuarioRepository.deleteById(id);
    }
}
