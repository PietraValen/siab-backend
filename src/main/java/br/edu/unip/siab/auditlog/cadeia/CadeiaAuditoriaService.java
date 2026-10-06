package br.edu.unip.siab.auditlog.cadeia;

import br.edu.unip.siab.auditlog.AccessLog;
import br.edu.unip.siab.auditlog.AccessLogRepository;
import br.edu.unip.siab.auditlog.AcaoAdministrativa;
import br.edu.unip.siab.auditlog.AcaoAdministrativaRepository;
import br.edu.unip.siab.crypto.AssinaturaHibridaService;
import br.edu.unip.siab.crypto.AssinaturaHibridaService.AssinaturaHibrida;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Sela e verifica as duas cadeias do log de auditoria (seção 2 + 4.4 do
 * roteiro de segurança).
 * <p>
 * <b>Selar</b>: assina com Ed25519 + ML-DSA-65 o último hash de cada
 * cadeia e grava um {@link CheckpointAuditoria}. Roda sozinho de tempos em
 * tempos ({@code siab.auditoria.intervalo-selagem-ms}, padrão 1 h), a cada
 * PDF exportado e sob demanda pelo painel.
 * <p>
 * <b>Verificar</b>: recalcula as duas correntes do zero e confere todos os
 * selos. Detecta registro editado, registro apagado do meio, registro
 * inserido por fora da aplicação e qualquer alteração anterior a um selo
 * (mesmo que quem alterou tenha recalculado a corrente). Limitação
 * inerente: registros posteriores ao último selo podem ser apagados do fim
 * sem deixar rastro — por isso a selagem periódica.
 */
@Service
public class CadeiaAuditoriaService {

    private static final Logger log = LoggerFactory.getLogger(CadeiaAuditoriaService.class);

    public static final String TABELA_ACESSOS = "logs_acesso";
    public static final String TABELA_ACOES = "acoes_administrativas";

    private final AccessLogRepository accessLogRepository;
    private final AcaoAdministrativaRepository acaoRepository;
    private final CheckpointAuditoriaRepository checkpointRepository;
    private final AssinaturaHibridaService assinatura;
    private final boolean selagemAutomatica;

    public CadeiaAuditoriaService(AccessLogRepository accessLogRepository, AcaoAdministrativaRepository acaoRepository,
                                  CheckpointAuditoriaRepository checkpointRepository, AssinaturaHibridaService assinatura,
                                  @Value("${siab.auditoria.selagem-automatica:true}") boolean selagemAutomatica) {
        this.accessLogRepository = accessLogRepository;
        this.acaoRepository = acaoRepository;
        this.checkpointRepository = checkpointRepository;
        this.assinatura = assinatura;
        this.selagemAutomatica = selagemAutomatica;
    }

    public record RelatorioIntegridade(
            boolean integra,
            int acessosVerificados,
            int acoesVerificadas,
            int registrosLegados,
            int checkpointsVerificados,
            List<String> problemas,
            String impressaoDigitalChaves
    ) {
    }

    @Scheduled(initialDelayString = "${siab.auditoria.intervalo-selagem-ms:3600000}",
            fixedDelayString = "${siab.auditoria.intervalo-selagem-ms:3600000}")
    public void selarPeriodicamente() {
        if (!selagemAutomatica) {
            return;
        }
        List<CheckpointAuditoria> novos = selar();
        if (!novos.isEmpty()) {
            log.info("Auditoria: {} checkpoint(s) assinados (Ed25519 + ML-DSA-65).", novos.size());
        }
    }

    /** Sela o último registro de cada cadeia que mudou desde o selo anterior. */
    @Transactional
    public synchronized List<CheckpointAuditoria> selar() {
        List<CheckpointAuditoria> criados = new ArrayList<>();
        accessLogRepository.findTopByOrderByIdDesc()
                .filter(ultimo -> ultimo.getHash() != null)
                .flatMap(ultimo -> selarSeMudou(TABELA_ACESSOS, ultimo.getId(), ultimo.getHash()))
                .ifPresent(criados::add);
        acaoRepository.findTopByOrderByIdDesc()
                .filter(ultimo -> ultimo.getHash() != null)
                .flatMap(ultimo -> selarSeMudou(TABELA_ACOES, ultimo.getId(), ultimo.getHash()))
                .ifPresent(criados::add);
        return criados;
    }

    private Optional<CheckpointAuditoria> selarSeMudou(String tabela, Long ultimoId, String ultimoHash) {
        boolean jaSelado = checkpointRepository.findTopByTabelaOrderByIdDesc(tabela)
                .map(c -> c.getUltimoId().equals(ultimoId) && c.getUltimoHash().equals(ultimoHash))
                .orElse(false);
        if (jaSelado) {
            return Optional.empty();
        }
        CheckpointAuditoria checkpoint = new CheckpointAuditoria();
        checkpoint.setTabela(tabela);
        checkpoint.setUltimoId(ultimoId);
        checkpoint.setUltimoHash(ultimoHash);
        AssinaturaHibrida assinado = assinatura.assinar(checkpoint.mensagemAssinada().getBytes(StandardCharsets.UTF_8));
        checkpoint.setAssinaturaEd25519(assinado.ed25519());
        checkpoint.setAssinaturaMlDsa(assinado.mlDsa());
        checkpoint.setImpressaoDigital(assinatura.impressaoDigital());
        return Optional.of(checkpointRepository.save(checkpoint));
    }

    public Optional<CheckpointAuditoria> ultimoSelo(String tabela) {
        return checkpointRepository.findTopByTabelaOrderByIdDesc(tabela);
    }

    @Transactional(readOnly = true)
    public RelatorioIntegridade verificar() {
        List<String> problemas = new ArrayList<>();
        int[] legados = {0};

        List<Elo> acessos = accessLogRepository.findAllByOrderByIdAsc().stream()
                .map(l -> new Elo(l.getId(), l.getHashAnterior(), l.getHash(), l.conteudoCanonico()))
                .toList();
        List<Elo> acoes = acaoRepository.findAllByOrderByIdAsc().stream()
                .map(a -> new Elo(a.getId(), a.getHashAnterior(), a.getHash(), a.conteudoCanonico()))
                .toList();

        Map<String, Map<Long, String>> hashesPorTabela = Map.of(
                TABELA_ACESSOS, percorrer(TABELA_ACESSOS, acessos, problemas, legados),
                TABELA_ACOES, percorrer(TABELA_ACOES, acoes, problemas, legados));

        List<CheckpointAuditoria> checkpoints = checkpointRepository.findAllByOrderByIdAsc();
        String impressaoAtual = assinatura.impressaoDigital();
        for (CheckpointAuditoria c : checkpoints) {
            if (!impressaoAtual.equals(c.getImpressaoDigital())) {
                problemas.add("Checkpoint #" + c.getId() + " foi assinado por outro par de chaves (" + c.getImpressaoDigital() + ").");
                continue;
            }
            boolean assinaturaOk = assinatura.verificar(c.mensagemAssinada().getBytes(StandardCharsets.UTF_8),
                    new AssinaturaHibrida(c.getAssinaturaEd25519(), c.getAssinaturaMlDsa()));
            if (!assinaturaOk) {
                problemas.add("Checkpoint #" + c.getId() + " tem assinatura inválida (selo adulterado).");
                continue;
            }
            String hashAtual = hashesPorTabela.getOrDefault(c.getTabela(), Map.of()).get(c.getUltimoId());
            if (!c.getUltimoHash().equals(hashAtual)) {
                problemas.add("Checkpoint #" + c.getId() + ": " + c.getTabela() + " #" + c.getUltimoId()
                        + " não bate com o hash selado em " + c.getDataHora() + " (cadeia alterada antes do selo).");
            }
        }

        return new RelatorioIntegridade(problemas.isEmpty(), acessos.size(), acoes.size(), legados[0],
                checkpoints.size(), problemas, impressaoAtual);
    }

    private record Elo(Long id, String hashAnterior, String hash, String conteudo) {
    }

    private static Map<Long, String> percorrer(String tabela, List<Elo> elos, List<String> problemas, int[] legados) {
        Map<Long, String> hashes = new HashMap<>();
        String anterior = null;
        for (Elo elo : elos) {
            if (elo.hash() == null) {
                if (anterior == null) {
                    legados[0]++; // registro anterior à existência da cadeia
                } else {
                    problemas.add(tabela + " #" + elo.id() + " está sem hash (inserido por fora da aplicação?).");
                }
                continue;
            }
            String esperadoAnterior = anterior == null ? CadeiaHash.GENESE : anterior;
            if (!esperadoAnterior.equals(elo.hashAnterior())) {
                problemas.add(tabela + " #" + elo.id() + ": elo quebrado (registro anterior apagado ou alterado).");
            }
            if (!CadeiaHash.proximo(elo.hashAnterior() == null ? "" : elo.hashAnterior(), elo.conteudo()).equals(elo.hash())) {
                problemas.add(tabela + " #" + elo.id() + ": conteúdo não confere com o hash (registro alterado).");
            }
            hashes.put(elo.id(), elo.hash());
            anterior = elo.hash();
        }
        return hashes;
    }
}
