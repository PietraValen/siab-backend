package br.edu.unip.siab.crypto;

import jakarta.persistence.Converter;
import org.springframework.stereotype.Component;

/**
 * Conversores concretos, um por coluna sensível. O contexto (AAD) é o nome
 * da coluna: um valor cifrado copiado para outra coluna não decifra.
 */
public final class Conversores {

    private Conversores() {
    }

    /** {@code face_embeddings.vetor} — vetor de características (Fase 4). */
    @Component
    @Converter
    public static class VetorFacial extends CampoTextoCifradoConverter {
        public static final String CONTEXTO = "face_embeddings.vetor";

        public VetorFacial(CifraHibridaService cifra) {
            super(cifra);
        }

        @Override
        protected String contexto() {
            return CONTEXTO;
        }
    }

    /** {@code face_embedding_imagens.imagem} — foto original do cadastro. */
    @Component
    @Converter
    public static class ImagemFacial extends CampoBinarioCifradoConverter {
        public static final String CONTEXTO = "face_embedding_imagens.imagem";

        public ImagemFacial(CifraHibridaService cifra) {
            super(cifra);
        }

        @Override
        protected String contexto() {
            return CONTEXTO;
        }
    }

    /** {@code terminais.chave} — segredo HMAC compartilhado com o terminal do cofre. */
    @Component
    @Converter
    public static class ChaveTerminal extends CampoTextoCifradoConverter {
        public ChaveTerminal(CifraHibridaService cifra) {
            super(cifra);
        }

        @Override
        protected String contexto() {
            return "terminais.chave";
        }
    }

    /** {@code administradores.totp_segredo} — segredo do segundo fator do admin. */
    @Component
    @Converter
    public static class SegredoTotp extends CampoTextoCifradoConverter {
        public SegredoTotp(CifraHibridaService cifra) {
            super(cifra);
        }

        @Override
        protected String contexto() {
            return "administradores.totp_segredo";
        }
    }
}
