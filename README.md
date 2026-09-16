# SIAB — Back-end (Java / Spring Boot)

API do Sistema de Identificação e Autenticação Biométrica, projeto de APS
(PIVC — UNIP). A estrutura de pacotes reflete exatamente os módulos
descritos na seção 5.2 do documento de escopo do projeto.

> **Nota de versão:** o documento de escopo menciona "Spring Boot 3", mas a
> linha 3.x saiu de suporte (EOL) em 30/06/2026. Este esqueleto usa
> **Spring Boot 4.1.1** (Java 25 LTS), a versão atualmente suportada. Vale
> atualizar a seção 4.1 do escopo para refletir isso.

## Estrutura de pacotes

```
br.edu.unip.siab
├── config/            Segurança (JWT), CORS, OpenAPI/Swagger
├── auth/               Módulo "auth" — login administrativo via JWT
├── user/               Módulo "user-management" — CRUD de usuários
├── accesslevel/        Os 3 níveis de acesso (niveis_acesso)
├── pipeline/
│   ├── acquisition/    Fase 1 — controllers de /enroll e /scan
│   ├── preprocessing/  Fase 2 — escala de cinza, equalização
│   ├── segmentation/   Fase 3 — detecção facial (Haar Cascade)
│   ├── feature/        Fase 4 — extração de características (PLACEHOLDER)
│   ├── recognition/     Fase 5 — comparação por similaridade
│   ├── liveness/        Anti-spoofing (PLACEHOLDER)
│   └── PipelineOrchestratorService.java  — amarra as 5 fases (seção 5.5)
├── accesscontrol/       Módulo "access-control" — regra dos 3 níveis
├── auditlog/            Módulo "audit-log" — logs_acesso
└── reporting/            Módulo "reporting" — resumo/relatórios
```

## O que já funciona vs. o que precisa ser implementado

✅ **Funcionando (esqueleto compilável e navegável):**
- CRUD de usuários e níveis de acesso
- Login administrativo com JWT
- Estrutura completa dos endpoints REST (`/api/enrollment`, `/api/recognition/scan`, `/api/admin/**`)
- Orquestração das 5 fases, na ordem correta, registrando logs de auditoria
- Swagger UI em `/swagger-ui.html` (assim que a app subir)

🚧 **TODO — trabalho técnico central do grupo (procure "TODO" no código):**
1. **`SegmentationService`**: baixar o `haarcascade_frontalface_default.xml` (ver `src/main/resources/haarcascades/LEIA-ME.txt`)
2. **`FeatureExtractionService`**: substituir o placeholder por LBPH real ou por um embedding via ONNX Runtime — **esta é a parte mais importante do projeto tecnicamente**
3. **`RecognitionService`**: calibrar o `threshold` de decisão com dados reais
4. **`LivenessService`**: implementar a técnica de anti-spoofing escolhida (piscar de olhos ou análise textural)
5. **`ReportController`**: gerar PDF real (ex.: com OpenPDF/PDFBox)

## Como rodar localmente (fora de container, para desenvolvimento)

1. Copie `.env.example` para `.env` e preencha com os dados do Aiven
2. Exporte as variáveis (ou configure na sua IDE) e rode:
   ```bash
   mvn spring-boot:run
   ```
3. Acesse `http://localhost:8080/swagger-ui.html`

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
| POST | `/api/auth/login` | Login do painel admin, retorna JWT |
| GET/POST/PUT/DELETE | `/api/admin/usuarios` | CRUD de usuários (requer JWT) |
| POST | `/api/enrollment` | Cadastra o rosto de um usuário (multipart: `usuarioId`, `imagem`) |
| POST | `/api/recognition/scan` | Roda o pipeline completo e decide o acesso (multipart: `imagem`) |
| GET | `/api/admin/logs` | Lista o histórico de tentativas (requer JWT) |
| GET | `/api/admin/reports/access-summary` | Resumo de acessos (requer JWT) |
