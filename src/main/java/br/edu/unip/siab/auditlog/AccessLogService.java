package br.edu.unip.siab.auditlog;

import br.edu.unip.siab.auditlog.cadeia.CadeiaHash;
import br.edu.unip.siab.terminal.Terminal;
import br.edu.unip.siab.user.Usuario;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

/**
 * Módulo "audit-log" (seção 5.2 do escopo) — RF05.
 * Registra e consulta o histórico de tentativas de autenticação.
 */
@Service
public class AccessLogService {

    private final AccessLogRepository accessLogRepository;
    private final TransactionTemplate novaTransacao;

    public AccessLogService(AccessLogRepository accessLogRepository, PlatformTransactionManager transactionManager) {
        this.accessLogRepository = accessLogRepository;
        this.novaTransacao = new TransactionTemplate(transactionManager);
        this.novaTransacao.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
    }

    /** Uma tentativa de acesso a registrar; {@code terminal} e {@code ip} podem ser nulos. */
    public record Tentativa(Optional<Usuario> usuario, AccessLog.Resultado resultado, Double similaridade,
                            String motivo, Terminal terminal, String ip) {
    }

    /**
     * Grava a tentativa encadeada à anterior. Sincronizado e com transação
     * própria commitada <b>dentro</b> do bloco sincronizado: duas tentativas
     * simultâneas nunca leem o mesmo "último hash" (o que bifurcaria a
     * cadeia).
     */
    public synchronized AccessLog registrar(Tentativa tentativa) {
        return novaTransacao.execute(status -> {
            AccessLog log = new AccessLog();
            tentativa.usuario().ifPresent(u -> {
                log.setUsuario(u);
                log.setUsuarioRef(u.getId());
            });
            log.setResultado(tentativa.resultado());
            log.setSimilaridade(tentativa.similaridade());
            log.setMotivo(tentativa.motivo());
            if (tentativa.terminal() != null) {
                log.setTerminalRef(tentativa.terminal().getId());
                log.setTerminalNome(tentativa.terminal().getNome());
            }
            log.setIp(tentativa.ip());

            String anterior = accessLogRepository.findTopByOrderByIdDesc()
                    .map(AccessLog::getHash)
                    .orElse(CadeiaHash.GENESE);
            if (anterior == null) {
                anterior = CadeiaHash.GENESE; // último registro é legado (anterior à cadeia)
            }
            log.setHashAnterior(anterior);
            log.setHash(CadeiaHash.proximo(anterior, log.conteudoCanonico()));
            return accessLogRepository.save(log);
        });
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
