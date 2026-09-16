package br.edu.unip.siab.pipeline.feature;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface FaceEmbeddingRepository extends JpaRepository<FaceEmbedding, Long> {

    List<FaceEmbedding> findByUsuarioId(Long usuarioId);
}
