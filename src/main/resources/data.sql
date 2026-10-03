-- Seção 5.6 do escopo: os 3 níveis de acesso do sistema.
-- INSERT IGNORE evita duplicar os registros a cada restart da aplicação.

INSERT IGNORE INTO niveis_acesso (id, nome, descricao) VALUES
    (1, 'Acesso Geral', 'Permissão básica de entrada.'),
    (2, 'Diretoria', 'Concedido apenas a diretores de divisões específicas.'),
    (3, 'Ministro', 'Acesso exclusivo ao ministro do Meio Ambiente (ou papel equivalente).');

-- Nenhum administrador é semeado aqui de propósito: uma senha padrão
-- versionada no repositório vale para qualquer instância que suba com este
-- data.sql (inclusive a de produção). O primeiro administrador é criado
-- pelo bootstrap: enquanto a tabela "administradores" estiver vazia,
-- POST /api/admin/administradores é liberado sem JWT (ver
-- AdministradorController e GET /api/auth/existe-administrador).
