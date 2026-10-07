package br.edu.unip.siab;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Lob;
import org.hibernate.Length;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.config.BeanDefinition;
import org.springframework.context.annotation.ClassPathScanningCandidateComponentProvider;
import org.springframework.core.type.filter.AnnotationTypeFilter;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Sem {@code length}, o Hibernate 7 cria um {@code @Lob} como TINYTEXT/TINYBLOB
 * (255 bytes) no MySQL e os valores cifrados não cabem — o H2 dos testes não
 * reclama, então a regra é conferida aqui direto nas anotações.
 */
class LobColunasTest {

    @Test
    void todoLobTemTamanhoDeLongtext() throws ClassNotFoundException {
        var scanner = new ClassPathScanningCandidateComponentProvider(false);
        scanner.addIncludeFilter(new AnnotationTypeFilter(Entity.class));

        List<String> lobs = new ArrayList<>();
        List<String> semTamanho = new ArrayList<>();
        for (BeanDefinition entidade : scanner.findCandidateComponents("br.edu.unip.siab")) {
            for (Field campo : Class.forName(entidade.getBeanClassName()).getDeclaredFields()) {
                if (!campo.isAnnotationPresent(Lob.class)) {
                    continue;
                }
                String nome = campo.getDeclaringClass().getSimpleName() + "." + campo.getName();
                lobs.add(nome);
                Column coluna = campo.getAnnotation(Column.class);
                if (coluna == null || coluna.length() < Length.LONG32) {
                    semTamanho.add(nome);
                }
            }
        }

        assertThat(lobs).isNotEmpty();
        assertThat(semTamanho).as("@Lob sem @Column(length = Length.LONG32)").isEmpty();
    }
}
