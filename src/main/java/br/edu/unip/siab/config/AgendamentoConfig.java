package br.edu.unip.siab.config;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * Liga as tarefas agendadas — hoje só a selagem periódica do log de
 * auditoria (ver CadeiaAuditoriaService). Desligável com
 * {@code siab.auditoria.selagem-automatica=false}.
 */
@Configuration
@EnableScheduling
@ConditionalOnProperty(name = "siab.auditoria.selagem-automatica", havingValue = "true", matchIfMissing = true)
public class AgendamentoConfig {
}
