package br.edu.unip.siab.pipeline.feature;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.Optional;

public interface FaceEmbeddingImagemRepository extends JpaRepository<FaceEmbeddingImagem, Long> {

    Optional<FaceEmbeddingImagem> findByFaceEmbeddingId(Long faceEmbeddingId);

    /**
     * Apaga em lote, direto no banco, as fotos gravadas antes de {@code limite}.
     * Um {@code deleteBy...} derivado carregaria (e decifraria) cada foto só
     * para apagá-la; aqui nenhuma imagem passa pela memória.
     */
    @Modifying
    @Query("delete from FaceEmbeddingImagem i where i.criadoEm < :limite")
    int apagarCriadasAntesDe(@Param("limite") LocalDateTime limite);

    @Modifying
    @Query("delete from FaceEmbeddingImagem i")
    int apagarTodas();
}
