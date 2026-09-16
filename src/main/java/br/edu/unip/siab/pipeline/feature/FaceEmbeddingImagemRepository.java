package br.edu.unip.siab.pipeline.feature;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface FaceEmbeddingImagemRepository extends JpaRepository<FaceEmbeddingImagem, Long> {

    Optional<FaceEmbeddingImagem> findByFaceEmbeddingId(Long faceEmbeddingId);
}
