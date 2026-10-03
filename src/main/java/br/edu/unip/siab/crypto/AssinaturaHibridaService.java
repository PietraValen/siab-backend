package br.edu.unip.siab.crypto;

import org.springframework.stereotype.Service;

import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.security.PublicKey;
import java.security.Signature;
import java.util.Base64;
import java.util.HexFormat;

/**
 * Assinatura híbrida <b>Ed25519 + ML-DSA-65</b> (seção 4.4 do roteiro de
 * segurança): a mesma mensagem é assinada pelos dois algoritmos e a
 * verificação só passa se <b>as duas</b> assinaturas forem válidas. Um
 * atacante precisa forjar Ed25519 (clássico) e ML-DSA (FIPS 204,
 * pós-quântico) ao mesmo tempo.
 * <p>
 * Usada para selar a cadeia de hashes do log de auditoria — ver
 * {@code br.edu.unip.siab.auditlog.cadeia}.
 */
@Service
public class AssinaturaHibridaService {

    /** As duas assinaturas, em Base64, prontas para gravar no banco ou exibir. */
    public record AssinaturaHibrida(String ed25519, String mlDsa) {
    }

    /** Chaves públicas em Base64 (X.509), para quem quiser verificar fora do sistema. */
    public record ChavesPublicas(String ed25519, String mlDsa65, String impressaoDigital) {
    }

    private final ConjuntoDeChaves chaves;

    public AssinaturaHibridaService(ChavesConfig.ChavesDeAssinatura chavesDeAssinatura) {
        this.chaves = chavesDeAssinatura.conjunto();
    }

    public AssinaturaHibrida assinar(byte[] mensagem) {
        try {
            Signature ed = Signature.getInstance("Ed25519");
            ed.initSign(chaves.classico().getPrivate());
            ed.update(mensagem);

            Signature mlDsa = Signature.getInstance("ML-DSA");
            mlDsa.initSign(chaves.pqc().getPrivate());
            mlDsa.update(mensagem);

            Base64.Encoder b64 = Base64.getEncoder();
            return new AssinaturaHibrida(b64.encodeToString(ed.sign()), b64.encodeToString(mlDsa.sign()));
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException("Falha ao assinar.", e);
        }
    }

    public boolean verificar(byte[] mensagem, AssinaturaHibrida assinatura) {
        try {
            Base64.Decoder b64 = Base64.getDecoder();
            return verificarCom("Ed25519", chaves.classico().getPublic(), mensagem, b64.decode(assinatura.ed25519()))
                    & verificarCom("ML-DSA", chaves.pqc().getPublic(), mensagem, b64.decode(assinatura.mlDsa()));
        } catch (GeneralSecurityException | IllegalArgumentException e) {
            return false;
        }
    }

    public ChavesPublicas chavesPublicas() {
        Base64.Encoder b64 = Base64.getEncoder();
        return new ChavesPublicas(
                b64.encodeToString(chaves.classico().getPublic().getEncoded()),
                b64.encodeToString(chaves.pqc().getPublic().getEncoded()),
                impressaoDigital());
    }

    /** SHA-256 das duas chaves públicas (16 primeiros bytes em hex) — identifica qual par assinou. */
    public String impressaoDigital() {
        try {
            MessageDigest sha = MessageDigest.getInstance("SHA-256");
            sha.update(chaves.classico().getPublic().getEncoded());
            sha.update(chaves.pqc().getPublic().getEncoded());
            return HexFormat.of().formatHex(sha.digest(), 0, 16);
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException(e);
        }
    }

    private static boolean verificarCom(String algoritmo, PublicKey chave, byte[] mensagem, byte[] assinatura)
            throws GeneralSecurityException {
        Signature verificador = Signature.getInstance(algoritmo);
        verificador.initVerify(chave);
        verificador.update(mensagem);
        return verificador.verify(assinatura);
    }
}
