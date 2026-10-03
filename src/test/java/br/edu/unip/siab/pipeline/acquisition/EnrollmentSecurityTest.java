package br.edu.unip.siab.pipeline.acquisition;

import br.edu.unip.siab.auditlog.AuditoriaAdminService;
import br.edu.unip.siab.auth.JwtService;
import br.edu.unip.siab.config.SecurityConfig;
import br.edu.unip.siab.pipeline.PipelineOrchestratorService;
import br.edu.unip.siab.pipeline.feature.FaceEmbedding;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.userdetails.User;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.Collections;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Garante que POST /api/enrollment exige um administrador autenticado.
 * Mantém a cadeia real do Spring Security (via {@code @Import(SecurityConfig.class)},
 * como em {@code AdministradorBootstrapControllerTest}): se o endpoint
 * voltasse a ser público, qualquer pessoa poderia associar o próprio rosto
 * a um usuário existente e passar pelo /scan com o nível de acesso dele.
 */
@WebMvcTest(EnrollmentController.class)
@Import(SecurityConfig.class)
class EnrollmentSecurityTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private PipelineOrchestratorService pipelineOrchestratorService;

    // Dependência do JwtAuthFilter, que entra na cadeia junto com o
    // SecurityConfig importado.
    @MockitoBean
    private JwtService jwtService;

    @MockitoBean
    private AuditoriaAdminService auditoriaAdminService;

    private final MockMultipartFile imagem =
            new MockMultipartFile("imagem", "captura.jpg", "image/jpeg", new byte[]{1, 2, 3});

    @Test
    void recusaCadastroSemToken() throws Exception {
        mockMvc.perform(multipart("/api/enrollment").file(imagem).param("usuarioId", "1"))
                .andExpect(status().isForbidden());

        verify(pipelineOrchestratorService, never()).cadastrarRosto(anyLong(), any());
    }

    @Test
    void permiteCadastroComAdministradorAutenticado() throws Exception {
        FaceEmbedding embedding = new FaceEmbedding();
        embedding.setId(10L);
        embedding.setAlgoritmo("LBPH");
        when(pipelineOrchestratorService.cadastrarRosto(anyLong(), any())).thenReturn(embedding);

        var adminLogado = new UsernamePasswordAuthenticationToken(
                new User("admin-logado", "", Collections.emptyList()), null, Collections.emptyList());

        mockMvc.perform(multipart("/api/enrollment").file(imagem).param("usuarioId", "1")
                        .with(authentication(adminLogado)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.embeddingId").value(10))
                .andExpect(jsonPath("$.algoritmo").value("LBPH"));
    }
}
