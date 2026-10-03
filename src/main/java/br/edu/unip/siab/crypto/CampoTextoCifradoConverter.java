package br.edu.unip.siab.crypto;

import jakarta.persistence.AttributeConverter;

import java.nio.charset.StandardCharsets;
import java.util.Base64;

/**
 * Base dos conversores JPA que cifram colunas de texto com a
 * {@link CifraHibridaService}. Cada subclasse define o {@link #contexto()}
 * (nome da coluna), que entra como AAD do AES-GCM.
 * <p>
 * Valores antigos, gravados antes da cifra existir, não têm o prefixo
 * {@value #PREFIXO} e são devolvidos como estão — ver
 * {@link MigracaoCifraBiometrica}, que os recifra na inicialização.
 * <p>
 * As subclasses são {@code @Component}: o Spring Boot registra o
 * {@code SpringBeanContainer} no Hibernate, que então pede os conversores
 * ao contexto do Spring (com injeção de dependência) em vez de
 * instanciá-los com o construtor vazio.
 */
public abstract class CampoTextoCifradoConverter implements AttributeConverter<String, String> {

    public static final String PREFIXO = "enc:v1:";

    private final CifraHibridaService cifra;

    protected CampoTextoCifradoConverter(CifraHibridaService cifra) {
        this.cifra = cifra;
    }

    protected abstract String contexto();

    @Override
    public String convertToDatabaseColumn(String valor) {
        if (valor == null) {
            return null;
        }
        byte[] envelope = cifra.cifrar(valor.getBytes(StandardCharsets.UTF_8), contexto());
        return PREFIXO + Base64.getEncoder().encodeToString(envelope);
    }

    @Override
    public String convertToEntityAttribute(String valorNoBanco) {
        if (valorNoBanco == null || !valorNoBanco.startsWith(PREFIXO)) {
            return valorNoBanco; // legado em texto puro
        }
        byte[] envelope = Base64.getDecoder().decode(valorNoBanco.substring(PREFIXO.length()));
        return new String(cifra.decifrar(envelope, contexto()), StandardCharsets.UTF_8);
    }
}
