package br.edu.unip.siab.auth.dto;

import java.time.Instant;

/**
 * O token também vai no cookie HttpOnly {@code SIAB_TOKEN}; no corpo ele
 * continua disponível para Swagger, Postman e {@code requests.http}, que
 * usam o header Authorization. O front-end ignora este campo.
 */
public record LoginResponse(
        String token,
        String tokenType,
        Instant expiraEm
) {
    public static LoginResponse of(String token, Instant expiraEm) {
        return new LoginResponse(token, "Bearer", expiraEm);
    }
}
