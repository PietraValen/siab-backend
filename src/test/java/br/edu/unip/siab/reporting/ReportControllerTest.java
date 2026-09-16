package br.edu.unip.siab.reporting;

import br.edu.unip.siab.auditlog.AccessLog;
import br.edu.unip.siab.auditlog.AccessLogService;
import br.edu.unip.siab.auth.JwtAuthFilter;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * Valida o contrato HTTP do módulo "reporting": o resumo numérico
 * (dashboard) e a exportação em PDF (content-type e cabeçalho de
 * download). A biblioteca de PDF em si (ReportPdfService) já é testada
 * isoladamente em ReportPdfServiceTest.
 */
@WebMvcTest(ReportController.class)
@AutoConfigureMockMvc(addFilters = false)
class ReportControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private AccessLogService accessLogService;

    @MockitoBean
    private ReportPdfService reportPdfService;

    @MockitoBean
    private JwtAuthFilter jwtAuthFilter;

    private AccessLog logConcedido() {
        AccessLog log = new AccessLog();
        log.setResultado(AccessLog.Resultado.CONCEDIDO);
        return log;
    }

    private AccessLog logNegado() {
        AccessLog log = new AccessLog();
        log.setResultado(AccessLog.Resultado.NEGADO);
        return log;
    }

    @Test
    @WithMockUser
    void resumoDeAcessosContaConcedidosENegadosCorretamente() throws Exception {
        when(accessLogService.listarTodos()).thenReturn(List.of(logConcedido(), logConcedido(), logNegado()));

        mockMvc.perform(get("/api/admin/reports/access-summary"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalTentativas").value(3))
                .andExpect(jsonPath("$.acessosConcedidos").value(2))
                .andExpect(jsonPath("$.acessosNegados").value(1));
    }

    @Test
    @WithMockUser
    void exportarRelatorioPdfRetornaPdfParaDownload() throws Exception {
        byte[] pdfFalso = "%PDF-conteudo-fake".getBytes();
        when(accessLogService.listarComFiltros(isNull(), isNull(), isNull())).thenReturn(List.of());
        when(reportPdfService.gerarRelatorioDeAcessos(any())).thenReturn(pdfFalso);

        mockMvc.perform(get("/api/admin/reports/access-log.pdf"))
                .andExpect(status().isOk())
                .andExpect(content().contentType("application/pdf"))
                .andExpect(header().string("Content-Disposition", org.hamcrest.Matchers.containsString("relatorio-acessos.pdf")))
                .andExpect(content().bytes(pdfFalso));
    }

    @Test
    @WithMockUser
    void exportarRelatorioPdfComFiltroDeUsuario() throws Exception {
        when(accessLogService.listarComFiltros(org.mockito.ArgumentMatchers.eq(5L), isNull(), isNull()))
                .thenReturn(List.of());
        when(reportPdfService.gerarRelatorioDeAcessos(any())).thenReturn("%PDF".getBytes());

        mockMvc.perform(get("/api/admin/reports/access-log.pdf").param("usuarioId", "5"))
                .andExpect(status().isOk());
    }
}
