package br.edu.unip.siab.accesscontrol;

/**
 * Áreas protegidas do cofre e o nível de acesso mínimo exigido por cada uma
 * (seção 5.6 do escopo). Os ids batem com os 3 registros de
 * {@code niveis_acesso} semeados em data.sql: 1 = Acesso Geral,
 * 2 = Diretoria, 3 = Ministro.
 * <p>
 * A tela /scan informa em qual área o terminal está instalado e o
 * {@link AccessControlService} compara esse nível com o do usuário
 * reconhecido — um usuário de nível superior também entra nas áreas de
 * nível inferior.
 */
public enum AreaCofre {
    GERAL(1L),
    DIRETORIA(2L),
    MINISTRO(3L);

    private final Long nivelMinimoExigido;

    AreaCofre(Long nivelMinimoExigido) {
        this.nivelMinimoExigido = nivelMinimoExigido;
    }

    public Long getNivelMinimoExigido() {
        return nivelMinimoExigido;
    }

    /**
     * Converte o valor recebido na requisição (sem diferenciar maiúsculas de
     * minúsculas). Lança {@link IllegalArgumentException} — tratada como 400
     * pelo ApiExceptionHandler — para qualquer área desconhecida.
     */
    public static AreaCofre deValor(String valor) {
        if (valor == null || valor.isBlank()) {
            return GERAL;
        }
        for (AreaCofre area : values()) {
            if (area.name().equalsIgnoreCase(valor.trim())) {
                return area;
            }
        }
        throw new IllegalArgumentException(
                "Área inválida: '" + valor + "'. Valores aceitos: GERAL, DIRETORIA, MINISTRO.");
    }
}
