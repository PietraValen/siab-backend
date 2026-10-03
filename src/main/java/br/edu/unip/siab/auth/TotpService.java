package br.edu.unip.siab.auth;

import org.springframework.stereotype.Service;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.net.URLEncoder;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.SecureRandom;
import java.time.Clock;
import java.util.OptionalLong;

/**
 * Segundo fator dos administradores (seção 1.5 do roteiro de segurança):
 * TOTP da RFC 6238 — o mesmo código de 6 dígitos que muda a cada 30 s do
 * Google Authenticator, Microsoft Authenticator, Aegis, 1Password etc.
 * Implementado direto sobre {@code HmacSHA1} da JCA (o algoritmo que esses
 * aplicativos esperam por padrão), sem biblioteca extra.
 * <p>
 * Aceita o passo atual e um de cada lado (±30 s de diferença de relógio) e
 * devolve o passo usado, para o chamador recusar o mesmo código duas vezes.
 */
@Service
public class TotpService {

    private static final int DIGITOS = 6;
    private static final long PASSO_SEGUNDOS = 30;
    private static final String ALFABETO_BASE32 = "ABCDEFGHIJKLMNOPQRSTUVWXYZ234567";

    private final SecureRandom aleatorio = new SecureRandom();
    private final Clock relogio;

    public TotpService() {
        this(Clock.systemUTC());
    }

    TotpService(Clock relogio) {
        this.relogio = relogio;
    }

    /** Segredo novo de 160 bits, em Base32 (formato que os apps de autenticação leem). */
    public String gerarSegredo() {
        byte[] segredo = new byte[20];
        aleatorio.nextBytes(segredo);
        return base32(segredo);
    }

    /** URI {@code otpauth://} — vira QR code no front ou pode ser digitada no app. */
    public String uriDeConfiguracao(String segredo, String username) {
        String rotulo = URLEncoder.encode("SIAB:" + username, StandardCharsets.UTF_8).replace("+", "%20");
        return "otpauth://totp/" + rotulo + "?secret=" + segredo + "&issuer=SIAB&algorithm=SHA1&digits=6&period=30";
    }

    /**
     * @return o passo de tempo em que o código bateu, ou vazio se não bateu
     * em nenhum dos três passos aceitos.
     */
    public OptionalLong verificar(String segredo, String codigo) {
        if (segredo == null || codigo == null || !codigo.matches("\\d{" + DIGITOS + "}")) {
            return OptionalLong.empty();
        }
        byte[] chave = deBase32(segredo);
        long passoAtual = relogio.instant().getEpochSecond() / PASSO_SEGUNDOS;
        for (long passo = passoAtual - 1; passo <= passoAtual + 1; passo++) {
            if (codigoNoPasso(chave, passo).equals(codigo)) {
                return OptionalLong.of(passo);
            }
        }
        return OptionalLong.empty();
    }

    String codigoNoPasso(byte[] chave, long passo) {
        try {
            Mac mac = Mac.getInstance("HmacSHA1");
            mac.init(new SecretKeySpec(chave, "HmacSHA1"));
            byte[] hash = mac.doFinal(ByteBuffer.allocate(8).putLong(passo).array());
            int deslocamento = hash[hash.length - 1] & 0x0F; // "dynamic truncation" da RFC 4226
            int binario = ((hash[deslocamento] & 0x7F) << 24)
                    | ((hash[deslocamento + 1] & 0xFF) << 16)
                    | ((hash[deslocamento + 2] & 0xFF) << 8)
                    | (hash[deslocamento + 3] & 0xFF);
            return String.format("%0" + DIGITOS + "d", binario % 1_000_000);
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException(e);
        }
    }

    static String base32(byte[] dados) {
        StringBuilder sb = new StringBuilder();
        int buffer = 0;
        int bits = 0;
        for (byte b : dados) {
            buffer = (buffer << 8) | (b & 0xFF);
            bits += 8;
            while (bits >= 5) {
                sb.append(ALFABETO_BASE32.charAt((buffer >> (bits - 5)) & 0x1F));
                bits -= 5;
            }
        }
        if (bits > 0) {
            sb.append(ALFABETO_BASE32.charAt((buffer << (5 - bits)) & 0x1F));
        }
        return sb.toString();
    }

    static byte[] deBase32(String texto) {
        String limpo = texto.replace("=", "").replace(" ", "").toUpperCase();
        ByteBuffer saida = ByteBuffer.allocate(limpo.length() * 5 / 8);
        int buffer = 0;
        int bits = 0;
        for (char c : limpo.toCharArray()) {
            int valor = ALFABETO_BASE32.indexOf(c);
            if (valor < 0) {
                throw new IllegalArgumentException("Caractere Base32 inválido: " + c);
            }
            buffer = (buffer << 5) | valor;
            bits += 5;
            if (bits >= 8) {
                saida.put((byte) (buffer >> (bits - 8)));
                bits -= 8;
            }
        }
        return saida.array();
    }
}
