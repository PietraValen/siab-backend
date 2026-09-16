package br.edu.unip.siab.accesslevel;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Tabela "niveis_acesso" (seção 5.4 do escopo).
 * Os 3 registros (geral / diretoria / ministro) são semeados via data.sql.
 */
@Entity
@Table(name = "niveis_acesso")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class NivelAcesso {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true)
    private String nome; // ex.: "Acesso Geral", "Diretoria", "Ministro"

    @Column(length = 500)
    private String descricao;
}
