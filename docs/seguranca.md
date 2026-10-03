# Segurança do SIAB — o que está implementado e como operar

Este documento acompanha as recomendações de
`boas-praticas-seguranca.md` (thread de segurança do projeto) e descreve o
que foi implementado no back-end, na mesma ordem de prioridade.

> **Antes de subir a API pela primeira vez depois desta mudança:** gere as
> chaves (seção 5.1) e configure `SIAB_CHAVES_CIFRA` e
> `SIAB_CHAVES_ASSINATURA`. Sem elas a aplicação **não inicia** (falha de
> propósito, para nunca gravar biometria sem cifra).

## 1. Terminal autenticado e anti-replay em `/api/recognition/scan`

Antes, qualquer um na rede podia mandar fotos para o `/scan` e ler a
similaridade de volta (oráculo para ataques de força bruta com imagens).

### 1.1 Protocolo

1. O admin cadastra o terminal (quiosque) em `POST /api/admin/terminais`
   informando nome e nível exigido. A resposta traz uma **chave HMAC de 256
   bits, exibida uma única vez**. O banco guarda a chave cifrada (seção 5).
2. A cada tentativa, o terminal pede um desafio:
   `GET /api/recognition/desafio` com o header `X-Terminal-Id`. Resposta:
   `nonce` (uso único, vale 60 s), nível exigido e `exigePin`.
3. O terminal assina com HMAC-SHA256 a mensagem canônica:

   ```
   SIAB-SCAN-v1
   <terminalId>
   <nonce>
   <timestampMs>
   <sha256hex(frame1)>,<sha256hex(frame2)>,...
   <pin ou vazio>
   ```

   e envia `POST /api/recognition/scan` (multipart `imagens` + `pin`
   opcional) com os headers `X-Terminal-Id`, `X-Desafio`, `X-Timestamp` e
   `X-Assinatura` (base64).
4. O servidor recusa com 401 se: o terminal não existe ou foi revogado, o
   nonce é desconhecido, expirou ou já foi usado (replay), o relógio
   diverge mais de 60 s, ou a assinatura não confere (frames ou PIN
   trocados no caminho).

**Por que HMAC e não ML-DSA no terminal:** o navegador (WebCrypto) ainda
não implementa ML-DSA, e criptografia simétrica com chave de 256 bits já é
resistente a computadores quânticos (Grover só reduz a segurança efetiva
para 128 bits). O pós-quântico entra onde há chave pública: TLS (seção 4),
cifra em repouso (seção 5) e selos da auditoria (seção 6).

O **nível exigido vem do terminal cadastrado**, não de um parâmetro que o
cliente escolhe. Um quiosque da porta "Geral" não consegue pedir acesso de
"Ministro".

### 1.2 Resposta sem similaridade

A resposta do `/scan` agora é só `{acessoConcedido, usuario, mensagem}`.
`usuario` (nome e nível) só aparece quando o acesso é concedido, e qualquer
negação devolve a mesma mensagem genérica, "Acesso negado.". O motivo real
(rosto desconhecido, nível insuficiente, PIN errado, liveness) e a
similaridade ficam só no log de auditoria, visível ao admin.

### 1.3 Testando sem o front-end

```bash
java scripts/ScanAssinado.java http://localhost:8080 <terminalId> <chave> frame1.jpg frame2.jpg frame3.jpg
java scripts/ScanAssinado.java http://localhost:8080 <terminalId> <chave> --pin 1234 frame*.jpg
```

O Postman/REST Client não conseguem mais chamar o `/scan` diretamente,
porque a assinatura depende do nonce e do hash dos frames.

### 1.4 Validação do upload

`ValidadorDeImagem` confere os bytes mágicos (só JPEG e PNG) e as dimensões
pelo cabeçalho, antes de decodificar (máx. 4096×4096), contra "bombas de
descompressão". No máximo 12 frames por tentativa.

## 2. Rate limiting e bloqueio de login

- Janela fixa de 1 minuto por IP: 10 tentativas de login e 30 de
  reconhecimento (`siab.rate-limit.*`). Acima disso, 429 com `Retry-After`.
- Bloqueio progressivo por usuário: depois de 5 senhas erradas, 30 s de
  bloqueio, dobrando a cada nova falha, até 15 min.
- Atrás de um proxy reverso, use `FORWARD_HEADERS_STRATEGY=native` para o
  limite usar o IP real do cliente. **Sem proxy, deixe `none`**, senão
  qualquer cliente forja `X-Forwarded-For` e escapa do limite.

Os contadores ficam em memória: com mais de uma instância da API, cada uma
conta separado (aceitável para o escopo do projeto).

## 3. Configuração de produção

- **Perfil `prod`** (`application-prod.yml`, ativado pelo `Dockerfile`):
  Swagger e `/v3/api-docs` desligados, log em INFO.
- **Respostas de erro** sem stack trace nem mensagem interna.
- **CORS** restrito às origens de `CORS_ALLOWED_ORIGINS`, com lista
  explícita de headers. Preflight de outra origem recebe 403.
- **Headers:** `X-Frame-Options: DENY`, `Referrer-Policy: no-referrer`,
  `X-Content-Type-Options: nosniff`.
- **Container** roda como usuário sem privilégios (`siab`, uid 10001).
- **BCrypt** com custo 12.

### 3.1 MySQL com `VERIFY_IDENTITY`

`REQUIRED` (padrão) cifra a conexão mas não confere o certificado, então
um intermediário consegue se passar pelo banco. Com `VERIFY_IDENTITY` o
driver valida a CA e o nome do host:

1. Baixe o `ca.pem` do serviço no console do Aiven.
2. Converta para um truststore:
   `keytool -importcert -alias aiven -file ca.pem -keystore aiven-ca.jks -storepass changeit -noprompt`
3. Monte o arquivo no container (ex.: `/certs/aiven-ca.jks`) e configure:

   ```
   DB_SSL_MODE=VERIFY_IDENTITY
   DB_SSL_EXTRA=&trustCertificateKeyStoreUrl=file:/certs/aiven-ca.jks&trustCertificateKeyStorePassword=changeit
   ```

O padrão continua `REQUIRED` para não quebrar quem ainda não fez isso.

## 4. TLS híbrido (X25519 + ML-KEM-768) no proxy reverso

A troca de chaves `X25519MLKEM768` protege o tráfego contra "colher agora,
decifrar depois". Ela é feita no proxy, que fica na frente da API e do
front-end:

- **`deploy/Caddyfile`** (recomendado; Caddy ≥ 2.10): TLS 1.3 só, curvas
  `x25519mlkem768` e `x25519`, HSTS, `/api/*` para o back-end e o resto
  para o front-end. Variáveis: `SIAB_DOMINIO`, `SIAB_BACKEND`,
  `SIAB_FRONTEND`.
- **`deploy/nginx.conf`**: alternativa; exige nginx compilado contra
  OpenSSL ≥ 3.5.
- **`deploy/verificar-tls-hibrido.go`**: cliente que oferece **só**
  `X25519MLKEM768` e mostra se o servidor aceitou
  (`go run deploy/verificar-tls-hibrido.go https://seu-dominio`).

Validado com Caddy 2.10.2: cliente só-PQC conecta, cliente X25519 cai no
clássico, cliente P-256 é recusado. Navegadores atuais (Chrome, Firefox,
Edge, Safari 26) já negociam `X25519MLKEM768` sozinhos.

Com o proxy na frente: `FORWARD_HEADERS_STRATEGY=native`,
`COOKIE_SECURE=true` e `CORS_ALLOWED_ORIGINS=https://seu-dominio`.

## 5. Biometria cifrada em repouso (híbrido clássico + PQC)

Os vetores LBPH (`face_embeddings.vetor`), as fotos de referência
(`face_embedding_imagens.imagem`), as chaves dos terminais e os segredos
TOTP são cifrados antes de ir para o banco (`crypto/`).

Esquema por registro (`CifraHibridaService`):

1. Encapsulamento duplo: X25519 (DHKEM) **e** ML-KEM-768 (JDK 25, JEP 496).
2. Os dois segredos entram juntos num HKDF-SHA256 (JEP 510), com os dois
   textos cifrados no `info` (combinador no estilo X-Wing). Quebrar só um
   dos algoritmos não revela a chave.
3. AES-256-GCM com IV aleatório e o nome da coluna como AAD (um valor
   copiado para outra coluna não decifra).

Formato: `SIAB | versão | ctX25519 | ctMLKEM | IV | cifrado+tag`. Colunas
de texto recebem o prefixo `enc:v1:`.

### 5.1 Gerando as chaves

```bash
java scripts/GerarChaves.java
```

Imprime `SIAB_CHAVES_CIFRA=...` e `SIAB_CHAVES_ASSINATURA=...`. Guarde as
duas num cofre de segredos (variáveis de ambiente da VM, secret do
GitHub, etc.), **nunca no repositório**. Sem `SIAB_CHAVES_CIFRA` a
biometria já cadastrada fica ilegível: faça backup.

Em testes, `siab.crypto.chaves-efemeras=true` gera chaves descartáveis a
cada execução.

### 5.2 Migração dos dados antigos

Na inicialização, `MigracaoCifraBiometrica` cifra as linhas que ainda
estão em texto puro. É idempotente e os conversores leem os dois formatos,
então não há janela de indisponibilidade.

### 5.3 Exclusão (LGPD)

`DELETE /api/admin/usuarios/{id}` apaga fotos e vetores do usuário. Os
logs de acesso dele são mantidos (trilha de auditoria), mas desvinculados:
aparecem como "Usuário excluído (#id)".

## 6. Auditoria encadeada e assinada

- Cada registro de `logs_acesso` guarda `hash = SHA-256(hashAnterior + "\n"
  + conteúdoCanônico)`. Alterar, apagar ou inserir uma linha no meio
  quebra a corrente a partir dali.
- Ações administrativas (login, criação de usuário/admin/terminal,
  cadastro de rosto, visualização de foto, exportação de relatório, MFA)
  vão para `acoes_administrativas`, com a mesma corrente.
- **Selos:** de hora em hora, a cada PDF exportado e sob demanda
  (`POST /api/admin/auditoria/selar`), o último hash das duas correntes é
  assinado com **Ed25519 + ML-DSA-65** (JEP 497). Ambas as assinaturas
  precisam conferir. Isso pega até quem recalcula a corrente inteira
  depois de adulterar, porque não tem a chave privada.
- `GET /api/admin/auditoria/verificacao` refaz tudo e lista os problemas.
- `GET /api/admin/auditoria/chaves-publicas` expõe as chaves públicas, para
  verificação independente. O rodapé do PDF traz o hash do selo e a
  impressão digital das chaves.

Linhas gravadas antes desta mudança aparecem como "legadas" no relatório
(não têm hash) e não contam como adulteração.

## 7. Liveness multi-frame, MFA do admin e PIN do nível Ministro

### 7.1 Piscada

O terminal envia vários frames (mín. 3). `DetectorDePiscada` usa o
`haarcascade_eye.xml` em cada rosto e exige o padrão aberto → fechado →
aberto. O teste de nitidez (variância do Laplaciano) continua valendo, e o
reconhecimento usa o frame mais nítido. `LIVENESS_EXIGIR_PISCADA=false`
desliga a exigência de piscada (só para testes).

### 7.2 MFA (TOTP) do administrador

- `POST /api/admin/mfa/configurar` devolve o segredo e a URI `otpauth://`
  (para QR code em qualquer app autenticador).
- `POST /api/admin/mfa/ativar {codigo}` confirma e liga.
- Com MFA ligado, o login exige `codigoMfa`; sem ele, a resposta é 401 com
  `mfaNecessario: true`. Um mesmo código não é aceito duas vezes.
- `DELETE /api/admin/mfa {codigo}` desliga.

### 7.3 PIN do nível Ministro

Usuários podem ter um PIN de 4 a 8 dígitos (guardado com BCrypt). Em
terminais de nível 3 (`siab.pin.nivel-minimo-que-exige`) o acesso só é
concedido com rosto **e** PIN. Cinco PINs errados bloqueiam o PIN daquele
usuário por 15 min.

## 8. Sessão do painel: cookie HttpOnly e CSRF

- O login grava o token no cookie `SIAB_TOKEN` (`HttpOnly`, `Secure`,
  `SameSite=Strict`, `Path=/api`). JavaScript não consegue lê-lo, então um
  XSS não rouba a sessão. O token ainda volta no corpo, para scripts e
  Swagger usarem `Authorization: Bearer`.
- Token com 30 min de validade, `iss`/`aud`/`jti`, e revogado no
  `POST /api/auth/logout`.
- **CSRF:** requisições que mudam estado e usam o cookie (sem `Bearer`)
  precisam do header `X-XSRF-TOKEN`, obtido em `GET /api/auth/csrf`.
- CSP e demais headers do front-end ficam no `next.config.ts` do
  `siab-frontend`.

## Limitações conhecidas

- Rate limit, nonces e revogação de token ficam em memória: reiniciar a API
  zera tudo, e várias instâncias não compartilham estado.
- O HMAC do terminal no navegador é tão seguro quanto o computador do
  quiosque. A chave é importada como não extraível no WebCrypto, mas quem
  controla a máquina pode usá-la enquanto estiver lá.
- Liveness por piscada é resistente a foto impressa, mas não a vídeo
  reproduzido numa tela. Defesas mais fortes (profundidade, IR, desafio
  aleatório de movimento) ficam fora do escopo.
- Os thresholds de reconhecimento e liveness continuam sendo chutes
  iniciais até a calibração com dados reais (ver `CLAUDE.md`).
