package br.edu.unip.siab.pipeline.feature;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;

/**
 * Tabela "face_embedding_imagens" — foto original enviada no cadastro
 * (Fase 1), guardada como referência ligada ao {@link FaceEmbedding}
 * gerado a partir dela.
 * <p>
 * Motivação: (1) permitir auditoria visual pelo painel administrativo de
 * quem está cadastrado; (2) acumular um dataset real de capturas para a
 * futura calibração experimental do threshold de reconhecimento (Fase 5) e
 * do threshold de liveness, hoje marcados como "chute inicial" no código.
 * <p>
 * Fica em tabela própria (não como coluna em FaceEmbedding) de propósito:
 * RecognitionService varre TODOS os embeddings a cada tentativa de
 * reconhecimento (findAll()) só para comparar o vetor — se a imagem
 * (potencialmente vários KB) estivesse na mesma linha/tabela, o Hibernate
 * carregaria todas as fotos na memória a cada scan, sem necessidade.
 * <p>
 * Dado biométrico sensível (LGPD, art. 5º, II) — nunca exposto por rota
 * pública; só acessível autenticado via /api/admin/** (ver SecurityConfig),
 * conforme RNF02 do escopo ("nunca a imagem original... sem controle de
 * acesso").
 */
@Entity
@Table(name = "face_embedding_imagens")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class FaceEmbeddingImagem {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @OneToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "face_embedding_id", nullable = false, unique = true)
    private FaceEmbedding faceEmbedding;

    @Lob
    @Column(nullable = false)
    private byte[] imagem;

    @Column(name = "content_type", nullable = false)
    private String contentType;

    @Column(name = "criado_em", nullable = false, updatable = false)
    private LocalDateTime criadoEm = LocalDateTime.now();
}
