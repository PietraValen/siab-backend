package br.edu.unip.siab;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.TestPropertySource;

/**
 * Verifica se o contexto do Spring sobe sem erros. Usa um banco em memória
 * apenas para o teste (não depende do MySQL/Aiven real).
 */
@SpringBootTest
@TestPropertySource(properties = {
        "spring.datasource.url=jdbc:h2:mem:testdb",
        "spring.datasource.driver-class-name=org.h2.Driver",
        "spring.jpa.hibernate.ddl-auto=create-drop",
        "spring.sql.init.mode=never"
})
class SiabBackendApplicationTests {

    @Test
    void contextLoads() {
    }
}
