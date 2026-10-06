package br.edu.unip.siab.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * Liga as tarefas agendadas: a selagem periódica do log de auditoria (ver
 * CadeiaAuditoriaService, desligável com
 * {@code siab.auditoria.selagem-automatica=false}) e a retenção das fotos
 * biométricas (ver RetencaoFotosService, desligável com
 * {@code siab.biometria.retencao-cron=-}).
 */
@Configuration
@EnableScheduling
public class AgendamentoConfig {
}
