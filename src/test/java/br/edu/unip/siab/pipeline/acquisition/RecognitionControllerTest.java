package br.edu.unip.siab.pipeline.acquisition;

import br.edu.unip.siab.auth.JwtAuthFilter;
import br.edu.unip.siab.pipeline.PipelineOrchestratorService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.Optional;

import static org.hamcrest.Matchers.nullValue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Regressão: quando nenhum rosto é reconhecido, o orquestrador devolve
 * {@code usuario} vazio e o controller precisa serializar isso como
 * {@code "usuario": null} no JSON (contrato com o tipo ScanResult do
 * front-end). Antes esse campo era montado com {@code Map.of(...)}, que
 * lança NullPointerException em qualquer valor nulo — todo scan sem
 * correspondência (o caminho mais comum, já que o threshold ainda não foi
 * calibrado) derrubava o endpoint com 500.
 */
@WebMvcTest(RecognitionController.class)
@AutoConfigureMockMvc(addFilters = false)
class RecognitionControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private PipelineOrchestratorService pipelineOrchestratorService;

    @MockitoBean
    private JwtAuthFilter jwtAuthFilter;

    @Test
    @WithMockUser
    void rostoNaoReconhecidoRetorna200ComUsuarioNulo() throws Exception {
        var resultado = new PipelineOrchestratorService.ResultadoScan(
                false, Optional.empty(), 0.62, "Usuário não reconhecido.");
        when(pipelineOrchestratorService.reconhecer(any())).thenReturn(resultado);

        MockMultipartFile imagem = new MockMultipartFile("imagem", "captura.jpg", "image/jpeg", new byte[]{1, 2, 3});

        mockMvc.perform(multipart("/api/recognition/scan").file(imagem))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.acessoConcedido").value(false))
                .andExpect(jsonPath("$.usuario").value(nullValue()))
                .andExpect(jsonPath("$.motivo").value("Usuário não reconhecido."));
    }
}
