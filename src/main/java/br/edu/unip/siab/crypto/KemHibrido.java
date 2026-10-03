package br.edu.unip.siab.crypto;

import javax.crypto.KDF;
import javax.crypto.KEM;
import javax.crypto.SecretKey;
import javax.crypto.spec.HKDFParameterSpec;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.PrivateKey;
import java.security.PublicKey;

/**
 * KEM híbrido <b>X25519 + ML-KEM-768</b> (seção 4.3 do roteiro de
 * segurança em {@code docs/}): encapsula um segredo nos dois algoritmos e
 * combina os dois segredos compartilhados com HKDF-SHA256. Quem quiser a
 * chave resultante precisa quebrar <b>os dois</b> — o X25519 (clássico,
 * maduro, mas vulnerável ao algoritmo de Shor) e o ML-KEM-768 (FIPS 203,
 * pós-quântico, mas ainda jovem).
 * <p>
 * Tudo com a JCA nativa do Java 25, sem BouncyCastle: {@code DHKEM} sobre
 * X25519 (JDK 21), {@code ML-KEM} (JEP 496, JDK 24) e a API {@code KDF}
 * com HKDF (JEP 510, final no JDK 25).
 * <p>
 * O combinador segue a ideia do X-Wing / draft-ietf-tls-hybrid-design:
 * os dois segredos entram como material de entrada do HKDF e os dois
 * ciphertexts entram no {@code info}, amarrando a chave derivada àquele
 * encapsulamento específico.
 */
public final class KemHibrido {

    private static final byte[] ROTULO = "SIAB-KEM-HIBRIDO-X25519-MLKEM768-v1".getBytes(StandardCharsets.US_ASCII);

    /** Resultado do encapsulamento: os dois ciphertexts (a guardar) e a chave AES-256 derivada (a usar e descartar). */
    public record Encapsulado(byte[] ctClassico, byte[] ctPqc, SecretKey chave) {
    }

    private KemHibrido() {
    }

    public static Encapsulado encapsular(PublicKey publicaX25519, PublicKey publicaMlKem) throws GeneralSecurityException {
        KEM.Encapsulated classico = KEM.getInstance("DHKEM").newEncapsulator(publicaX25519).encapsulate();
        KEM.Encapsulated pqc = KEM.getInstance("ML-KEM").newEncapsulator(publicaMlKem).encapsulate();
        SecretKey chave = combinar(classico.key(), pqc.key(), classico.encapsulation(), pqc.encapsulation());
        return new Encapsulado(classico.encapsulation(), pqc.encapsulation(), chave);
    }

    public static SecretKey decapsular(PrivateKey privadaX25519, PrivateKey privadaMlKem,
                                       byte[] ctClassico, byte[] ctPqc) throws GeneralSecurityException {
        SecretKey classico = KEM.getInstance("DHKEM").newDecapsulator(privadaX25519).decapsulate(ctClassico);
        SecretKey pqc = KEM.getInstance("ML-KEM").newDecapsulator(privadaMlKem).decapsulate(ctPqc);
        return combinar(classico, pqc, ctClassico, ctPqc);
    }

    private static SecretKey combinar(SecretKey segredoClassico, SecretKey segredoPqc,
                                      byte[] ctClassico, byte[] ctPqc) throws GeneralSecurityException {
        byte[] materialDeEntrada = concatenar(segredoClassico.getEncoded(), segredoPqc.getEncoded());
        byte[] info = concatenar(ROTULO, ctClassico, ctPqc);
        KDF hkdf = KDF.getInstance("HKDF-SHA256");
        return hkdf.deriveKey("AES", HKDFParameterSpec.ofExtract()
                .addIKM(materialDeEntrada)
                .thenExpand(info, 32));
    }

    static byte[] concatenar(byte[]... partes) {
        int tamanho = 0;
        for (byte[] parte : partes) {
            tamanho += parte.length;
        }
        ByteBuffer buffer = ByteBuffer.allocate(tamanho);
        for (byte[] parte : partes) {
            buffer.put(parte);
        }
        return buffer.array();
    }
}
