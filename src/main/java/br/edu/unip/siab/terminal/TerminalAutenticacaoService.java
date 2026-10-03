package br.edu.unip.siab.terminal;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.time.Clock;
import java.time.LocalDateTime;
import java.util.Base64;
import java.util.HexFormat;
import java.util.List;

/**
 * Verifica que uma tentativa de reconhecimento veio de um terminal
 * cadastrado e não é um replay (seção 1.1 do roteiro de segurança).
 * <p>
 * O terminal assina com <b>HMAC-SHA256</b> e o segredo que recebeu do admin
 * a mensagem canônica:
 * <pre>
 * SIAB-SCAN-v1
 * &lt;terminalId&gt;
 * &lt;nonce do desafio&gt;
 * &lt;timestamp em ms desde a época&gt;
 * &lt;SHA-256 em hex de cada frame enviado, separados por vírgula, na ordem do envio&gt;
 * &lt;PIN digitado, ou vazio&gt;
 * </pre>
 * Por que HMAC e não a assinatura híbrida Ed25519 + ML-DSA sugerida no
 * roteiro: o terminal é um navegador, e a WebCrypto não tem ML-DSA. O HMAC
 * é simétrico — com chave de 256 bits ele não é afetado pelo algoritmo de
 * Shor, então já é resistente a computador quântico, sem biblioteca extra
 * no front. A assinatura híbrida ficou para o log de auditoria, onde quem
 * assina é o próprio back-end.
 */
@Service
public class TerminalAutenticacaoService {

    static final String VERSAO_MENSAGEM = "SIAB-SCAN-v1";

    private final TerminalRepository terminalRepository;
    private final DesafioService desafioService;
    private final Clock relogio;

    @Value("${siab.terminal.tolerancia-relogio-segundos:60}")
    private long toleranciaRelogioSegundos = 60;

    @Autowired
    public TerminalAutenticacaoService(TerminalRepository terminalRepository, DesafioService desafioService) {
        this(terminalRepository, desafioService, Clock.systemUTC());
    }

    TerminalAutenticacaoService(TerminalRepository terminalRepository, DesafioService desafioService, Clock relogio) {
        this.terminalRepository = terminalRepository;
        this.desafioService = desafioService;
        this.relogio = relogio;
    }

    /** Credenciais enviadas nos headers da requisição de scan. */
    public record Credenciais(String terminalId, String nonce, String timestamp, String assinatura) {
    }

    public Terminal buscarAtivo(String terminalId) {
        Long id = paraId(terminalId);
        return terminalRepository.findById(id)
                .filter(Terminal::isAtivo)
                .orElseThrow(() -> new TerminalNaoAutorizadoException("Terminal desconhecido ou revogado: " + terminalId));
    }

    @Transactional
    public Terminal autenticar(Credenciais credenciais, List<byte[]> frames, String pin) {
        Terminal terminal = buscarAtivo(credenciais.terminalId());

        if (!desafioService.consumir(credenciais.nonce(), terminal.getId())) {
            throw new TerminalNaoAutorizadoException("Desafio ausente, vencido, já usado ou de outro terminal.");
        }

        long timestamp;
        try {
            timestamp = Long.parseLong(credenciais.timestamp());
        } catch (NumberFormatException | NullPointerException e) {
            throw new TerminalNaoAutorizadoException("Timestamp inválido.");
        }
        if (Math.abs(relogio.millis() - timestamp) > toleranciaRelogioSegundos * 1000) {
            throw new TerminalNaoAutorizadoException("Relógio do terminal fora da janela de tolerância.");
        }

        String mensagem = mensagemCanonica(terminal.getId(), credenciais.nonce(), credenciais.timestamp(), frames, pin);
        byte[] esperada = hmac(terminal.getChave(), mensagem);
        byte[] recebida;
        try {
            recebida = Base64.getDecoder().decode(credenciais.assinatura() == null ? "" : credenciais.assinatura());
        } catch (IllegalArgumentException e) {
            throw new TerminalNaoAutorizadoException("Assinatura não é Base64.");
        }
        if (!MessageDigest.isEqual(esperada, recebida)) { // comparação em tempo constante
            throw new TerminalNaoAutorizadoException("Assinatura HMAC inválida.");
        }

        terminal.setUltimoUsoEm(LocalDateTime.now());
        return terminalRepository.save(terminal);
    }

    static String mensagemCanonica(Long terminalId, String nonce, String timestamp, List<byte[]> frames, String pin) {
        StringBuilder hashes = new StringBuilder();
        for (byte[] frame : frames) {
            if (!hashes.isEmpty()) {
                hashes.append(',');
            }
            hashes.append(sha256Hex(frame));
        }
        return String.join("\n", VERSAO_MENSAGEM, String.valueOf(terminalId), nonce, timestamp,
                hashes.toString(), pin == null ? "" : pin);
    }

    static byte[] hmac(String chaveBase64, String mensagem) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(Base64.getDecoder().decode(chaveBase64), "HmacSHA256"));
            return mac.doFinal(mensagem.getBytes(StandardCharsets.UTF_8));
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException(e);
        }
    }

    private static String sha256Hex(byte[] dados) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(dados));
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException(e);
        }
    }

    private static Long paraId(String terminalId) {
        try {
            return Long.valueOf(terminalId);
        } catch (NumberFormatException | NullPointerException e) {
            throw new TerminalNaoAutorizadoException("Header X-Terminal-Id ausente ou inválido.");
        }
    }
}
