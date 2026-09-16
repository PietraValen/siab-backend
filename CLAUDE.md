# CLAUDE.md — Contexto do projeto SIAB (back-end)

Este arquivo é lido automaticamente pelo Claude Code ao abrir este diretório.
Ele existe para dar contexto de arquitetura e prioridades, evitando que cada
sessão precise redescobrir a estrutura do zero.

## O que é este projeto

API back-end do **SIAB — Sistema de Identificação e Autenticação Biométrica**,
trabalho de APS (Atividades Práticas Supervisionadas) da disciplina de
Processamento de Imagem e Visão Computacional (PIVC) — UNIP.

O sistema simula o controle de acesso a um cofre de segurança máxima com 3
níveis de permissão, usando reconhecimento facial. **O requisito central da
disciplina é implementar as 5 fases clássicas do processamento de imagens
digitais "na mão"** (aquisição, pré-processamento, segmentação, extração de
características, reconhecimento) — não é aceitável substituir isso por uma
API de reconhecimento facial de terceiros (Azure Face API, AWS Rekognition,
Google Vision, etc.). Isso anularia o objetivo acadêmico do trabalho.

## Stack

- Java 25 (LTS) + Spring Boot 4.1.1
- OpenCV via JavaCV (`org.bytedeco:javacv-platform`) para todo o pipeline de imagem
- MySQL em produção (hospedado no Aiven), H2 em memória nos testes
- JWT (jjwt) para autenticação do painel admin
- Maven

## Mapa de módulos (`src/main/java/br/edu/unip/siab/`)

| Pacote | Responsabilidade |
|---|---|
| `auth/` | Login administrativo via JWT |
| `admin/` | Administradores do painel (entidade real no banco, ver abaixo) |
| `user/` | CRUD de usuários e níveis de acesso |
| `accesslevel/` | Entidade dos 3 níveis (Geral/Diretoria/Ministro) |
| `pipeline/preprocessing/` | **Fase 2** — escala de cinza, equalização |
| `pipeline/segmentation/` | **Fase 3** — detecção facial (Haar Cascade) |
| `pipeline/feature/` | **Fase 4** — extração de características (LBPH) + foto de referência do cadastro |
| `pipeline/recognition/` | **Fase 5** — comparação por similaridade |
| `pipeline/liveness/` | Anti-spoofing (variância do Laplaciano) |
| `pipeline/acquisition/` | **Fase 1** — controllers REST (`/enrollment`, `/recognition/scan`) |
| `pipeline/PipelineOrchestratorService.java` | Amarra as 5 fases na ordem certa — **não altere a ordem das chamadas** |
| `accesscontrol/` | Regra de negócio dos 3 níveis |
| `auditlog/` | Logs de tentativas de acesso |
| `reporting/` | Resumo + relatório de auditoria em PDF (OpenPDF) |

## Estado atual — o que está pronto vs. pendente

✅ **Pronto e testado:** todas as 5 fases do pipeline, auth (com tabela real
de administradores), user-management, access-control, audit-log, reporting
(PDF real), Swagger anotado, estrutura REST completa, orquestração do
pipeline.

🚧 **Pendente — só é possível com dados reais, não algo que dá pra "codar":**

1. **Calibração do `recognition-threshold`** (`RecognitionService`, hoje
   `0.35`) **e do `liveness-variance-threshold`** (`LivenessService`, hoje
   `80.0`) — ambos são chutes iniciais. Calibração de verdade exige um
   dataset de capturas reais (mesma pessoa vs. pessoas diferentes) para
   medir a distribuição de distâncias e escolher o ponto de corte (ex.:
   Equal Error Rate). Cada cadastro feito via `/api/enrollment` já salva a
   foto original (ver `FaceEmbeddingImagem`), então o dataset vai se
   acumulando naturalmente com o uso — quando houver fotos suficientes, dá
   pra escrever um script/endpoint que processe esse dataset e sugira os
   valores.
2. **Teste de segmentação com foto real** — `SegmentationServiceTest` só
   cobre o caso "nenhum rosto detectado" (imagem preta). Adicionar uma foto
   real de rosto em `src/test/resources/fixtures/` (com consentimento de
   quem aparece na foto — dado biométrico) e testar
   `service.segmentar(...)` retornando um Mat não vazio fica para o grupo.

## Harness de testes — use isso como guia

```bash
mvn test                    # roda toda a suíte (H2 em memória, sem precisar do Aiven)
mvn test -Dtest=RecognitionServiceTest   # roda uma classe específica
```

- `src/test/.../accesscontrol/`, `.../recognition/`, `.../pipeline/feature/` —
  testes unitários com mocks, já passam com o código atual. Servem de exemplo
  de estilo (JUnit 5 + Mockito) para os testes que você for adicionar.
- `src/test/.../pipeline/segmentation/SegmentationServiceTest.java` — pula
  automaticamente (não falha) enquanto o `haarcascade_frontalface_default.xml`
  não existir. Rode `scripts/download-haarcascade.sh` e ele passa a rodar
  sem precisar editar nada no teste.
- `src/test/.../*ControllerTest.java` — testes `@WebMvcTest` com MockMvc,
  validam contrato HTTP sem precisar de banco real.
- **Antes de considerar qualquer TODO acima "concluído", rode `mvn test`
  e garanta que nada quebrou.**
- Não há Maven local neste ambiente de desenvolvimento — rode `mvn` sempre
  dentro do container oficial: `docker run --rm -v "$PWD":/app -w /app -v
  maven-repo-siab:/root/.m2 maven:3.9-eclipse-temurin-25 mvn ...` (o volume
  nomeado cacheia o `.m2` entre execuções). Note que essa imagem base NÃO
  tem as libs nativas de GTK que o OpenCV/JavaCV precisa para carregar (ver
  nota abaixo) — para rodar `mvn test` de ponta a ponta (incluindo
  `SiabBackendApplicationTests`), construa uma variante local com
  `RUN apt-get install -y libgtk2.0-0 libcanberra-gtk-module libgl1
  libglib2.0-0` em cima dessa imagem.

## Notas de ambiente (Java 25 / Spring Boot 4.1 / JavaCV) — armadilhas já resolvidas

Estas descobertas custaram tempo de investigação; documentadas aqui para não
serem re-descobertas em sessões futuras.

- **Lombok não gerava getters/setters** com Java 25 +
  `maven-compiler-plugin` 3.15 usando auto-discovery de annotation
  processors no classpath. Corrigido declarando o path do processor
  explicitamente no `pom.xml` (`<annotationProcessorPaths>`), que é também
  a prática recomendada pelo próprio plugin.
- **Spring Boot 4 modularizou os módulos de teste.** `@WebMvcTest` e
  `@AutoConfigureMockMvc` saíram de `spring-boot-test-autoconfigure` e
  foram para um artefato novo e dedicado,
  `org.springframework.boot:spring-boot-starter-webmvc-test` (pacote
  `org.springframework.boot.webmvc.test.autoconfigure`). `@MockBean` foi
  removido — use `@org.springframework.test.context.bean.override.mockito.MockitoBean`
  (de `spring-test`, não `spring-boot-test`).
- **Boot 4 usa Jackson 3 (`tools.jackson.*`) por padrão**, não mais
  `com.fasterxml.jackson.databind.ObjectMapper`. Um `@Autowired
  ObjectMapper` (tipo antigo) num contexto `@WebMvcTest` não encontra bean
  — se precisar de um `ObjectMapper` de verdade num teste, instancie
  `new ObjectMapper()` diretamente em vez de injetar.
- **Mockar um `Filter` do Spring Security (ex.: `@MockitoBean
  JwtAuthFilter`) quebra a cadeia de filtros silenciosamente** — o mock não
  chama `filterChain.doFilter(...)`, então a requisição nunca chega ao
  `DispatcherServlet` e o `MockMvc` devolve 200 com corpo vazio para
  qualquer request. Quando o filtro precisa ser mockado só para satisfazer
  a injeção de dependência do `SecurityConfig` (não para ser exercitado),
  desligue a cadeia inteira com `@AutoConfigureMockMvc(addFilters = false)`
  e confie em `@WithMockUser` para simular o principal autenticado.
- **`javacv-platform` empacota o OpenCV inteiro como uma lib nativa só**
  (`opencv_objdetect` + `opencv_highgui` juntos), então mesmo usando só
  `CascadeClassifier` (Fase 3), o carregamento nativo exige GTK2
  (`libgtk-x11-2.0.so.0`), ausente tanto na imagem `maven:...-eclipse-temurin-25`
  quanto na imagem de runtime `eclipse-temurin:25-jre-jammy` do `Dockerfile`.
  **Isso derrubava a aplicação inteira na inicialização em produção**
  (`SegmentationService` é `@Service` com `@PostConstruct`, carregado
  eager). Corrigido instalando `libgtk2.0-0 libcanberra-gtk-module libgl1
  libglib2.0-0` no estágio de runtime do `Dockerfile`.
- **`data.sql` rodava antes do Hibernate criar o schema** (`ddl-auto:
  update`), então a semeadura de `niveis_acesso`/`administradores` falhava
  com "Table ... doesn't exist" na primeira vez que a aplicação sobe contra
  um banco *realmente* vazio (`mvn test` não pegava isso, porque os testes
  usam `spring.sql.init.mode=never`; e o `docker build` também não, porque
  usa `-DskipTests` e nunca chega a rodar a aplicação). Corrigido com
  `spring.jpa.defer-datasource-initialization: true` em `application.yml`.
  Validado subindo a aplicação de verdade contra um MySQL 8.4 recém-criado
  (container descartável) — sem esse flag, o boot falhava sempre na
  primeira execução contra um banco novo.

## Convenções do código

- Entidades, DTOs e nomes de domínio ficam em **português** (bate com a
  dissertação e com o banco de dados) — ex.: `Usuario`, `NivelAcesso`,
  `nivelAcesso`. Nomes técnicos genéricos (services, controllers, config)
  ficam em inglês, como é padrão em projetos Spring.
- DTOs são `record` Java (imutáveis).
- Lombok (`@Getter`/`@Setter`/`@RequiredArgsConstructor`) para reduzir
  boilerplate — já configurado no `pom.xml`.
- Toda decisão técnica relevante (algoritmo escolhido, threshold, etc.) deve
  virar um comentário Javadoc referenciando a seção correspondente do
  `docs/Escopo_Projeto_Biometria_Facial.pdf`, porque o grupo precisa
  justificar essas escolhas na dissertação final.

## Rodando localmente

```bash
cp .env.example .env        # preencher com os dados reais do Aiven
export $(cat .env | xargs)  # ou configurar as env vars na sua IDE
mvn spring-boot:run       # sobe em http://localhost:8080
```

Swagger em `http://localhost:8080/swagger-ui.html` — todos os endpoints
anotados (`@Tag`/`@Operation`), com botão "Authorize" já configurado para
Bearer JWT (cole o token de `POST /api/auth/login`).

Requisições de exemplo prontas em `requests.http` (abra com a extensão
"REST Client" do VS Code e clique em "Send Request" acima de cada bloco).
Para testar pelo Postman/Insomnia, importe `siab-backend.postman_collection.json`
— o login já salva o token automaticamente na variável `token`, reaproveitada
por todas as outras requisições.
