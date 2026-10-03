package br.edu.unip.siab.crypto;

import org.springframework.stereotype.Service;

import javax.crypto.Cipher;
import javax.crypto.SecretKey;
import javax.crypto.spec.GCMParameterSpec;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.SecureRandom;
import java.util.Arrays;

/**
 * Cifra de envelope híbrida para dados biométricos em repouso (seção 4.3
 * do roteiro de segurança; LGPD art. 5º, II e art. 46).
 * <p>
 * Cada valor recebe uma chave AES-256 própria, derivada de um
 * encapsulamento novo do {@link KemHibrido} (X25519 + ML-KEM-768) contra
 * a chave pública do sistema; o valor é cifrado com <b>AES-256-GCM</b>
 * (AES-256 continua seguro contra computador quântico: o algoritmo de
 * Grover só reduz a segurança efetiva para ~128 bits). Só quem tem as
 * duas chaves privadas — que ficam fora do banco, em variável de ambiente
 * — consegue decifrar. Um dump do MySQL sozinho não revela nenhum rosto.
 * <p>
 * O {@code contexto} entra como dado autenticado (AAD) do GCM: um valor
 * cifrado para uma coluna não decifra se for copiado para outra.
 * <p>
 * Formato gravado (todos os inteiros big-endian):
 * <pre>"SIAB" | versão (1 byte) | len(ctX25519) (2) | ctX25519 | len(ctMlKem) (2) | ctMlKem | IV (12) | ciphertext+tag</pre>
 * O identificador de versão permite trocar algoritmos no futuro sem
 * recifrar tudo de uma vez (cripto-agilidade).
 */
@Service
public class CifraHibridaService {

    private static final byte[] MAGICO = {'S', 'I', 'A', 'B'};
    private static final byte VERSAO = 1;
    private static final int TAMANHO_IV = 12;
    private static final int TAMANHO_TAG_BITS = 128;

    private final ConjuntoDeChaves chaves;
    private final SecureRandom aleatorio = new SecureRandom();

    public CifraHibridaService(ChavesConfig.ChavesDeCifra chavesDeCifra) {
        this.chaves = chavesDeCifra.conjunto();
    }

    public byte[] cifrar(byte[] claro, String contexto) {
        try {
            KemHibrido.Encapsulado encapsulado = KemHibrido.encapsular(chaves.classico().getPublic(), chaves.pqc().getPublic());

            byte[] iv = new byte[TAMANHO_IV];
            aleatorio.nextBytes(iv);
            byte[] cifrado = aesGcm(Cipher.ENCRYPT_MODE, encapsulado.chave(), iv, contexto, claro);

            byte[] ctX = encapsulado.ctClassico();
            byte[] ctPq = encapsulado.ctPqc();
            return ByteBuffer.allocate(MAGICO.length + 1 + 2 + ctX.length + 2 + ctPq.length + TAMANHO_IV + cifrado.length)
                    .put(MAGICO).put(VERSAO)
                    .putShort((short) ctX.length).put(ctX)
                    .putShort((short) ctPq.length).put(ctPq)
                    .put(iv).put(cifrado)
                    .array();
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException("Falha ao cifrar dado biométrico.", e);
        }
    }

    public byte[] decifrar(byte[] envelope, String contexto) {
        if (!estaCifrado(envelope)) {
            throw new IllegalArgumentException("Valor não está no formato cifrado do SIAB.");
        }
        try {
            ByteBuffer buffer = ByteBuffer.wrap(envelope, MAGICO.length + 1, envelope.length - MAGICO.length - 1);
            byte[] ctX = lerComTamanho(buffer);
            byte[] ctPq = lerComTamanho(buffer);
            byte[] iv = new byte[TAMANHO_IV];
            buffer.get(iv);
            byte[] cifrado = new byte[buffer.remaining()];
            buffer.get(cifrado);

            SecretKey chave = KemHibrido.decapsular(chaves.classico().getPrivate(), chaves.pqc().getPrivate(), ctX, ctPq);
            return aesGcm(Cipher.DECRYPT_MODE, chave, iv, contexto, cifrado);
        } catch (GeneralSecurityException | RuntimeException e) {
            // Tag GCM inválida = dado adulterado, chave errada ou contexto trocado.
            throw new IllegalStateException("Falha ao decifrar dado biométrico (" + contexto + ").", e);
        }
    }

    /** {@code true} se o valor começa com o cabeçalho do envelope (versão atual). */
    public boolean estaCifrado(byte[] valor) {
        return valor != null
                && valor.length > MAGICO.length + 1
                && Arrays.equals(valor, 0, MAGICO.length, MAGICO, 0, MAGICO.length)
                && valor[MAGICO.length] == VERSAO;
    }

    private static byte[] aesGcm(int modo, SecretKey chave, byte[] iv, String contexto, byte[] entrada)
            throws GeneralSecurityException {
        Cipher gcm = Cipher.getInstance("AES/GCM/NoPadding");
        gcm.init(modo, chave, new GCMParameterSpec(TAMANHO_TAG_BITS, iv));
        gcm.updateAAD(contexto.getBytes(StandardCharsets.UTF_8));
        return gcm.doFinal(entrada);
    }

    private static byte[] lerComTamanho(ByteBuffer buffer) {
        int tamanho = Short.toUnsignedInt(buffer.getShort());
        byte[] valor = new byte[tamanho];
        buffer.get(valor);
        return valor;
    }
}
