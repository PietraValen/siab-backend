# SIAB — Back-end (Java / Spring Boot)

API do Sistema de Identificação e Autenticação Biométrica, projeto de APS
(PIVC — UNIP).

**Cenário da APS:** no Ministério do Meio Ambiente há um cofre de segurança
máxima com relatórios ultrassecretos sobre toxinas de altíssimo risco. O
SIAB é a última linha de defesa desse cofre: identifica e autentica o rosto
de quem chega a uma porta e libera ou bloqueia a entrada conforme três
níveis de permissão.

| Nível | Quem | O que a porta exige |
|---|---|---|
| 1 — Acesso Geral | Servidores e equipe técnica | Rosto + prova de vida |
| 2 — Diretoria | Diretores de divisões específicas | Rosto + prova de vida |
| 3 — Ministro | Ministro do Meio Ambiente | Rosto + prova de vida + PIN |

Quem tem nível maior também entra nas portas de nível menor. O nível de cada
porta vem do terminal cadastrado, não do quiosque.

**Identificação vs. autenticação:** a Fase 5 compara o vetor capturado com
todos os cadastros e escolhe o mais próximo (identificação 1:N). A pessoa só
é autenticada se essa distância ficar abaixo do limiar, se a prova de vida
passar e, no nível Ministro, se o PIN estiver certo. Toda tentativa,
concedida ou negada, vai para o log de auditoria encadeado por hash.

> **Nota de versão:** o documento de escopo menciona "Spring Boot 3", mas a
> linha 3.x saiu de suporte (EOL) em 30/06/2026. Este projeto usa
> **Spring Boot 4.1.1** (Java 25 LTS), a versão atualmente suportada. Vale
> atualizar a seção 4.1 do escopo para refletir isso.

## Estrutura de pacotes

```
br.edu.unip.siab
├── config/            Segurança (JWT), CORS, OpenAPI/Swagger
├── auth/               Login administrativo (cookie HttpOnly + CSRF, MFA TOTP)
├── admin/              Administradores do painel
├── user/               CRUD de usuários
├── accesslevel/        Os 3 níveis de acesso (niveis_acesso)
├── pipeline/
│   ├── acquisition/    Fase 1 — controllers de /enrollment e /recognition/scan
│   ├── preprocessing/  Fase 2 — escala de cinza, equalização de histograma
│   ├── segmentation/   Fase 3 — detecção facial (Haar Cascade)
│   ├── feature/        Fase 4 — LBPH implementado pixel a pixel
│   ├── recognition/    Fase 5 — distância euclidiana + limiar, e calibração do limiar
│   ├── liveness/       Anti-spoofing (variância do Laplaciano + piscada)
│   └── PipelineOrchestratorService.java  — amarra as 5 fases (seção 5.5)
├── accesscontrol/      Regra dos 3 níveis + PIN do nível Ministro
├── terminal/           Terminais (portas) com chave HMAC e desafio por nonce
├── crypto/             Biometria cifrada em repouso e assinatura híbrida (PQC)
├── auditlog/           Logs de tentativas e de ações admin, encadeados e selados
├── security/           Rate limiting e bloqueio progressivo de login
└── reporting/          Resumo e relatório de auditoria em PDF
```

## O que já funciona vs. o que depende de dados reais

✅ **Funcionando e testado:** as 5 fases do pipeline, prova de vida, regra
dos 3 níveis com PIN no nível Ministro, terminais assinados, auditoria
encadeada, relatório em PDF, login administrativo com MFA e Swagger.

🚧 **Depende de capturas reais do grupo:**
1. **Recalibrar o limiar de reconhecimento** (`siab.pipeline.recognition-threshold`,
   hoje `1.0`). Esse valor veio de selfies de 2 integrantes tiradas em
   lugares e luzes diferentes: com ele nenhum impostor é aceito, mas 76%
   dos pares da mesma pessoa também são rejeitados, porque o LBPH sofre com
   mudança de luz e de pose. Cadastre 2 ou mais fotos de cada pessoa pela
   câmera do próprio terminal e consulte `GET /api/admin/calibracao`: ele
   mede as distâncias entre a mesma pessoa e entre pessoas diferentes e
   devolve a FAR/FRR do limiar atual, o limiar do Equal Error Rate e o maior
   limiar sem nenhuma falsa aceitação (o mais indicado para um cofre).
2. **Calibrar o limiar de textura do liveness** (`liveness-variance-threshold`).
3. **Teste de segmentação com foto real**: coloque
   `src/test/resources/fixtures/rosto-exemplo.jpg` localmente, com
   consentimento de quem aparece, e rode `mvn test -Dtest=SegmentationServiceTest`.
   A pasta está no `.gitignore`, então a foto não vai para o repositório.

## Como rodar localmente (fora de container, para desenvolvimento)

1. Copie `.env.example` para `.env` e preencha com os dados do Aiven.
   `JWT_SECRET` é obrigatório: a aplicação não sobe sem ele, com menos de
   32 bytes ou com o valor de exemplo. Gere um com `openssl rand -base64 48`.
2. Exporte as variáveis (ou configure na sua IDE) e rode:
   ```bash
   mvn spring-boot:run
   ```
3. Acesse `http://localhost:8080/swagger-ui.html`
4. Num banco novo não existe nenhum administrador (nenhuma senha padrão é
   semeada). Crie o primeiro com `POST /api/admin/administradores`, que
   dispensa JWT só enquanto a tabela estiver vazia (bloco "0." do
   `requests.http`), e depois faça login normalmente.

## Como rodar via Podman (mesmo ambiente da VM)

```bash
podman build -t siab-backend .
podman run -p 8080:8080 --env-file .env siab-backend
```

## Trabalhando neste projeto com o Claude Code (VS Code)

Este repositório já vem com um harness pronto para o Claude Code trabalhar:

- **`CLAUDE.md`** — contexto automático (arquitetura, prioridades, convenções). O Claude Code lê isso sozinho ao abrir a pasta.
- **`.vscode/`** — debug configurado, extensões recomendadas (Java, Spring Boot, REST Client)
- **`.devcontainer/`** — ambiente Java 25 padronizado, caso algum integrante do grupo use Dev Containers
- **Testes em `src/test/`** — cobrem o que já funciona (access-control, recognition, serialização de embeddings, contrato HTTP dos controllers) e servem de exemplo de estilo para os testes que faltam
- **`requests.http`** — requisições prontas para testar a API manualmente (extensão REST Client)
- **`scripts/download-haarcascade.sh`** — baixa o arquivo que falta para a Fase 3 funcionar

Fluxo sugerido: abra a pasta no VS Code, rode `scripts/download-haarcascade.sh`,
rode `mvn test` para ver o que já passa, e peça ao Claude Code para implementar
os TODOs na ordem listada no `CLAUDE.md` — pedindo pra ele rodar `mvn test`
depois de cada mudança.

## Endpoints principais
| Método | Rota | Descrição |
|---|---|---|
| POST | `/api/auth/login` | Login do painel admin (`codigoMfa` se o MFA estiver ligado); grava o cookie `SIAB_TOKEN` e devolve o JWT |
| POST | `/api/auth/logout` | Revoga o token e apaga o cookie |
| GET | `/api/auth/csrf` | Token CSRF para chamadas do painel via cookie (header `X-XSRF-TOKEN`) |
| GET | `/api/admin/sessao` | Admin logado e se o MFA está ativo |
| POST/DELETE | `/api/admin/mfa/...` | Configurar, ativar e desativar o MFA (TOTP) |
| GET/POST/PUT/DELETE | `/api/admin/usuarios` | CRUD de usuários, com PIN opcional (requer JWT) |
| GET/POST/DELETE | `/api/admin/terminais` | Cadastro e revogação de terminais de reconhecimento (requer JWT) |
| POST | `/api/enrollment` | Cadastra o rosto de um usuário (multipart: `usuarioId`, `imagem`; requer JWT) |
| GET | `/api/recognition/desafio` | Nonce de uso único para o terminal (header `X-Terminal-Id`) |
| POST | `/api/recognition/scan` | Roda o pipeline e decide o acesso (multipart: `imagens`, `pin`; requisição assinada pelo terminal) |
| GET | `/api/admin/logs` | Lista o histórico de tentativas (requer JWT) |
| GET/POST | `/api/admin/auditoria/...` | Verificação da corrente de hashes, selos assinados e ações administrativas |
| GET | `/api/admin/reports/access-summary` | Resumo de acessos (requer JWT) |

## Segurança

Desde a introdução da criptografia híbrida, a API **só sobe com as chaves
configuradas**: gere-as com `java scripts/GerarChaves.java` e preencha
`SIAB_CHAVES_CIFRA`/`SIAB_CHAVES_ASSINATURA` (ver `.env.example`). Detalhes
de tudo que foi implementado, do protocolo do terminal e do deploy com TLS
híbrido em [`docs/seguranca.md`](docs/seguranca.md).
