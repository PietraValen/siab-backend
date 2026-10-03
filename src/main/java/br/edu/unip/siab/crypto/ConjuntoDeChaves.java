package br.edu.unip.siab.crypto;

import java.security.GeneralSecurityException;
import java.security.KeyFactory;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.spec.PKCS8EncodedKeySpec;
import java.security.spec.X509EncodedKeySpec;
import java.util.Base64;

/**
 * Par de pares de chaves (um clássico + um pós-quântico) serializado numa
 * única string, para caber numa variável de ambiente:
 * <pre>v1.&lt;pública clássica&gt;.&lt;privada clássica&gt;.&lt;pública PQC&gt;.&lt;privada PQC&gt;</pre>
 * Cada parte é Base64 URL-safe da codificação padrão da JCA (X.509 para as
 * públicas, PKCS#8 para as privadas). Gerado por
 * {@code scripts/GerarChaves.java}.
 */
public record ConjuntoDeChaves(KeyPair classico, KeyPair pqc) {

    private static final String VERSAO = "v1";

    /** Algoritmos de um conjunto: nome da KeyFactory e do gerador de cada metade. */
    public record Algoritmos(String fabricaClassico, String geradorClassico, String fabricaPqc, String geradorPqc) {
    }

    /** KEM híbrido usado para cifrar dados biométricos (seção 4.3). */
    public static final Algoritmos CIFRA = new Algoritmos("X25519", "X25519", "ML-KEM", "ML-KEM-768");

    /** Assinatura híbrida usada no log de auditoria (seção 4.4). */
    public static final Algoritmos ASSINATURA = new Algoritmos("Ed25519", "Ed25519", "ML-DSA", "ML-DSA-65");

    public static ConjuntoDeChaves gerar(Algoritmos algoritmos) {
        try {
            return new ConjuntoDeChaves(
                    KeyPairGenerator.getInstance(algoritmos.geradorClassico()).generateKeyPair(),
                    KeyPairGenerator.getInstance(algoritmos.geradorPqc()).generateKeyPair());
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException("JDK sem suporte a " + algoritmos, e);
        }
    }

    public static ConjuntoDeChaves ler(String serializado, Algoritmos algoritmos) {
        String[] partes = serializado.trim().split("\\.");
        if (partes.length != 5 || !VERSAO.equals(partes[0])) {
            throw new IllegalArgumentException("Formato de chave inválido: esperado v1.<pub>.<priv>.<pub>.<priv>");
        }
        try {
            Base64.Decoder b64 = Base64.getUrlDecoder();
            KeyFactory fabricaClassico = KeyFactory.getInstance(algoritmos.fabricaClassico());
            KeyFactory fabricaPqc = KeyFactory.getInstance(algoritmos.fabricaPqc());
            return new ConjuntoDeChaves(
                    new KeyPair(fabricaClassico.generatePublic(new X509EncodedKeySpec(b64.decode(partes[1]))),
                            fabricaClassico.generatePrivate(new PKCS8EncodedKeySpec(b64.decode(partes[2])))),
                    new KeyPair(fabricaPqc.generatePublic(new X509EncodedKeySpec(b64.decode(partes[3]))),
                            fabricaPqc.generatePrivate(new PKCS8EncodedKeySpec(b64.decode(partes[4])))));
        } catch (GeneralSecurityException | IllegalArgumentException e) {
            throw new IllegalArgumentException("Não foi possível ler as chaves (" + algoritmos.geradorClassico()
                    + " + " + algoritmos.geradorPqc() + "): " + e.getMessage(), e);
        }
    }

    public String serializar() {
        Base64.Encoder b64 = Base64.getUrlEncoder().withoutPadding();
        return String.join(".", VERSAO,
                b64.encodeToString(classico.getPublic().getEncoded()),
                b64.encodeToString(classico.getPrivate().getEncoded()),
                b64.encodeToString(pqc.getPublic().getEncoded()),
                b64.encodeToString(pqc.getPrivate().getEncoded()));
    }
}
