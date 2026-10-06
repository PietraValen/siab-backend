package br.edu.unip.siab.pipeline.feature;

import br.edu.unip.siab.auditlog.AuditoriaAdminService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.LocalDateTime;

/**
 * Prazo de retenção das fotos originais do cadastro ({@link FaceEmbeddingImagem}).
 * <p>
 * O reconhecimento (Fase 5) só usa o vetor LBPH de {@link FaceEmbedding}; a
 * foto existe para auditoria visual e para acumular o dataset de calibração
 * dos thresholds. Passado esse uso, mantê-la só aumenta o estrago de um
 * vazamento de dado biométrico sensível (LGPD, art. 5º, II), e a LGPD manda
 * eliminar o dado ao fim do tratamento (arts. 15 e 16). Por isso:
 * <ul>
 *   <li>uma tarefa diária ({@code siab.biometria.retencao-cron}, padrão 03:30)
 *       apaga as fotos com mais de {@code siab.biometria.retencao-fotos-dias}
 *       dias (padrão 180; 0 desliga o prazo);</li>
 *   <li>o painel pode apagar todas de uma vez, depois que a calibração
 *       terminar ({@code DELETE /api/admin/rostos/imagens}).</li>
 * </ul>
 * Os embeddings continuam intactos, então ninguém precisa se recadastrar.
 * Toda remoção fica na trilha de auditoria.
 */
@Service
public class RetencaoFotosService {

    private static final Logger log = LoggerFactory.getLogger(RetencaoFotosService.class);

    static final String ADMINISTRADOR_SISTEMA = "(sistema)";

    private final FaceEmbeddingImagemRepository repository;
    private final AuditoriaAdminService auditoria;
    private final int diasDeRetencao;
    private final Clock relogio;

    @Autowired
    public RetencaoFotosService(FaceEmbeddingImagemRepository repository, AuditoriaAdminService auditoria,
                                @Value("${siab.biometria.retencao-fotos-dias:180}") int diasDeRetencao) {
        this(repository, auditoria, diasDeRetencao, Clock.systemDefaultZone());
    }

    RetencaoFotosService(FaceEmbeddingImagemRepository repository, AuditoriaAdminService auditoria,
                         int diasDeRetencao, Clock relogio) {
        if (diasDeRetencao < 0) {
            throw new IllegalStateException("siab.biometria.retencao-fotos-dias não pode ser negativo (0 desliga o prazo).");
        }
        this.repository = repository;
        this.auditoria = auditoria;
        this.diasDeRetencao = diasDeRetencao;
        this.relogio = relogio;
    }

    @Scheduled(cron = "${siab.biometria.retencao-cron:0 30 3 * * *}")
    public void expurgarPeriodicamente() {
        int removidas = expurgarVencidas();
        if (removidas > 0) {
            log.info("Retenção: {} foto(s) biométrica(s) com mais de {} dias apagada(s).", removidas, diasDeRetencao);
        }
    }

    /** Apaga as fotos com mais de {@code diasDeRetencao} dias. Retorna quantas saíram. */
    @Transactional
    public int expurgarVencidas() {
        if (diasDeRetencao == 0) {
            return 0;
        }
        int removidas = repository.apagarCriadasAntesDe(LocalDateTime.now(relogio).minusDays(diasDeRetencao));
        if (removidas > 0) {
            auditoria.registrarComo(ADMINISTRADOR_SISTEMA, "FOTOS_BIOMETRICAS_EXPIRADAS",
                    "removidas=" + removidas + ", prazo=" + diasDeRetencao + "d");
        }
        return removidas;
    }

    /** Apaga todas as fotos (ex.: calibração concluída), em nome do admin logado. */
    @Transactional
    public int expurgarTodas() {
        int removidas = repository.apagarTodas();
        auditoria.registrar("FOTOS_BIOMETRICAS_APAGADAS", "removidas=" + removidas);
        return removidas;
    }
}
