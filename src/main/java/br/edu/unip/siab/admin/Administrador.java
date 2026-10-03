package br.edu.unip.siab.admin;

import br.edu.unip.siab.crypto.Conversores;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.ColumnDefault;

import java.time.LocalDateTime;

/**
 * Tabela "administradores".
 * Representa um operador com acesso ao painel administrativo (módulo
 * "auth", seção 5.2 do escopo). Substitui o usuário fixo que antes vinha
 * de variável de ambiente em SecurityConfig — a senha é sempre armazenada
 * como hash BCrypt (nunca em texto puro), gerado pelo PasswordEncoder já
 * configurado no projeto.
 */
@Entity
@Table(name = "administradores")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class Administrador {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true)
    private String username;

    @Column(nullable = false)
    private String senha; // hash BCrypt - nunca texto puro

    /**
     * Segredo TOTP (Base32) do segundo fator, cifrado em repouso. Gravado em
     * POST /api/admin/mfa/configurar, mas só passa a valer depois que o admin
     * confirma um código em /ativar ({@link #totpAtivo}).
     */
    @Lob
    @Column(name = "totp_segredo")
    @Convert(converter = Conversores.SegredoTotp.class)
    private String totpSegredo;

    // DEFAULT no DDL: o INSERT do data.sql (e linhas já existentes no MySQL)
    // não informam esta coluna.
    @Column(name = "totp_ativo", nullable = false)
    @ColumnDefault("false")
    private boolean totpAtivo = false;

    /** Último passo de 30 s aceito — o mesmo código não serve duas vezes. */
    @Column(name = "totp_ultimo_passo")
    private Long totpUltimoPasso;

    @Column(name = "criado_em", nullable = false, updatable = false)
    private LocalDateTime criadoEm = LocalDateTime.now();
}
