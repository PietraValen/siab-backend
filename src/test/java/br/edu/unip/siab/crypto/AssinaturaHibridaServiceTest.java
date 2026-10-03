package br.edu.unip.siab.crypto;

import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;

/** Assinatura híbrida Ed25519 + ML-DSA-65: só vale se as duas metades forem válidas. */
class AssinaturaHibridaServiceTest {

    private final AssinaturaHibridaService assinatura = new AssinaturaHibridaService(
            new ChavesConfig.ChavesDeAssinatura(ConjuntoDeChaves.gerar(ConjuntoDeChaves.ASSINATURA)));

    private final byte[] mensagem = "SIAB-CHECKPOINT-v1|logs_acesso|42|abc".getBytes(StandardCharsets.UTF_8);

    @Test
    void assinaturaValidaEhAceita() {
        var assinado = assinatura.assinar(mensagem);

        assertThat(assinatura.verificar(mensagem, assinado)).isTrue();
        assertThat(java.util.Base64.getDecoder().decode(assinado.mlDsa())).hasSize(3309); // ML-DSA-65
    }

    @Test
    void mensagemAlteradaEhRecusada() {
        var assinado = assinatura.assinar(mensagem);
        byte[] alterada = "SIAB-CHECKPOINT-v1|logs_acesso|43|abc".getBytes(StandardCharsets.UTF_8);

        assertThat(assinatura.verificar(alterada, assinado)).isFalse();
    }

    @Test
    void bastaUmaMetadeInvalidaParaRecusar() {
        var assinado = assinatura.assinar(mensagem);
        var outra = assinatura.assinar("outra".getBytes(StandardCharsets.UTF_8));

        // Ed25519 certo + ML-DSA de outra mensagem (e vice-versa): recusado.
        assertThat(assinatura.verificar(mensagem, new AssinaturaHibridaService.AssinaturaHibrida(assinado.ed25519(), outra.mlDsa()))).isFalse();
        assertThat(assinatura.verificar(mensagem, new AssinaturaHibridaService.AssinaturaHibrida(outra.ed25519(), assinado.mlDsa()))).isFalse();
    }
}
