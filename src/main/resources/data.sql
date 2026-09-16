-- Seção 5.6 do escopo: os 3 níveis de acesso do sistema.
-- INSERT IGNORE evita duplicar os registros a cada restart da aplicação.

INSERT IGNORE INTO niveis_acesso (id, nome, descricao) VALUES
    (1, 'Acesso Geral', 'Permissão básica de entrada.'),
    (2, 'Diretoria', 'Concedido apenas a diretores de divisões específicas.'),
    (3, 'Ministro', 'Acesso exclusivo ao ministro do Meio Ambiente (ou papel equivalente).');

-- Administrador inicial do painel (login em POST /api/auth/login), só para
-- o primeiro acesso em desenvolvimento. Senha em texto SOMENTE aqui, neste
-- seed de dev, para o grupo saber o que digitar no primeiro login:
--   username: admin
--   senha:    TrocarSenha123!
-- O hash abaixo foi gerado com o mesmo BCryptPasswordEncoder (força 10) que
-- o SecurityConfig usa em produção. Troque a senha pelo endpoint
-- POST /api/admin/administradores assim que possível.
INSERT IGNORE INTO administradores (id, username, senha, criado_em) VALUES
    (1, 'admin', '$2a$10$dewQrQnKO9N1dErUq7OsG.0G.i1B9zlZyXuiSf5yX4uxq6im.v.ra', CURRENT_TIMESTAMP);
