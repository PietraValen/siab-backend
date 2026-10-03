package br.edu.unip.siab.auditlog;

import br.edu.unip.siab.auditlog.cadeia.CadeiaHash;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;

/**
 * Tabela "acoes_administrativas" — trilha do que os administradores fazem
 * no painel (criar/excluir usuário, cadastrar rosto, ver foto biométrica,
 * exportar relatório, logins). Encadeada por hash como o
 * {@link AccessLog}. Exigida pela LGPD para tratamento de dado sensível:
 * quem acessou a biometria de quem, e quando.
 */
@Entity
@Table(name = "acoes_administrativas")
@Getter
@Setter
@NoArgsConstructor
public class AcaoAdministrativa {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "data_hora", nullable = false)
    private LocalDateTime dataHora = LocalDateTime.now().truncatedTo(ChronoUnit.MILLIS);

    /** Username do admin autenticado, ou o informado no login (tentativas). */
    @Column(nullable = false)
    private String administrador;

    @Column(nullable = false, length = 64)
    private String acao;

    @Column(length = 1000)
    private String detalhe;

    @Column(length = 64)
    private String ip;

    @Column(name = "hash_anterior", length = 64)
    private String hashAnterior;

    @Column(length = 64)
    private String hash;

    public String conteudoCanonico() {
        return CadeiaHash.juntar("ADMIN", dataHora, administrador, acao, detalhe, ip);
    }
}
