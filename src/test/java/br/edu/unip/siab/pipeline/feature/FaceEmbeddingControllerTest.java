package br.edu.unip.siab.pipeline.feature;

import br.edu.unip.siab.auth.JwtAuthFilter;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.Optional;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Valida o contrato HTTP de consulta da foto de referência de um cadastro
 * (ver Javadoc de FaceEmbeddingImagem): retorna os bytes da imagem com o
 * content-type correto quando existe, e 404 quando não há foto salva para
 * o embedding informado.
 */
@WebMvcTest(FaceEmbeddingController.class)
@AutoConfigureMockMvc(addFilters = false)
class FaceEmbeddingControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private FaceEmbeddingImagemRepository faceEmbeddingImagemRepository;

    @MockitoBean
    private JwtAuthFilter jwtAuthFilter;

    @Test
    @WithMockUser
    void retornaImagemQuandoExisteFotoDeReferencia() throws Exception {
        byte[] bytesDaFoto = { 1, 2, 3, 4 };

        FaceEmbedding embedding = new FaceEmbedding();
        embedding.setId(1L);

        FaceEmbeddingImagem imagem = new FaceEmbeddingImagem();
        imagem.setId(10L);
        imagem.setFaceEmbedding(embedding);
        imagem.setImagem(bytesDaFoto);
        imagem.setContentType("image/jpeg");

        when(faceEmbeddingImagemRepository.findByFaceEmbeddingId(1L)).thenReturn(Optional.of(imagem));

        mockMvc.perform(get("/api/admin/rostos/1/imagem"))
                .andExpect(status().isOk())
                .andExpect(content().contentType("image/jpeg"))
                .andExpect(content().bytes(bytesDaFoto));
    }

    @Test
    @WithMockUser
    void retorna404QuandoNaoHaFotoSalva() throws Exception {
        when(faceEmbeddingImagemRepository.findByFaceEmbeddingId(999L)).thenReturn(Optional.empty());

        mockMvc.perform(get("/api/admin/rostos/999/imagem"))
                .andExpect(status().isNotFound());
    }
}
