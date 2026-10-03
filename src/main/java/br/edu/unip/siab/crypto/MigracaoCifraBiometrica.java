package br.edu.unip.siab.crypto;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.util.Base64;

/**
 * Recifra, na inicialização, os vetores e fotos gravados em texto puro
 * antes da cifra híbrida existir. Usa JDBC direto (e não as entidades)
 * porque os conversores JPA devolvem o valor legado como está — pelo JPA
 * não dá para distinguir "já cifrado" de "texto puro". Idempotente: linhas
 * já cifradas são ignoradas.
 */
@Component
public class MigracaoCifraBiometrica implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(MigracaoCifraBiometrica.class);

    private final JdbcTemplate jdbc;
    private final CifraHibridaService cifra;

    public MigracaoCifraBiometrica(JdbcTemplate jdbc, CifraHibridaService cifra) {
        this.jdbc = jdbc;
        this.cifra = cifra;
    }

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        int vetores = jdbc.query("SELECT id, vetor FROM face_embeddings", rs -> {
            int total = 0;
            while (rs.next()) {
                String vetor = rs.getString("vetor");
                if (vetor != null && !vetor.startsWith(CampoTextoCifradoConverter.PREFIXO)) {
                    byte[] envelope = cifra.cifrar(vetor.getBytes(StandardCharsets.UTF_8), Conversores.VetorFacial.CONTEXTO);
                    jdbc.update("UPDATE face_embeddings SET vetor = ? WHERE id = ?",
                            CampoTextoCifradoConverter.PREFIXO + Base64.getEncoder().encodeToString(envelope), rs.getLong("id"));
                    total++;
                }
            }
            return total;
        });

        int imagens = jdbc.query("SELECT id, imagem FROM face_embedding_imagens", rs -> {
            int total = 0;
            while (rs.next()) {
                byte[] imagem = rs.getBytes("imagem");
                if (imagem != null && !cifra.estaCifrado(imagem)) {
                    jdbc.update("UPDATE face_embedding_imagens SET imagem = ? WHERE id = ?",
                            cifra.cifrar(imagem, Conversores.ImagemFacial.CONTEXTO), rs.getLong("id"));
                    total++;
                }
            }
            return total;
        });

        if (vetores + imagens > 0) {
            log.info("Cifra híbrida: {} vetor(es) e {} foto(s) legados recifrados com X25519+ML-KEM-768/AES-256-GCM.",
                    vetores, imagens);
        }
    }
}
