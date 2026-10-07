package br.edu.unip.siab.terminal;

import br.edu.unip.siab.accesslevel.NivelAcesso;
import br.edu.unip.siab.crypto.Conversores;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.Length;

import java.time.LocalDateTime;

/**
 * Tabela "terminais" — cada quiosque /scan instalado numa porta do cofre.
 * <p>
 * Motivação (seção 1.1 do roteiro de segurança): sem identidade de
 * dispositivo, qualquer pessoa na internet podia mandar um arquivo de foto
 * direto para POST /api/recognition/scan, sem passar pela câmera — o que
 * anulava a verificação de vivacidade. Agora o terminal recebe do admin um
 * segredo próprio ({@link #chave}) e assina cada tentativa com ele (ver
 * {@link TerminalAutenticacaoService}).
 * <p>
 * O nível exigido pela porta também passa a ser do terminal, definido pelo
 * admin no cadastro — não mais escolhido por quem está diante do quiosque.
 */
@Entity
@Table(name = "terminais")
@Getter
@Setter
@NoArgsConstructor
public class Terminal {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String nome;

    /** Nível mínimo exigido pela porta onde o terminal está instalado. */
    @ManyToOne(fetch = FetchType.EAGER, optional = false)
    @JoinColumn(name = "nivel_acesso_id", nullable = false)
    private NivelAcesso nivelExigido;

    /**
     * Segredo HMAC-SHA256 (256 bits, Base64), cifrado em repouso.
     * <p>
     * {@code length = Length.LONG32} vale para todo {@code @Lob} do projeto:
     * sem ele o Hibernate 7 cria TINYTEXT/TINYBLOB (255 bytes) no MySQL, e o
     * valor cifrado com X25519 + ML-KEM-768 (~1,5 KB) não cabe ("Data too
     * long for column"). Com ele a coluna vira LONGTEXT/LONGBLOB, e o
     * {@code ddl-auto: update} amplia sozinho as colunas já existentes.
     */
    @Lob
    @Column(nullable = false, length = Length.LONG32)
    @Convert(converter = Conversores.ChaveTerminal.class)
    private String chave;

    @Column(nullable = false)
    private boolean ativo = true;

    @Column(name = "criado_em", nullable = false, updatable = false)
    private LocalDateTime criadoEm = LocalDateTime.now();

    @Column(name = "ultimo_uso_em")
    private LocalDateTime ultimoUsoEm;
}
