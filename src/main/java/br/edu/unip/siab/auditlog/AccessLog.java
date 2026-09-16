package br.edu.unip.siab.auditlog;

import br.edu.unip.siab.user.Usuario;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;

/**
 * Tabela "logs_acesso" (seção 5.4 do escopo) — RF05.
 * Usuario é opcional: uma tentativa negada por "ninguém reconhecido" não
 * tem usuário associado.
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
    private Usuario usuario; // pode ser nulo se ninguém foi reconhecido

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private Resultado resultado;

    private Double similaridade;

    @Column(name = "data_hora", nullable = false)
    private LocalDateTime dataHora = LocalDateTime.now();
}
