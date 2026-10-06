package br.edu.unip.siab.pipeline.feature;

import br.edu.unip.siab.auditlog.AuditoriaAdminService;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * Prazo de retenção das fotos do cadastro (ver Javadoc de RetencaoFotosService):
 * apaga só o que passou do prazo, audita a remoção e respeita o 0 = sem prazo.
 */
class RetencaoFotosServiceTest {

    private static final Clock RELOGIO = Clock.fixed(Instant.parse("2026-10-06T12:00:00Z"), ZoneOffset.UTC);

    private final FaceEmbeddingImagemRepository repository = mock(FaceEmbeddingImagemRepository.class);
    private final AuditoriaAdminService auditoria = mock(AuditoriaAdminService.class);

    @Test
    void apagaAsFotosMaisAntigasQueOPrazoEAudita() {
        when(repository.apagarCriadasAntesDe(LocalDateTime.of(2026, 4, 9, 12, 0))).thenReturn(3);

        int removidas = new RetencaoFotosService(repository, auditoria, 180, RELOGIO).expurgarVencidas();

        assertThat(removidas).isEqualTo(3);
        verify(auditoria).registrarComo(RetencaoFotosService.ADMINISTRADOR_SISTEMA,
                "FOTOS_BIOMETRICAS_EXPIRADAS", "removidas=3, prazo=180d");
    }

    @Test
    void naoAuditaQuandoNadaVenceu() {
        when(repository.apagarCriadasAntesDe(any())).thenReturn(0);

        new RetencaoFotosService(repository, auditoria, 180, RELOGIO).expurgarVencidas();

        verify(auditoria, never()).registrarComo(anyString(), anyString(), anyString());
    }

    @Test
    void prazoZeroGuardaAsFotosSemPrazo() {
        int removidas = new RetencaoFotosService(repository, auditoria, 0, RELOGIO).expurgarVencidas();

        assertThat(removidas).isZero();
        verifyNoInteractions(repository, auditoria);
    }

    @Test
    void recusaPrazoNegativo() {
        assertThatThrownBy(() -> new RetencaoFotosService(repository, auditoria, -1, RELOGIO))
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    void apagarTodasRegistraOAdminLogado() {
        when(repository.apagarTodas()).thenReturn(7);

        int removidas = new RetencaoFotosService(repository, auditoria, 180, RELOGIO).expurgarTodas();

        assertThat(removidas).isEqualTo(7);
        verify(auditoria).registrar("FOTOS_BIOMETRICAS_APAGADAS", "removidas=7");
    }
}
