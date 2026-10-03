package br.edu.unip.siab.auditlog;

import br.edu.unip.siab.auditlog.cadeia.CadeiaHash;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import java.util.List;

/**
 * Registra ações do painel administrativo na tabela encadeada
 * {@link AcaoAdministrativa} (seção 2 do roteiro de segurança). Descobre
 * sozinho o admin logado e o IP da requisição corrente.
 */
@Service
public class AuditoriaAdminService {

    private final AcaoAdministrativaRepository repository;
    private final TransactionTemplate novaTransacao;

    public AuditoriaAdminService(AcaoAdministrativaRepository repository, PlatformTransactionManager transactionManager) {
        this.repository = repository;
        this.novaTransacao = new TransactionTemplate(transactionManager);
        this.novaTransacao.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
    }

    /** Ação feita pelo admin autenticado na requisição atual. */
    public AcaoAdministrativa registrar(String acao, String detalhe) {
        return registrarComo(adminAtual(), acao, detalhe);
    }

    /** Ação atribuída a um username explícito (ex.: tentativa de login, ainda sem autenticação). */
    public synchronized AcaoAdministrativa registrarComo(String administrador, String acao, String detalhe) {
        String ip = ipAtual();
        return novaTransacao.execute(status -> {
            AcaoAdministrativa registro = new AcaoAdministrativa();
            registro.setAdministrador(administrador == null || administrador.isBlank() ? "(anônimo)" : truncar(administrador, 255));
            registro.setAcao(acao);
            registro.setDetalhe(truncar(detalhe, 1000));
            registro.setIp(ip);

            String anterior = repository.findTopByOrderByIdDesc()
                    .map(AcaoAdministrativa::getHash)
                    .orElse(CadeiaHash.GENESE);
            registro.setHashAnterior(anterior);
            registro.setHash(CadeiaHash.proximo(anterior, registro.conteudoCanonico()));
            return repository.save(registro);
        });
    }

    public List<AcaoAdministrativa> listarRecentes() {
        return repository.findTop200ByOrderByIdDesc();
    }

    private static String adminAtual() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || auth instanceof AnonymousAuthenticationToken) {
            return null;
        }
        return auth.getName();
    }

    private static String ipAtual() {
        if (RequestContextHolder.getRequestAttributes() instanceof ServletRequestAttributes atributos) {
            HttpServletRequest request = atributos.getRequest();
            return request.getRemoteAddr();
        }
        return null;
    }

    private static String truncar(String texto, int maximo) {
        return texto == null || texto.length() <= maximo ? texto : texto.substring(0, maximo);
    }
}
