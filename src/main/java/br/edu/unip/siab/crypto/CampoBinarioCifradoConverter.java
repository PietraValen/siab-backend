package br.edu.unip.siab.crypto;

import jakarta.persistence.AttributeConverter;

/**
 * Versão binária do {@link CampoTextoCifradoConverter} (ex.: foto de
 * referência do cadastro). Valores legados, sem o cabeçalho do envelope,
 * são devolvidos como estão.
 */
public abstract class CampoBinarioCifradoConverter implements AttributeConverter<byte[], byte[]> {

    private final CifraHibridaService cifra;

    protected CampoBinarioCifradoConverter(CifraHibridaService cifra) {
        this.cifra = cifra;
    }

    protected abstract String contexto();

    @Override
    public byte[] convertToDatabaseColumn(byte[] valor) {
        return valor == null ? null : cifra.cifrar(valor, contexto());
    }

    @Override
    public byte[] convertToEntityAttribute(byte[] valorNoBanco) {
        if (valorNoBanco == null || !cifra.estaCifrado(valorNoBanco)) {
            return valorNoBanco; // legado em texto puro
        }
        return cifra.decifrar(valorNoBanco, contexto());
    }
}
