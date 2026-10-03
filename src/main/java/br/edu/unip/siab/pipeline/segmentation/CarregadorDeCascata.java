package br.edu.unip.siab.pipeline.segmentation;

import org.bytedeco.opencv.opencv_objdetect.CascadeClassifier;
import org.springframework.core.io.ClassPathResource;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;

/**
 * Carrega um classificador Haar a partir de um caminho que pode ser
 * "classpath:..." — usado pela Fase 3 (rosto) e pela detecção de piscada
 * do liveness (olhos).
 */
public final class CarregadorDeCascata {

    private CarregadorDeCascata() {
    }

    public static CascadeClassifier carregar(String caminho) {
        return new CascadeClassifier(resolverCaminho(caminho));
    }

    /**
     * O {@link CascadeClassifier} do OpenCV só sabe carregar de um caminho
     * de arquivo real no disco — não existe overload que aceite um
     * InputStream/recurso dentro de um .jar. O código antigo trocava
     * "classpath:" por "src/main/resources/" na marra, o que só funciona
     * rodando via Maven/IDE (com o diretório de trabalho na raiz do
     * projeto); dentro do container Docker/Podman só existe o jar
     * empacotado, então esse caminho relativo nunca existe e o
     * classificador ficava sempre vazio — toda tentativa de cadastro ou
     * reconhecimento falhava na Fase 3 com "!empty() in detectMultiScale".
     * Por isso extraímos o recurso do classpath para um arquivo temporário
     * e carregamos a partir dele, o que funciona nos dois ambientes.
     */
    static String resolverCaminho(String path) {
        if (!path.startsWith("classpath:")) {
            return path;
        }

        String localizacaoNoClasspath = path.substring("classpath:".length());
        try (InputStream entrada = new ClassPathResource(localizacaoNoClasspath).getInputStream()) {
            Path arquivoTemporario = Files.createTempFile("haarcascade-", ".xml");
            arquivoTemporario.toFile().deleteOnExit();
            Files.copy(entrada, arquivoTemporario, StandardCopyOption.REPLACE_EXISTING);
            return arquivoTemporario.toAbsolutePath().toString();
        } catch (IOException e) {
            throw new IllegalStateException("Não foi possível carregar o classificador Haar de " + path, e);
        }
    }
}
