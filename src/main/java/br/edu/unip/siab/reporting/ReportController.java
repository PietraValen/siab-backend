package br.edu.unip.siab.reporting;

import br.edu.unip.siab.auditlog.AccessLog;
import br.edu.unip.siab.auditlog.AccessLogService;
import br.edu.unip.siab.reporting.dto.AccessSummaryResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.List;

import static br.edu.unip.siab.config.OpenApiConfig.ESQUEMA_JWT;

/**
 * Módulo "reporting" (seção 5.2 e 5.7 do escopo, funcionalidade extra).
 * Consumido pela tela /admin/reports do front-end: resumo numérico para o
 * dashboard e exportação do relatório completo em PDF (ver
 * {@link ReportPdfService}), com filtros opcionais por usuário e/ou
 * período.
 */
@RestController
@RequestMapping("/api/admin/reports")
@RequiredArgsConstructor
@Tag(name = "Relatórios", description = "Resumo e exportação em PDF do histórico de auditoria")
@SecurityRequirement(name = ESQUEMA_JWT)
public class ReportController {

    private final AccessLogService accessLogService;
    private final ReportPdfService reportPdfService;

    @Operation(summary = "Resumo numérico das tentativas de acesso", description = "Totais de tentativas concedidas/negadas, para o dashboard do painel administrativo.")
    @GetMapping("/access-summary")
    public AccessSummaryResponse resumoDeAcessos() {
        List<AccessLog> logs = accessLogService.listarTodos();

        long concedidos = logs.stream().filter(l -> l.getResultado() == AccessLog.Resultado.CONCEDIDO).count();
        long negados = logs.stream().filter(l -> l.getResultado() == AccessLog.Resultado.NEGADO).count();

        return new AccessSummaryResponse(logs.size(), concedidos, negados);
    }

    @Operation(summary = "Exporta o relatório de auditoria em PDF", description = "Filtros opcionais e combináveis por usuário e/ou período (data de início/fim, formato ISO yyyy-MM-dd). Sem filtros, exporta o histórico completo.")
    @ApiResponse(responseCode = "200", description = "PDF gerado", content = @Content(mediaType = MediaType.APPLICATION_PDF_VALUE))
    @GetMapping(value = "/access-log.pdf", produces = MediaType.APPLICATION_PDF_VALUE)
    public ResponseEntity<byte[]> exportarRelatorioPdf(
            @Parameter(description = "Filtra por um usuário específico") @RequestParam(required = false) Long usuarioId,
            @Parameter(description = "Data inicial (inclusive), formato yyyy-MM-dd") @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate dataInicio,
            @Parameter(description = "Data final (inclusive), formato yyyy-MM-dd") @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate dataFim) {

        LocalDateTime inicio = dataInicio != null ? dataInicio.atStartOfDay() : null;
        LocalDateTime fim = dataFim != null ? dataFim.atTime(LocalTime.MAX) : null;

        List<AccessLog> logs = accessLogService.listarComFiltros(usuarioId, inicio, fim);
        byte[] pdf = reportPdfService.gerarRelatorioDeAcessos(logs);

        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=relatorio-acessos.pdf")
                .contentType(MediaType.APPLICATION_PDF)
                .body(pdf);
    }
}
