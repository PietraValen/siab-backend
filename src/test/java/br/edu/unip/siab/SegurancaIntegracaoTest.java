package br.edu.unip.siab;

import br.edu.unip.siab.accesslevel.NivelAcesso;
import br.edu.unip.siab.accesslevel.NivelAcessoRepository;
import br.edu.unip.siab.auditlog.cadeia.CadeiaAuditoriaService;
import br.edu.unip.siab.auditlog.cadeia.CadeiaHash;
import br.edu.unip.siab.pipeline.feature.FaceEmbedding;
import br.edu.unip.siab.pipeline.feature.FaceEmbeddingImagem;
import br.edu.unip.siab.pipeline.feature.FaceEmbeddingImagemRepository;
import br.edu.unip.siab.pipeline.feature.FaceEmbeddingRepository;
import br.edu.unip.siab.user.Usuario;
import br.edu.unip.siab.user.UsuarioRepository;
import com.jayway.jsonpath.JsonPath;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Base64;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.hasItem;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Ponta a ponta, com o contexto inteiro (H2 em memória, cadeia real do
 * Spring Security, OpenCV real): o roteiro de segurança funcionando junto.
 */
@SpringBootTest
@AutoConfigureMockMvc
@TestPropertySource(properties = {
        "spring.datasource.url=jdbc:h2:mem:seguranca;DB_CLOSE_DELAY=-1",
        "spring.datasource.driver-class-name=org.h2.Driver",
        "spring.jpa.hibernate.ddl-auto=create-drop",
        "spring.sql.init.mode=never"
})
class SegurancaIntegracaoTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JdbcTemplate jdbc;

    @Autowired
    private NivelAcessoRepository nivelAcessoRepository;

    @Autowired
    private UsuarioRepository usuarioRepository;

    @Autowired
    private FaceEmbeddingRepository faceEmbeddingRepository;

    @Autowired
    private FaceEmbeddingImagemRepository faceEmbeddingImagemRepository;

    @Autowired
    private CadeiaAuditoriaService cadeiaAuditoriaService;

    private Cookie sessao;
    private String csrf;
    private Cookie cookieCsrf;

    @BeforeEach
    void entrarComoAdmin() throws Exception {
        if (nivelAcessoRepository.count() == 0) {
            for (String nome : List.of("Acesso Geral", "Diretoria", "Ministro")) {
                NivelAcesso nivel = new NivelAcesso();
                nivel.setNome(nome);
                nivelAcessoRepository.save(nivel);
            }
        }
        String corpo = "{ \"username\": \"admin-it\", \"senha\": \"SenhaForte123!\" }";
        if (jdbc.queryForObject("SELECT COUNT(*) FROM administradores", Integer.class) == 0) {
            mockMvc.perform(post("/api/admin/administradores").contentType("application/json").content(corpo))
                    .andExpect(status().isOk());
        }

        MvcResult login = mockMvc.perform(post("/api/auth/login").contentType("application/json")
                        .content("{ \"username\": \"admin-it\", \"password\": \"SenhaForte123!\" }"))
                .andExpect(status().isOk())
                .andReturn();
        sessao = login.getResponse().getCookie("SIAB_TOKEN");
        assertThat(sessao).isNotNull();
        assertThat(sessao.isHttpOnly()).isTrue();

        MvcResult resultadoCsrf = mockMvc.perform(get("/api/auth/csrf").cookie(sessao)).andReturn();
        csrf = JsonPath.read(resultadoCsrf.getResponse().getContentAsString(), "$.token");
        cookieCsrf = resultadoCsrf.getResponse().getCookie("XSRF-TOKEN");
    }

    private Long nivelId(String nome) {
        return nivelAcessoRepository.findAll().stream().filter(n -> n.getNome().equals(nome)).findFirst().orElseThrow().getId();
    }

    @Test
    void cookieDoPainelExigeTokenCsrfEmRequisicaoQueAlteraDados() throws Exception {
        String corpo = "{ \"nome\": \"Porta CSRF\", \"nivelExigidoId\": " + nivelId("Acesso Geral") + " }";

        mockMvc.perform(post("/api/admin/terminais").cookie(sessao).contentType("application/json").content(corpo))
                .andExpect(status().isForbidden());

        mockMvc.perform(post("/api/admin/terminais").cookie(sessao, cookieCsrf).header("X-XSRF-TOKEN", csrf)
                        .contentType("application/json").content(corpo))
                .andExpect(status().isOk());

        // Leitura com o cookie não precisa de CSRF.
        mockMvc.perform(get("/api/admin/sessao").cookie(sessao))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.username").value("admin-it"));
    }

    @Test
    void logoutRevogaOToken() throws Exception {
        // Com o cookie, logout também exige CSRF (senão outro site derrubaria a sessão do admin).
        mockMvc.perform(post("/api/auth/logout").cookie(sessao)).andExpect(status().isForbidden());
        mockMvc.perform(post("/api/auth/logout").cookie(sessao, cookieCsrf).header("X-XSRF-TOKEN", csrf))
                .andExpect(status().isNoContent());

        mockMvc.perform(get("/api/admin/sessao").cookie(sessao)).andExpect(status().isForbidden());
    }

    @Test
    void scanSoAceitaTerminalAssinadoEUmaVezPorDesafio() throws Exception {
        String corpo = "{ \"nome\": \"Porta Ministro\", \"nivelExigidoId\": " + nivelId("Ministro") + " }";
        String resposta = mockMvc.perform(post("/api/admin/terminais").cookie(sessao, cookieCsrf).header("X-XSRF-TOKEN", csrf)
                        .contentType("application/json").content(corpo))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        Number terminalId = JsonPath.read(resposta, "$.terminal.id");
        String chave = JsonPath.read(resposta, "$.chave");

        mockMvc.perform(get("/api/admin/terminais").cookie(sessao))
                .andExpect(jsonPath("$[*].chave").doesNotExist());

        String desafio = mockMvc.perform(get("/api/recognition/desafio").header("X-Terminal-Id", terminalId.toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.exigePin").value(true))
                .andReturn().getResponse().getContentAsString();
        String nonce = JsonPath.read(desafio, "$.nonce");

        byte[] frame = jpegPreto();
        String timestamp = String.valueOf(System.currentTimeMillis());
        String mensagem = String.join("\n", "SIAB-SCAN-v1", terminalId.toString(), nonce, timestamp, sha256Hex(frame), "");
        String assinatura = Base64.getEncoder().encodeToString(hmac(chave, mensagem));

        var scan = multipart("/api/recognition/scan").file(new MockMultipartFile("imagens", "f.jpg", "image/jpeg", frame))
                .header("X-Terminal-Id", terminalId.toString()).header("X-Desafio", nonce)
                .header("X-Timestamp", timestamp).header("X-Assinatura", assinatura);

        mockMvc.perform(scan)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.acessoConcedido").value(false))
                .andExpect(jsonPath("$.similaridade").doesNotExist());

        // Replay da mesma requisição: o desafio já foi gasto.
        mockMvc.perform(scan).andExpect(status().isUnauthorized());

        // Foto enviada direto, sem assinatura de terminal.
        mockMvc.perform(multipart("/api/recognition/scan").file(new MockMultipartFile("imagens", "f.jpg", "image/jpeg", frame)))
                .andExpect(status().isUnauthorized());

        // Arquivo que não é imagem.
        String nonce2 = JsonPath.read(mockMvc.perform(get("/api/recognition/desafio").header("X-Terminal-Id", terminalId.toString()))
                .andReturn().getResponse().getContentAsString(), "$.nonce");
        byte[] falso = "<?php echo 1; ?>".getBytes(StandardCharsets.UTF_8);
        String ts2 = String.valueOf(System.currentTimeMillis());
        String assinatura2 = Base64.getEncoder().encodeToString(hmac(chave,
                String.join("\n", "SIAB-SCAN-v1", terminalId.toString(), nonce2, ts2, sha256Hex(falso), "")));
        mockMvc.perform(multipart("/api/recognition/scan").file(new MockMultipartFile("imagens", "f.jpg", "image/jpeg", falso))
                        .header("X-Terminal-Id", terminalId.toString()).header("X-Desafio", nonce2)
                        .header("X-Timestamp", ts2).header("X-Assinatura", assinatura2))
                .andExpect(status().isBadRequest());

        // As tentativas recusadas ficam na auditoria, com o motivo real.
        mockMvc.perform(get("/api/admin/logs").cookie(sessao))
                .andExpect(jsonPath("$[*].motivo", hasItem(org.hamcrest.Matchers.startsWith("Terminal não autorizado"))))
                .andExpect(jsonPath("$[*].motivo", hasItem("Nenhum rosto detectado.")));
    }

    @Test
    void biometriaFicaCifradaNoBancoEExclusaoApagaTudo() throws Exception {
        Usuario usuario = new Usuario();
        usuario.setNome("Titular LGPD");
        usuario.setNivelAcesso(nivelAcessoRepository.findById(nivelId("Acesso Geral")).orElseThrow());
        usuario = usuarioRepository.save(usuario);

        FaceEmbedding embedding = new FaceEmbedding();
        embedding.setUsuario(usuario);
        embedding.setVetor("0.111,0.222,0.333");
        embedding.setAlgoritmo("LBPH");
        embedding = faceEmbeddingRepository.save(embedding);

        FaceEmbeddingImagem imagem = new FaceEmbeddingImagem();
        imagem.setFaceEmbedding(embedding);
        imagem.setImagem(jpegPreto());
        imagem.setContentType("image/jpeg");
        faceEmbeddingImagemRepository.save(imagem);

        String vetorNoBanco = jdbc.queryForObject("SELECT vetor FROM face_embeddings WHERE id = ?", String.class, embedding.getId());
        byte[] fotoNoBanco = jdbc.queryForObject("SELECT imagem FROM face_embedding_imagens WHERE face_embedding_id = ?",
                byte[].class, embedding.getId());
        assertThat(vetorNoBanco).startsWith("enc:v1:").doesNotContain("0.222");
        assertThat(new String(fotoNoBanco, 0, 4, StandardCharsets.US_ASCII)).isEqualTo("SIAB");

        assertThat(faceEmbeddingRepository.findById(embedding.getId()).orElseThrow().getVetor()).isEqualTo("0.111,0.222,0.333");

        mockMvc.perform(delete("/api/admin/usuarios/" + usuario.getId()).cookie(sessao, cookieCsrf).header("X-XSRF-TOKEN", csrf))
                .andExpect(status().isNoContent());
        assertThat(faceEmbeddingRepository.findByUsuarioId(usuario.getId())).isEmpty();
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM face_embedding_imagens WHERE face_embedding_id = ?",
                Integer.class, embedding.getId())).isZero();
    }

    @Autowired
    private br.edu.unip.siab.crypto.MigracaoCifraBiometrica migracaoCifraBiometrica;

    @Test
    void vetorLegadoEmTextoPuroEhRecifradoNaMigracao() {
        Usuario usuario = new Usuario();
        usuario.setNome("Cadastro antigo");
        usuario.setNivelAcesso(nivelAcessoRepository.findById(nivelId("Acesso Geral")).orElseThrow());
        usuario = usuarioRepository.save(usuario);
        jdbc.update("INSERT INTO face_embeddings (usuario_id, vetor, algoritmo, criado_em) VALUES (?, ?, 'LBPH', CURRENT_TIMESTAMP)",
                usuario.getId(), "0.9,0.8,0.7");
        Long id = jdbc.queryForObject("SELECT MAX(id) FROM face_embeddings", Long.class);

        assertThat(faceEmbeddingRepository.findById(id).orElseThrow().getVetor()).isEqualTo("0.9,0.8,0.7"); // legado ainda legível

        migracaoCifraBiometrica.run(null);

        assertThat(jdbc.queryForObject("SELECT vetor FROM face_embeddings WHERE id = ?", String.class, id)).startsWith("enc:v1:");
        assertThat(faceEmbeddingRepository.findById(id).orElseThrow().getVetor()).isEqualTo("0.9,0.8,0.7");
    }

    @Test
    void adulteracaoDoLogDeAuditoriaEhDetectadaMesmoRecalculandoACadeia() throws Exception {
        mockMvc.perform(get("/api/admin/auditoria/verificacao").cookie(sessao))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.integra").value(true));
        mockMvc.perform(post("/api/admin/auditoria/selar").cookie(sessao, cookieCsrf).header("X-XSRF-TOKEN", csrf))
                .andExpect(status().isOk());

        // Ataque ingênuo: editar uma linha. Quebra o hash daquela linha.
        Long alvo = jdbc.queryForObject("SELECT MIN(id) FROM acoes_administrativas", Long.class);
        jdbc.update("UPDATE acoes_administrativas SET detalhe = 'nada aconteceu' WHERE id = ?", alvo);
        assertThat(cadeiaAuditoriaService.verificar().integra()).isFalse();

        // Ataque esperto: editar e recalcular a cadeia inteira. O selo assinado
        // (Ed25519 + ML-DSA-65) não bate mais.
        List<Map<String, Object>> linhas = jdbc.queryForList("SELECT id FROM acoes_administrativas ORDER BY id");
        String anterior = CadeiaHash.GENESE;
        for (Map<String, Object> linha : linhas) {
            Long id = ((Number) linha.get("ID")).longValue();
            var registro = cadeiaAcao(id);
            String hash = CadeiaHash.proximo(anterior, registro);
            jdbc.update("UPDATE acoes_administrativas SET hash_anterior = ?, hash = ? WHERE id = ?", anterior, hash, id);
            anterior = hash;
        }
        var relatorio = cadeiaAuditoriaService.verificar();
        assertThat(relatorio.integra()).isFalse();
        assertThat(relatorio.problemas()).anyMatch(p -> p.contains("Checkpoint"));
    }

    private String cadeiaAcao(Long id) {
        Map<String, Object> l = jdbc.queryForMap("SELECT * FROM acoes_administrativas WHERE id = ?", id);
        var dataHora = ((java.sql.Timestamp) l.get("DATA_HORA")).toLocalDateTime();
        return CadeiaHash.juntar("ADMIN", dataHora, l.get("ADMINISTRADOR"), l.get("ACAO"), l.get("DETALHE"), l.get("IP"));
    }

    private static byte[] jpegPreto() throws Exception {
        ByteArrayOutputStream saida = new ByteArrayOutputStream();
        ImageIO.write(new BufferedImage(64, 64, BufferedImage.TYPE_INT_RGB), "jpg", saida);
        return saida.toByteArray();
    }

    private static String sha256Hex(byte[] dados) throws Exception {
        return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(dados));
    }

    private static byte[] hmac(String chaveBase64, String mensagem) throws Exception {
        Mac mac = Mac.getInstance("HmacSHA256");
        mac.init(new SecretKeySpec(Base64.getDecoder().decode(chaveBase64), "HmacSHA256"));
        return mac.doFinal(mensagem.getBytes(StandardCharsets.UTF_8));
    }
}
