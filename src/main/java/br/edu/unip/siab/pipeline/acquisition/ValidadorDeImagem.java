package br.edu.unip.siab.pipeline.acquisition;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.imageio.ImageIO;
import javax.imageio.ImageReader;
import javax.imageio.stream.ImageInputStream;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.util.Iterator;

/**
 * Validação de upload antes de qualquer decodificação pelo OpenCV (seção 2
 * do roteiro de segurança):
 * <ul>
 *   <li>só JPEG ou PNG, conferidos pelos <i>magic bytes</i> (o
 *       Content-Type do multipart é escolhido pelo cliente e não prova
 *       nada);</li>
 *   <li>dimensões lidas só do cabeçalho, sem decodificar os pixels, e
 *       limitadas — um PNG de poucos KB pode declarar 50.000 × 50.000 pixels
 *       e estourar a memória no {@code imdecode} ("bomba de
 *       descompressão").</li>
 * </ul>
 */
@Component
public class ValidadorDeImagem {

    private static final byte[] JPEG = {(byte) 0xFF, (byte) 0xD8, (byte) 0xFF};
    private static final byte[] PNG = {(byte) 0x89, 'P', 'N', 'G', '\r', '\n', 0x1A, '\n'};

    @Value("${siab.upload.dimensao-maxima:4096}")
    private int dimensaoMaxima = 4096;

    public void validar(byte[] bytes) {
        if (bytes == null || bytes.length == 0) {
            throw new IllegalArgumentException("Imagem vazia.");
        }
        if (!comecaCom(bytes, JPEG) && !comecaCom(bytes, PNG)) {
            throw new IllegalArgumentException("Formato de imagem não suportado (aceitos: JPEG e PNG).");
        }
        try (ImageInputStream entrada = ImageIO.createImageInputStream(new ByteArrayInputStream(bytes))) {
            Iterator<ImageReader> leitores = ImageIO.getImageReaders(entrada);
            if (!leitores.hasNext()) {
                throw new IllegalArgumentException("Imagem corrompida ou ilegível.");
            }
            ImageReader leitor = leitores.next();
            try {
                leitor.setInput(entrada, true, true);
                int largura = leitor.getWidth(0);
                int altura = leitor.getHeight(0);
                if (largura <= 0 || altura <= 0 || largura > dimensaoMaxima || altura > dimensaoMaxima) {
                    throw new IllegalArgumentException("Dimensões da imagem fora do limite (" + largura + "x" + altura
                            + "; máximo " + dimensaoMaxima + "x" + dimensaoMaxima + ").");
                }
            } finally {
                leitor.dispose();
            }
        } catch (IOException e) {
            throw new IllegalArgumentException("Imagem corrompida ou ilegível.", e);
        }
    }

    private static boolean comecaCom(byte[] bytes, byte[] prefixo) {
        if (bytes.length < prefixo.length) {
            return false;
        }
        for (int i = 0; i < prefixo.length; i++) {
            if (bytes[i] != prefixo[i]) {
                return false;
            }
        }
        return true;
    }
}
