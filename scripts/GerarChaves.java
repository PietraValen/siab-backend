// Gera os dois conjuntos de chaves híbridas do SIAB (docs/seguranca.md).
//
// Uso (JDK 25, sem compilar nada antes):
//   java scripts/GerarChaves.java
//
// Copie as duas linhas para o .env (ou para os secrets do servidor).
// Guarde uma cópia em local seguro: sem SIAB_CHAVES_CIFRA, os vetores e as
// fotos biométricas gravados ficam ilegíveis para sempre.

import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.util.Base64;

void main() throws Exception {
    IO.println("SIAB_CHAVES_CIFRA=" + gerar("X25519", "ML-KEM-768"));
    IO.println("SIAB_CHAVES_ASSINATURA=" + gerar("Ed25519", "ML-DSA-65"));
}

String gerar(String classico, String pqc) throws Exception {
    KeyPair c = KeyPairGenerator.getInstance(classico).generateKeyPair();
    KeyPair q = KeyPairGenerator.getInstance(pqc).generateKeyPair();
    Base64.Encoder b64 = Base64.getUrlEncoder().withoutPadding();
    return String.join(".", "v1",
            b64.encodeToString(c.getPublic().getEncoded()), b64.encodeToString(c.getPrivate().getEncoded()),
            b64.encodeToString(q.getPublic().getEncoded()), b64.encodeToString(q.getPrivate().getEncoded()));
}
