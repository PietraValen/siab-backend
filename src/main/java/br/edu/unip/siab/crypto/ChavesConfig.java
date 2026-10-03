package br.edu.unip.siab.crypto;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Carrega os dois conjuntos de chaves híbridas das variáveis de ambiente
 * {@code SIAB_CHAVES_CIFRA} e {@code SIAB_CHAVES_ASSINATURA}.
 * <p>
 * Sem elas a aplicação <b>não sobe</b> (falha rápida de propósito): subir
 * sem chave significaria gravar biometria em texto puro ou perder a
 * capacidade de decifrá-la depois. A única exceção é
 * {@code siab.crypto.chaves-efemeras=true}, usada só nos testes, que gera
 * chaves novas em memória a cada execução — nunca use isso com um banco de
 * verdade, porque tudo que for cifrado fica ilegível no próximo restart.
 */
@Configuration
public class ChavesConfig {

    private static final Logger log = LoggerFactory.getLogger(ChavesConfig.class);

    static final String COMO_GERAR = "Gere com: java scripts/GerarChaves.java (JDK 25) e defina a variável de ambiente.";

    @Value("${siab.crypto.chaves-cifra:}")
    private String chavesCifra;

    @Value("${siab.crypto.chaves-assinatura:}")
    private String chavesAssinatura;

    @Value("${siab.crypto.chaves-efemeras:false}")
    private boolean chavesEfemeras;

    @Bean
    public ChavesDeCifra chavesDeCifra() {
        return new ChavesDeCifra(carregar(chavesCifra, "SIAB_CHAVES_CIFRA", ConjuntoDeChaves.CIFRA));
    }

    @Bean
    public ChavesDeAssinatura chavesDeAssinatura() {
        return new ChavesDeAssinatura(carregar(chavesAssinatura, "SIAB_CHAVES_ASSINATURA", ConjuntoDeChaves.ASSINATURA));
    }

    private ConjuntoDeChaves carregar(String valor, String variavel, ConjuntoDeChaves.Algoritmos algoritmos) {
        if (valor != null && !valor.isBlank()) {
            return ConjuntoDeChaves.ler(valor, algoritmos);
        }
        if (chavesEfemeras) {
            log.warn("{} ausente: usando chaves EFÊMERAS (só para testes — dados cifrados não sobrevivem ao restart).", variavel);
            return ConjuntoDeChaves.gerar(algoritmos);
        }
        throw new IllegalStateException("Variável de ambiente " + variavel + " não definida. " + COMO_GERAR);
    }

    /** Conjunto X25519 + ML-KEM-768 que protege os dados biométricos em repouso. */
    public record ChavesDeCifra(ConjuntoDeChaves conjunto) {
    }

    /** Conjunto Ed25519 + ML-DSA-65 que assina a cadeia do log de auditoria. */
    public record ChavesDeAssinatura(ConjuntoDeChaves conjunto) {
    }
}
