package br.edu.unip.siab.admin;

import br.edu.unip.siab.admin.dto.AdministradorRequest;
import br.edu.unip.siab.auth.MfaNecessarioException;
import br.edu.unip.siab.auth.TotpService;
import jakarta.persistence.EntityNotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import java.util.OptionalLong;

/**
 * Módulo "auth" (seção 5.2 do escopo): gestão dos administradores com
 * acesso ao painel. A senha nunca é persistida em texto puro — é sempre
 * hasheada com o {@link PasswordEncoder} (BCrypt) já configurado em
 * SecurityConfig antes de ser salva.
 */
@Service
@RequiredArgsConstructor
public class AdministradorService {

    private final AdministradorRepository administradorRepository;
    private final PasswordEncoder passwordEncoder;
    private final TotpService totpService;

    /** Segredo e URI {@code otpauth://} para o admin cadastrar no app autenticador. */
    public record ConfiguracaoMfa(String segredo, String uri) {
    }

    public Administrador criar(AdministradorRequest request) {
        if (administradorRepository.existsByUsername(request.username())) {
            throw new UsernameJaCadastradoException(request.username());
        }

        Administrador administrador = new Administrador();
        administrador.setUsername(request.username());
        administrador.setSenha(passwordEncoder.encode(request.senha()));

        return administradorRepository.save(administrador);
    }

    /**
     * Usado tanto pelo endpoint público de bootstrap (GET
     * /api/auth/existe-administrador) quanto pelo próprio
     * {@link AdministradorController}, que dispensa JWT na criação apenas
     * enquanto isto for {@code false}.
     */
    public boolean existeAdministrador() {
        return administradorRepository.count() > 0;
    }

    /**
     * Criação do primeiro administrador (bootstrap, sem JWT). Sincronizado e
     * com a checagem "tabela vazia" feita aqui dentro: antes a checagem
     * ficava no controller, separada da gravação, e duas requisições
     * simultâneas podiam criar dois administradores "primeiros".
     */
    public synchronized Administrador criarPrimeiro(AdministradorRequest request) {
        if (existeAdministrador()) {
            throw new AutenticacaoNecessariaException();
        }
        return criar(request);
    }

    public boolean mfaAtivo(String username) {
        return buscar(username).isTotpAtivo();
    }

    /**
     * Confere o segundo fator no login. Não faz nada se o admin não tem MFA
     * ativo; senão exige um código válido e ainda não usado.
     */
    public void verificarSegundoFator(String username, String codigo) {
        Administrador admin = buscar(username);
        if (!admin.isTotpAtivo()) {
            return;
        }
        if (codigo == null || codigo.isBlank()) {
            throw new MfaNecessarioException("Informe o código do aplicativo autenticador.");
        }
        consumirCodigo(admin, codigo);
    }

    /** Gera (ou regera) o segredo; o MFA só passa a valer depois de {@link #ativarMfa}. */
    public ConfiguracaoMfa configurarMfa(String username) {
        Administrador admin = buscar(username);
        if (admin.isTotpAtivo()) {
            throw new IllegalArgumentException("MFA já está ativo. Desative antes de configurar de novo.");
        }
        String segredo = totpService.gerarSegredo();
        admin.setTotpSegredo(segredo);
        admin.setTotpUltimoPasso(null);
        administradorRepository.save(admin);
        return new ConfiguracaoMfa(segredo, totpService.uriDeConfiguracao(segredo, username));
    }

    public void ativarMfa(String username, String codigo) {
        Administrador admin = buscar(username);
        if (admin.getTotpSegredo() == null) {
            throw new IllegalArgumentException("Configure o MFA antes de ativar.");
        }
        consumirCodigo(admin, codigo);
        admin.setTotpAtivo(true);
        administradorRepository.save(admin);
    }

    public void desativarMfa(String username, String codigo) {
        Administrador admin = buscar(username);
        if (!admin.isTotpAtivo()) {
            return;
        }
        consumirCodigo(admin, codigo);
        admin.setTotpAtivo(false);
        admin.setTotpSegredo(null);
        admin.setTotpUltimoPasso(null);
        administradorRepository.save(admin);
    }

    private void consumirCodigo(Administrador admin, String codigo) {
        OptionalLong passo = totpService.verificar(admin.getTotpSegredo(), codigo);
        if (passo.isEmpty() || (admin.getTotpUltimoPasso() != null && passo.getAsLong() <= admin.getTotpUltimoPasso())) {
            throw new MfaNecessarioException("Código do autenticador inválido ou já usado.");
        }
        admin.setTotpUltimoPasso(passo.getAsLong());
        administradorRepository.save(admin);
    }

    private Administrador buscar(String username) {
        return administradorRepository.findByUsername(username)
                .orElseThrow(() -> new EntityNotFoundException("Administrador não encontrado: " + username));
    }
}
