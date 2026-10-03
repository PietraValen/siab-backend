package br.edu.unip.siab.terminal;

import br.edu.unip.siab.accesslevel.NivelAcesso;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Base64;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

/**
 * Identidade do terminal + anti-replay do /scan (seção 1.1 do roteiro de
 * segurança): só passa a tentativa assinada com a chave do terminal, sobre
 * um desafio deste terminal, ainda não usado e dentro do prazo.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class TerminalAutenticacaoServiceTest {

    private static final Instant AGORA = Instant.parse("2026-10-03T12:00:00Z");
    private static final String CHAVE = Base64.getEncoder().encodeToString(new byte[32]);

    @Mock
    private TerminalRepository terminalRepository;

    private final Clock relogio = Clock.fixed(AGORA, ZoneOffset.UTC);
    private final DesafioService desafios = new DesafioService(relogio);
    private TerminalAutenticacaoService service;
    private Terminal terminal;

    private final List<byte[]> frames = List.of(new byte[]{1, 2, 3}, new byte[]{4, 5});

    @BeforeEach
    void setUp() {
        service = new TerminalAutenticacaoService(terminalRepository, desafios, relogio);
        NivelAcesso nivel = new NivelAcesso();
        nivel.setId(1L);
        terminal = new Terminal();
        terminal.setId(7L);
        terminal.setNome("Porta 1");
        terminal.setNivelExigido(nivel);
        terminal.setChave(CHAVE);
        when(terminalRepository.findById(7L)).thenReturn(Optional.of(terminal));
        when(terminalRepository.save(any())).thenAnswer(i -> i.getArgument(0));
    }

    private TerminalAutenticacaoService.Credenciais assinar(String nonce, long timestamp, String chave, String pin) {
        String mensagem = TerminalAutenticacaoService.mensagemCanonica(7L, nonce, String.valueOf(timestamp), frames, pin);
        String assinatura = Base64.getEncoder().encodeToString(TerminalAutenticacaoService.hmac(chave, mensagem));
        return new TerminalAutenticacaoService.Credenciais("7", nonce, String.valueOf(timestamp), assinatura);
    }

    @Test
    void tentativaAssinadaCorretamenteEhAceita() {
        String nonce = desafios.emitir(7L).nonce();

        assertThat(service.autenticar(assinar(nonce, AGORA.toEpochMilli(), CHAVE, null), frames, null)).isSameAs(terminal);
        assertThat(terminal.getUltimoUsoEm()).isNotNull();
    }

    @Test
    void reenviarAMesmaRequisicaoEhRecusado() {
        String nonce = desafios.emitir(7L).nonce();
        var credenciais = assinar(nonce, AGORA.toEpochMilli(), CHAVE, null);
        service.autenticar(credenciais, frames, null);

        assertThatThrownBy(() -> service.autenticar(credenciais, frames, null))
                .isInstanceOf(TerminalNaoAutorizadoException.class);
    }

    @Test
    void trocarOsFramesDepoisDeAssinarEhRecusado() {
        String nonce = desafios.emitir(7L).nonce();
        var credenciais = assinar(nonce, AGORA.toEpochMilli(), CHAVE, null);

        assertThatThrownBy(() -> service.autenticar(credenciais, List.of(new byte[]{9}), null))
                .isInstanceOf(TerminalNaoAutorizadoException.class);
    }

    @Test
    void trocarOPinDepoisDeAssinarEhRecusado() {
        String nonce = desafios.emitir(7L).nonce();
        var credenciais = assinar(nonce, AGORA.toEpochMilli(), CHAVE, "1234");

        assertThatThrownBy(() -> service.autenticar(credenciais, frames, "9999"))
                .isInstanceOf(TerminalNaoAutorizadoException.class);
    }

    @Test
    void chaveErradaEhRecusada() {
        String nonce = desafios.emitir(7L).nonce();
        String outraChave = Base64.getEncoder().encodeToString(new byte[]{1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11, 12, 13, 14, 15, 16,
                17, 18, 19, 20, 21, 22, 23, 24, 25, 26, 27, 28, 29, 30, 31, 32});

        assertThatThrownBy(() -> service.autenticar(assinar(nonce, AGORA.toEpochMilli(), outraChave, null), frames, null))
                .isInstanceOf(TerminalNaoAutorizadoException.class);
    }

    @Test
    void desafioInventadoOuDeOutroTerminalEhRecusado() {
        assertThatThrownBy(() -> service.autenticar(assinar("inventado", AGORA.toEpochMilli(), CHAVE, null), frames, null))
                .isInstanceOf(TerminalNaoAutorizadoException.class);

        String deOutro = desafios.emitir(8L).nonce();
        assertThatThrownBy(() -> service.autenticar(assinar(deOutro, AGORA.toEpochMilli(), CHAVE, null), frames, null))
                .isInstanceOf(TerminalNaoAutorizadoException.class);
    }

    @Test
    void relogioForaDaJanelaEhRecusado() {
        String nonce = desafios.emitir(7L).nonce();
        long cincoMinutosAtras = AGORA.minusSeconds(300).toEpochMilli();

        assertThatThrownBy(() -> service.autenticar(assinar(nonce, cincoMinutosAtras, CHAVE, null), frames, null))
                .isInstanceOf(TerminalNaoAutorizadoException.class);
    }

    @Test
    void terminalRevogadoEhRecusado() {
        terminal.setAtivo(false);

        assertThatThrownBy(() -> service.buscarAtivo("7")).isInstanceOf(TerminalNaoAutorizadoException.class);
        assertThatThrownBy(() -> service.buscarAtivo("abc")).isInstanceOf(TerminalNaoAutorizadoException.class);
    }
}
