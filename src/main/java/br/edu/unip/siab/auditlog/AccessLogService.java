package br.edu.unip.siab.auditlog;

import br.edu.unip.siab.user.Usuario;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

/**
 * Módulo "audit-log" (seção 5.2 do escopo) — RF05.
 * Registra e consulta o histórico de tentativas de autenticação.
 */
@Service
@RequiredArgsConstructor
public class AccessLogService {

    private final AccessLogRepository accessLogRepository;

    public AccessLog registrar(Optional<Usuario> usuario, AccessLog.Resultado resultado, double similaridade) {
        AccessLog log = new AccessLog();
        usuario.ifPresent(log::setUsuario);
        log.setResultado(resultado);
        log.setSimilaridade(similaridade);
        return accessLogRepository.save(log);
    }

    public List<AccessLog> listarTodos() {
        return accessLogRepository.findAllByOrderByDataHoraDesc();
    }

    public List<AccessLog> listarPorUsuario(Long usuarioId) {
        return accessLogRepository.findByUsuarioIdOrderByDataHoraDesc(usuarioId);
    }

    /**
     * Usada pelo módulo "reporting" (RF06/seção 5.7 do escopo) para filtrar
     * o relatório de auditoria em PDF por usuário e/ou período — os dois
     * filtros são opcionais e combináveis.
     */
    public List<AccessLog> listarComFiltros(Long usuarioId, LocalDateTime inicio, LocalDateTime fim) {
        boolean temUsuario = usuarioId != null;
        boolean temPeriodo = inicio != null && fim != null;

        if (temUsuario && temPeriodo) {
            return accessLogRepository.findByUsuarioIdAndDataHoraBetweenOrderByDataHoraDesc(usuarioId, inicio, fim);
        }
        if (temUsuario) {
            return accessLogRepository.findByUsuarioIdOrderByDataHoraDesc(usuarioId);
        }
        if (temPeriodo) {
            return accessLogRepository.findByDataHoraBetweenOrderByDataHoraDesc(inicio, fim);
        }
        return listarTodos();
    }
}
