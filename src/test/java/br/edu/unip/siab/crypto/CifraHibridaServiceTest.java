package br.edu.unip.siab.crypto;

import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Cifra de envelope híbrida X25519 + ML-KEM-768 / AES-256-GCM usada nos
 * dados biométricos em repouso.
 */
class CifraHibridaServiceTest {

    private final CifraHibridaService cifra = new CifraHibridaService(
            new ChavesConfig.ChavesDeCifra(ConjuntoDeChaves.gerar(ConjuntoDeChaves.CIFRA)));

    private final byte[] vetor = "0.123,-0.045,0.998".getBytes(StandardCharsets.UTF_8);

    @Test
    void cifraEDecifraDeVolta() {
        byte[] envelope = cifra.cifrar(vetor, "face_embeddings.vetor");

        assertThat(cifra.estaCifrado(envelope)).isTrue();
        assertThat(new String(envelope, StandardCharsets.ISO_8859_1)).doesNotContain("0.123");
        assertThat(cifra.decifrar(envelope, "face_embeddings.vetor")).isEqualTo(vetor);
    }

    @Test
    void cadaCifragemUsaUmEncapsulamentoNovo() {
        assertThat(cifra.cifrar(vetor, "x")).isNotEqualTo(cifra.cifrar(vetor, "x"));
    }

    @Test
    void valorCopiadoParaOutraColunaNaoDecifra() {
        byte[] envelope = cifra.cifrar(vetor, "face_embeddings.vetor");

        assertThatThrownBy(() -> cifra.decifrar(envelope, "terminais.chave"))
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    void qualquerByteAlteradoEhDetectado() {
        byte[] envelope = cifra.cifrar(vetor, "c");
        for (int posicao : new int[]{10, 600, envelope.length - 1}) { // ct X25519, ct ML-KEM, tag GCM
            byte[] adulterado = envelope.clone();
            adulterado[posicao] ^= 0x01;
            assertThatThrownBy(() -> cifra.decifrar(adulterado, "c")).isInstanceOf(IllegalStateException.class);
        }
    }

    @Test
    void outroParDeChavesNaoDecifra() {
        CifraHibridaService outra = new CifraHibridaService(
                new ChavesConfig.ChavesDeCifra(ConjuntoDeChaves.gerar(ConjuntoDeChaves.CIFRA)));

        assertThatThrownBy(() -> outra.decifrar(cifra.cifrar(vetor, "c"), "c"))
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    void conversorDevolveValorLegadoEmTextoPuroComoEsta() {
        var conversor = new Conversores.VetorFacial(cifra);

        assertThat(conversor.convertToEntityAttribute("0.5,0.25")).isEqualTo("0.5,0.25");

        String gravado = conversor.convertToDatabaseColumn("0.5,0.25");
        assertThat(gravado).startsWith(CampoTextoCifradoConverter.PREFIXO).doesNotContain("0.25");
        assertThat(conversor.convertToEntityAttribute(gravado)).isEqualTo("0.5,0.25");
    }

    @Test
    void chavesSerializadasSaoLidasDeVolta() {
        ConjuntoDeChaves original = ConjuntoDeChaves.gerar(ConjuntoDeChaves.CIFRA);
        ConjuntoDeChaves lido = ConjuntoDeChaves.ler(original.serializar(), ConjuntoDeChaves.CIFRA);

        CifraHibridaService comOriginal = new CifraHibridaService(new ChavesConfig.ChavesDeCifra(original));
        CifraHibridaService comLido = new CifraHibridaService(new ChavesConfig.ChavesDeCifra(lido));
        assertThat(comLido.decifrar(comOriginal.cifrar(vetor, "c"), "c")).isEqualTo(vetor);
    }
}
