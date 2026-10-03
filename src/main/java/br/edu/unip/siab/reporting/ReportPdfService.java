package br.edu.unip.siab.reporting;

import br.edu.unip.siab.auditlog.AccessLog;
import org.openpdf.text.Document;
import org.openpdf.text.DocumentException;
import org.openpdf.text.Font;
import org.openpdf.text.PageSize;
import org.openpdf.text.Paragraph;
import org.openpdf.text.Phrase;
import org.openpdf.text.pdf.PdfPCell;
import org.openpdf.text.pdf.PdfPTable;
import org.openpdf.text.pdf.PdfWriter;
import org.springframework.stereotype.Service;

import java.awt.*;
import java.io.ByteArrayOutputStream;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;

/**
 * Módulo "reporting" (seção 5.2 e 5.7 do escopo, funcionalidade extra) —
 * gera o PDF de auditoria consumido pela tela /admin/reports.
 * <p>
 * Biblioteca escolhida: <b>OpenPDF</b> — fork mantido do iText 4, com
 * licença LGPL/MPL (uso livre em projeto acadêmico, sem custo), diferente
 * do iText 5+/7 que exige licença comercial fora do regime AGPL. Preferido
 * em vez do Apache PDFBox porque sua API de alto nível baseada em
 * documento (Document/Paragraph/PdfPTable) monta um relatório tabular como
 * este com muito menos código do que o desenho de página baixo-nível do
 * PDFBox.
 */
@Service
public class ReportPdfService {

    private static final DateTimeFormatter FORMATO_DATA = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm:ss");
    private static final Color COR_CABECALHO_TABELA = new Color(45, 55, 72);

    /**
     * Selo da cadeia de auditoria no momento da exportação (ver
     * CadeiaAuditoriaService): impresso no rodapé para que o relatório possa
     * ser conferido depois contra GET /api/admin/auditoria/verificacao.
     */
    public record SeloDoRelatorio(Long checkpointId, String ultimoHash, String impressaoDigital, String assinaturaEd25519) {
    }

    public byte[] gerarRelatorioDeAcessos(List<AccessLog> logs) {
        return gerarRelatorioDeAcessos(logs, null);
    }

    public byte[] gerarRelatorioDeAcessos(List<AccessLog> logs, SeloDoRelatorio selo) {
        Document documento = new Document(PageSize.A4, 36, 36, 54, 36);
        ByteArrayOutputStream saida = new ByteArrayOutputStream();

        try {
            PdfWriter.getInstance(documento, saida);
            documento.open();

            adicionarCabecalho(documento, logs.size());
            documento.add(construirTabela(logs));
            if (selo != null) {
                adicionarSelo(documento, selo);
            }

            documento.close();
        } catch (DocumentException e) {
            throw new IllegalStateException("Falha ao gerar relatório PDF de acessos.", e);
        }

        return saida.toByteArray();
    }

    private void adicionarCabecalho(Document documento, int totalRegistros) throws DocumentException {
        Font fonteTitulo = new Font(Font.HELVETICA, 16, Font.BOLD);
        Paragraph titulo = new Paragraph("SIAB - Relatório de Auditoria de Acessos", fonteTitulo);
        documento.add(titulo);

        Font fonteSubtitulo = new Font(Font.HELVETICA, 9, Font.ITALIC, Color.DARK_GRAY);
        Paragraph subtitulo = new Paragraph(
                "Gerado em " + FORMATO_DATA.format(LocalDateTime.now()) + " — " + totalRegistros + " registro(s)",
                fonteSubtitulo);
        subtitulo.setSpacingAfter(16f);
        documento.add(subtitulo);
    }

    private void adicionarSelo(Document documento, SeloDoRelatorio selo) throws DocumentException {
        Font fonte = new Font(Font.COURIER, 7, Font.NORMAL, Color.DARK_GRAY);
        Paragraph paragrafo = new Paragraph(
                "Integridade: cadeia de hashes SHA-256 selada no checkpoint #" + selo.checkpointId()
                        + " com assinatura híbrida Ed25519 + ML-DSA-65 (FIPS 204).\n"
                        + "Último hash: " + selo.ultimoHash() + "\n"
                        + "Chaves: " + selo.impressaoDigital() + "   Ed25519: " + selo.assinaturaEd25519(),
                fonte);
        paragrafo.setSpacingBefore(14f);
        documento.add(paragrafo);
    }

    private PdfPTable construirTabela(List<AccessLog> logs) throws DocumentException {
        PdfPTable tabela = new PdfPTable(new float[]{1.3f, 2.2f, 1.1f, 1.1f, 1.6f});
        tabela.setWidthPercentage(100);

        for (String coluna : new String[]{"Data/Hora", "Usuário", "Resultado", "Similaridade", "Nível de acesso"}) {
            tabela.addCell(celulaCabecalho(coluna));
        }

        Font fonteLinha = new Font(Font.HELVETICA, 9);
        for (AccessLog log : logs) {
            tabela.addCell(celulaLinha(FORMATO_DATA.format(log.getDataHora()), fonteLinha));
            tabela.addCell(celulaLinha(nomeDoUsuario(log), fonteLinha));
            tabela.addCell(celulaLinha(log.getResultado().name(), fonteLinha));
            tabela.addCell(celulaLinha(formatarSimilaridade(log), fonteLinha));
            tabela.addCell(celulaLinha(nivelDeAcessoDoUsuario(log), fonteLinha));
        }

        return tabela;
    }

    private String nomeDoUsuario(AccessLog log) {
        return log.getUsuario() != null ? log.getUsuario().getNome() : "Não identificado";
    }

    private String nivelDeAcessoDoUsuario(AccessLog log) {
        return log.getUsuario() != null ? log.getUsuario().getNivelAcesso().getNome() : "-";
    }

    private String formatarSimilaridade(AccessLog log) {
        return log.getSimilaridade() != null ? String.format("%.4f", log.getSimilaridade()) : "-";
    }

    private PdfPCell celulaCabecalho(String texto) {
        Font fonte = new Font(Font.HELVETICA, 9, Font.BOLD, Color.WHITE);
        PdfPCell celula = new PdfPCell(new Phrase(texto, fonte));
        celula.setBackgroundColor(COR_CABECALHO_TABELA);
        celula.setPadding(6f);
        return celula;
    }

    private PdfPCell celulaLinha(String texto, Font fonte) {
        PdfPCell celula = new PdfPCell(new Phrase(texto, fonte));
        celula.setPadding(5f);
        return celula;
    }
}
