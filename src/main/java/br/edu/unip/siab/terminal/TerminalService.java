package br.edu.unip.siab.terminal;

import br.edu.unip.siab.accesslevel.NivelAcesso;
import br.edu.unip.siab.accesslevel.NivelAcessoRepository;
import br.edu.unip.siab.terminal.dto.TerminalRequest;
import jakarta.persistence.EntityNotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.security.SecureRandom;
import java.util.Base64;
import java.util.List;

/**
 * Cadastro e revogação dos terminais do cofre (painel administrativo).
 */
@Service
@RequiredArgsConstructor
public class TerminalService {

    private final TerminalRepository terminalRepository;
    private final NivelAcessoRepository nivelAcessoRepository;
    private final SecureRandom aleatorio = new SecureRandom();

    public record TerminalCriado(Terminal terminal, String chave) {
    }

    public TerminalCriado criar(TerminalRequest request) {
        NivelAcesso nivel = nivelAcessoRepository.findById(request.nivelExigidoId())
                .orElseThrow(() -> new EntityNotFoundException("Nível de acesso não encontrado: " + request.nivelExigidoId()));

        byte[] segredo = new byte[32]; // 256 bits
        aleatorio.nextBytes(segredo);
        String chave = Base64.getEncoder().encodeToString(segredo);

        Terminal terminal = new Terminal();
        terminal.setNome(request.nome());
        terminal.setNivelExigido(nivel);
        terminal.setChave(chave);
        return new TerminalCriado(terminalRepository.save(terminal), chave);
    }

    public List<Terminal> listar() {
        return terminalRepository.findAllByOrderByIdAsc();
    }

    /** Revoga para sempre: o terminal precisa ser recadastrado (chave nova). */
    public Terminal revogar(Long id) {
        Terminal terminal = terminalRepository.findById(id)
                .orElseThrow(() -> new EntityNotFoundException("Terminal não encontrado: " + id));
        terminal.setAtivo(false);
        return terminalRepository.save(terminal);
    }
}
