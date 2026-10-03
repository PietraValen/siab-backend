package br.edu.unip.siab.pipeline.acquisition;

import br.edu.unip.siab.accesscontrol.PinService;
import br.edu.unip.siab.accesslevel.NivelAcesso;
import br.edu.unip.siab.auditlog.AccessLogService;
import br.edu.unip.siab.auth.JwtAuthFilter;
import br.edu.unip.siab.pipeline.PipelineOrchestratorService;
import br.edu.unip.siab.terminal.DesafioService;
import br.edu.unip.siab.terminal.Terminal;
import br.edu.unip.siab.terminal.TerminalAutenticacaoService;
import br.edu.unip.siab.terminal.TerminalNaoAutorizadoException;
import br.edu.unip.siab.user.Usuario;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.Optional;

import static org.hamcrest.Matchers.nullValue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Contrato HTTP do /scan depois do endurecimento (seções 1.1 e 1.2 do
 * roteiro de segurança):
 * <ul>
 *   <li>tentativa sem assinatura válida de terminal é recusada com 401 e
 *       nem chega ao pipeline;</li>
 *   <li>a resposta não traz similaridade, id do usuário nem o motivo
 *       detalhado — e {@code usuario} é null sempre que o acesso é negado
 *       (regressão do antigo bug do Map.of com valor nulo).</li>
 * </ul>
 */
@WebMvcTest(RecognitionController.class)
@AutoConfigureMockMvc(addFilters = false)
class RecognitionControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private PipelineOrchestratorService pipelineOrchestratorService;

    @MockitoBean
    private TerminalAutenticacaoService terminalAutenticacaoService;

    @MockitoBean
    private DesafioService desafioService;

    @MockitoBean
    private AccessLogService accessLogService;

    @MockitoBean
    private PinService pinService;

    @MockitoBean
    private JwtAuthFilter jwtAuthFilter;

    private final MockMultipartFile frame =
            new MockMultipartFile("imagens", "f0.jpg", "image/jpeg", new byte[]{1, 2, 3});

    private Terminal terminal() {
        NivelAcesso nivel = new NivelAcesso();
        nivel.setId(1L);
        nivel.setNome("Acesso Geral");
        Terminal terminal = new Terminal();
        terminal.setId(7L);
        terminal.setNome("Porta 1");
        terminal.setNivelExigido(nivel);
        return terminal;
    }

    @Test
    void negadoNaoExpoeUsuarioNemSimilaridade() throws Exception {
        when(terminalAutenticacaoService.autenticar(any(), anyList(), any())).thenReturn(terminal());
        Usuario reconhecido = new Usuario();
        reconhecido.setNome("Fulano");
        when(pipelineOrchestratorService.reconhecer(any())).thenReturn(new PipelineOrchestratorService.ResultadoScan(
                false, Optional.of(reconhecido), 0.12, "Nível de acesso insuficiente (exigido 3).", "Acesso negado."));

        mockMvc.perform(multipart("/api/recognition/scan").file(frame)
                        .header("X-Terminal-Id", "7").header("X-Desafio", "n").header("X-Timestamp", "1")
                        .header("X-Assinatura", "x"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.acessoConcedido").value(false))
                .andExpect(jsonPath("$.usuario").value(nullValue()))
                .andExpect(jsonPath("$.mensagem").value("Acesso negado."))
                .andExpect(jsonPath("$.similaridade").doesNotExist())
                .andExpect(jsonPath("$.motivo").doesNotExist());
    }

    @Test
    void semAssinaturaValidaRetorna401ENaoRodaOPipeline() throws Exception {
        when(terminalAutenticacaoService.autenticar(any(), anyList(), any()))
                .thenThrow(new TerminalNaoAutorizadoException("Assinatura HMAC inválida."));

        mockMvc.perform(multipart("/api/recognition/scan").file(frame))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.mensagem").value("Terminal não autorizado."));

        verify(pipelineOrchestratorService, never()).reconhecer(any());
        verify(accessLogService).registrar(any());
    }

    @Test
    void concedidoMostraSoNomeENivel() throws Exception {
        when(terminalAutenticacaoService.autenticar(any(), anyList(), any())).thenReturn(terminal());
        NivelAcesso nivel = new NivelAcesso();
        nivel.setNome("Diretoria");
        Usuario usuario = new Usuario();
        usuario.setId(42L);
        usuario.setNome("Beatriz");
        usuario.setNivelAcesso(nivel);
        when(pipelineOrchestratorService.reconhecer(any())).thenReturn(new PipelineOrchestratorService.ResultadoScan(
                true, Optional.of(usuario), 0.2, "Acesso concedido.", "Acesso concedido."));

        mockMvc.perform(multipart("/api/recognition/scan").file(frame))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.acessoConcedido").value(true))
                .andExpect(jsonPath("$.usuario.nome").value("Beatriz"))
                .andExpect(jsonPath("$.usuario.nivelAcesso").value("Diretoria"))
                .andExpect(jsonPath("$.usuario.id").doesNotExist());
    }

    @Test
    void muitosFramesRetorna400() throws Exception {
        var requisicao = multipart("/api/recognition/scan");
        for (int i = 0; i < 13; i++) {
            requisicao.file(new MockMultipartFile("imagens", "f" + i + ".jpg", "image/jpeg", new byte[]{1}));
        }
        mockMvc.perform(requisicao).andExpect(status().isBadRequest());
    }
}
