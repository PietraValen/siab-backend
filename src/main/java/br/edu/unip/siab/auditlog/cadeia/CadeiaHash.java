package br.edu.unip.siab.auditlog.cadeia;

import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.util.HexFormat;

/**
 * Cadeia de hashes do log de auditoria (seção 2 do roteiro de segurança):
 * {@code hash(n) = SHA-256(hash(n-1) + "\n" + conteúdo(n))}. Editar ou
 * apagar qualquer linha muda todos os hashes seguintes, então a adulteração
 * fica detectável recalculando a corrente. Para impedir que alguém com
 * acesso ao banco simplesmente recalcule tudo, o último hash é selado de
 * tempos em tempos com a assinatura híbrida Ed25519 + ML-DSA-65 (ver
 * {@link CadeiaAuditoriaService}).
 */
public final class CadeiaHash {

    public static final String GENESE = "0".repeat(64);

    private CadeiaHash() {
    }

    public static String proximo(String hashAnterior, String conteudoCanonico) {
        try {
            MessageDigest sha = MessageDigest.getInstance("SHA-256");
            sha.update(hashAnterior.getBytes(StandardCharsets.US_ASCII));
            sha.update((byte) '\n');
            sha.update(conteudoCanonico.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(sha.digest());
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException(e);
        }
    }

    /**
     * Serialização canônica: campos separados por '|', nulo como "-",
     * valor presente como "=" + valor com '|' e '\' escapados — dois
     * registros diferentes nunca produzem o mesmo texto.
     */
    public static String juntar(Object... campos) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < campos.length; i++) {
            if (i > 0) {
                sb.append('|');
            }
            Object campo = campos[i];
            sb.append(campo == null ? "-" : "=" + campo.toString().replace("\\", "\\\\").replace("|", "\\|"));
        }
        return sb.toString();
    }
}
