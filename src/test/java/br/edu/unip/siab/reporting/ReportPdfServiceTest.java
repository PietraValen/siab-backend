package br.edu.unip.siab.reporting;

import br.edu.unip.siab.accesslevel.NivelAcesso;
import br.edu.unip.siab.auditlog.AccessLog;
import br.edu.unip.siab.user.Usuario;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Cobre a geração do PDF de auditoria (ver Javadoc de ReportPdfService) —
 * não valida o conteúdo textual do PDF (isso exigiria um parser de PDF),
 * só que a saída é um PDF bem-formado e não vazio, com e sem registros.
 */
class ReportPdfServiceTest {

    private final ReportPdfService service = new ReportPdfService();

    @Test
    void geraPdfValidoComRegistros() {
        Usuario usuario = new Usuario();
        usuario.setNome("Beatriz Novais");
        NivelAcesso nivel = new NivelAcesso();
        nivel.setNome("Acesso Geral");
        usuario.setNivelAcesso(nivel);

        AccessLog log = new AccessLog();
        log.setUsuario(usuario);
        log.setResultado(AccessLog.Resultado.CONCEDIDO);
        log.setSimilaridade(0.12);
        log.setDataHora(LocalDateTime.now());

        byte[] pdf = service.gerarRelatorioDeAcessos(List.of(log));

        assertThat(pdf).isNotEmpty();
        assertThat(new String(pdf, 0, 4, java.nio.charset.StandardCharsets.US_ASCII)).isEqualTo("%PDF");
    }

    @Test
    void geraPdfValidoSemRegistros() {
        byte[] pdf = service.gerarRelatorioDeAcessos(List.of());

        assertThat(pdf).isNotEmpty();
        assertThat(new String(pdf, 0, 4, java.nio.charset.StandardCharsets.US_ASCII)).isEqualTo("%PDF");
    }

    @Test
    void geraPdfComTentativaNaoIdentificada() {
        AccessLog log = new AccessLog();
        log.setUsuario(null);
        log.setResultado(AccessLog.Resultado.NEGADO);
        log.setSimilaridade(null);
        log.setDataHora(LocalDateTime.now());

        byte[] pdf = service.gerarRelatorioDeAcessos(List.of(log));

        assertThat(pdf).isNotEmpty();
    }
}
