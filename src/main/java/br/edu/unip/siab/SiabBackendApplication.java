package br.edu.unip.siab;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * SIAB - Sistema de Identificação e Autenticação Biométrica.
 * <p>
 * Ponto de entrada da API. A arquitetura segue o que está descrito na seção 5
 * do documento de escopo do projeto: o back-end concentra o pipeline de
 * processamento de imagem (5 fases), o controle de acesso por níveis e a
 * persistência em MySQL. O front-end (Next.js) consome esta API via REST/JSON.
 */
@SpringBootApplication
public class SiabBackendApplication {

    public static void main(String[] args) {
        SpringApplication.run(SiabBackendApplication.class, args);
    }
}
