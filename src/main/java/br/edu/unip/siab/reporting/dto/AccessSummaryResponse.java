package br.edu.unip.siab.reporting.dto;

public record AccessSummaryResponse(
        long totalTentativas,
        long acessosConcedidos,
        long acessosNegados
) {
}
