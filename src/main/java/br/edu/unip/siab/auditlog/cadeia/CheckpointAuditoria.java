package br.edu.unip.siab.auditlog.cadeia;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;

/**
 * Tabela "checkpoints_auditoria" — selo assinado do último hash de uma das
 * cadeias num dado momento. Com o selo, recalcular a cadeia inteira depois
 * de editar o banco não basta para esconder a adulteração: o hash
 * recalculado não bate com o que foi assinado, e só o back-end tem as
 * chaves privadas Ed25519 + ML-DSA-65.
 */
@Entity
@Table(name = "checkpoints_auditoria")
@Getter
@Setter
@NoArgsConstructor
public class CheckpointAuditoria {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "data_hora", nullable = false)
    private LocalDateTime dataHora = LocalDateTime.now().truncatedTo(ChronoUnit.MILLIS);

    /** "logs_acesso" ou "acoes_administrativas". */
    @Column(nullable = false, length = 40)
    private String tabela;

    @Column(name = "ultimo_id", nullable = false)
    private Long ultimoId;

    @Column(name = "ultimo_hash", nullable = false, length = 64)
    private String ultimoHash;

    @Column(name = "assinatura_ed25519", nullable = false, length = 200)
    private String assinaturaEd25519;

    /** ML-DSA-65: 3309 bytes, ~4,4 KB em Base64. */
    @Lob
    @Column(name = "assinatura_ml_dsa", nullable = false)
    private String assinaturaMlDsa;

    @Column(name = "impressao_digital", nullable = false, length = 32)
    private String impressaoDigital;

    public String mensagemAssinada() {
        return CadeiaHash.juntar("SIAB-CHECKPOINT-v1", tabela, ultimoId, ultimoHash, dataHora);
    }
}
