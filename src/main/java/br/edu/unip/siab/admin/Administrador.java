package br.edu.unip.siab.admin;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

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

    @Column(name = "criado_em", nullable = false, updatable = false)
    private LocalDateTime criadoEm = LocalDateTime.now();
}
