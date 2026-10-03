package br.edu.unip.siab.auditlog;

import br.edu.unip.siab.auditlog.cadeia.CadeiaHash;
import br.edu.unip.siab.user.Usuario;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;

/**
 * Tabela "logs_acesso" (seção 5.4 do escopo) — RF05.
 * Usuario é opcional: uma tentativa negada por "ninguém reconhecido" não
 * tem usuário associado.
 * <p>
 * Cada linha guarda o hash da anterior (cadeia de hashes, ver
 * {@link CadeiaHash}): apagar ou editar uma linha quebra a corrente e
 * aparece em GET /api/admin/auditoria/verificacao. O conteúdo encadeado usa
 * {@link #usuarioRef} (cópia do id) e não a FK {@link #usuario}, para que a
 * exclusão de um usuário (LGPD) não quebre a cadeia.
 */
@Entity
@Table(name = "logs_acesso")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class AccessLog {

    public enum Resultado { CONCEDIDO, NEGADO }

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "usuario_id")
    private Usuario usuario; // pode ser nulo se ninguém foi reconhecido (ou se o usuário foi excluído)

    /** Id do usuário no momento da tentativa; sobrevive à exclusão do cadastro. */
    @Column(name = "usuario_ref")
    private Long usuarioRef;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private Resultado resultado;

    private Double similaridade;

    /** Motivo detalhado — fica só aqui, nunca vai para a resposta do /scan. */
    private String motivo;

    @Column(name = "terminal_ref")
    private Long terminalRef;

    @Column(name = "terminal_nome")
    private String terminalNome;

    @Column(length = 64)
    private String ip;

    @Column(name = "data_hora", nullable = false)
    private LocalDateTime dataHora = LocalDateTime.now().truncatedTo(ChronoUnit.MILLIS);

    @Column(name = "hash_anterior", length = 64)
    private String hashAnterior;

    @Column(length = 64)
    private String hash;

    /** Conteúdo que entra no hash. Mudar este formato invalida a cadeia existente. */
    public String conteudoCanonico() {
        return CadeiaHash.juntar("ACESSO", dataHora, resultado, usuarioRef, similaridade, motivo, terminalRef, terminalNome, ip);
    }
}
