package br.edu.unip.siab.pipeline.feature;

import br.edu.unip.siab.user.Usuario;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;

/**
 * Tabela "face_embeddings" (seção 5.4 do escopo).
 * Armazena o vetor de características gerado na Fase 4 (Extração de
 * Características) para cada captura de referência de um usuário.
 * Relação 1:N com Usuario, permitindo múltiplas capturas por pessoa.
 * <p>
 * O vetor é serializado como uma string de números separados por vírgula
 * (formato simples, fácil de auditar/exportar). Se o volume de dados
 * justificar, pode-se migrar para um tipo BLOB/JSON binário mais compacto.
 */
@Entity
@Table(name = "face_embeddings")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class FaceEmbedding {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "usuario_id", nullable = false)
    private Usuario usuario;

    @Lob
    @Column(nullable = false)
    private String vetor; // ex.: "0.123,-0.045,0.998,..."

    @Column(nullable = false)
    private String algoritmo; // ex.: "LBPH" ou "ONNX-FaceEmbedding-v1"

    @Column(name = "criado_em", nullable = false, updatable = false)
    private LocalDateTime criadoEm = LocalDateTime.now();
}
