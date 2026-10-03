package br.edu.unip.siab.user;

import br.edu.unip.siab.accesscontrol.PinService;
import br.edu.unip.siab.accesslevel.NivelAcesso;
import br.edu.unip.siab.accesslevel.NivelAcessoRepository;
import br.edu.unip.siab.auditlog.AccessLogRepository;
import br.edu.unip.siab.pipeline.feature.FaceEmbedding;
import br.edu.unip.siab.pipeline.feature.FaceEmbeddingImagemRepository;
import br.edu.unip.siab.pipeline.feature.FaceEmbeddingRepository;
import br.edu.unip.siab.user.dto.UsuarioRequest;
import jakarta.persistence.EntityNotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

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
    private final PinService pinService;
    private final FaceEmbeddingRepository faceEmbeddingRepository;
    private final FaceEmbeddingImagemRepository faceEmbeddingImagemRepository;
    private final AccessLogRepository accessLogRepository;

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
        if (request.pin() != null) {
            usuario.setPinHash(pinService.gerarHash(request.pin()));
        }

        return usuarioRepository.save(usuario);
    }

    public Usuario atualizar(Long id, UsuarioRequest request) {
        Usuario usuario = buscarPorId(id);
        NivelAcesso nivel = nivelAcessoRepository.findById(request.nivelAcessoId())
                .orElseThrow(() -> new EntityNotFoundException("Nível de acesso não encontrado: " + request.nivelAcessoId()));

        usuario.setNome(request.nome());
        usuario.setCargo(request.cargo());
        usuario.setNivelAcesso(nivel);
        if (request.pin() != null) {
            usuario.setPinHash(pinService.gerarHash(request.pin()));
        }

        return usuarioRepository.save(usuario);
    }

    /**
     * Exclusão completa (LGPD, art. 16 e 18, VI): apaga as fotos e os
     * vetores biométricos do usuário — antes a exclusão falhava por FK
     * quando havia rosto cadastrado — e solta a FK dos logs de acesso,
     * que continuam guardando só o id ({@code usuario_ref}) para não
     * quebrar a cadeia de auditoria.
     */
    @Transactional
    public void excluir(Long id) {
        Usuario usuario = buscarPorId(id);
        for (FaceEmbedding embedding : faceEmbeddingRepository.findByUsuarioId(id)) {
            faceEmbeddingImagemRepository.findByFaceEmbeddingId(embedding.getId())
                    .ifPresent(faceEmbeddingImagemRepository::delete);
            faceEmbeddingRepository.delete(embedding);
        }
        accessLogRepository.desvincularUsuario(id);
        usuarioRepository.delete(usuario);
    }
}
