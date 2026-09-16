package br.edu.unip.siab.auth.dto;

public record LoginResponse(
        String token,
        String tokenType
) {
    public static LoginResponse of(String token) {
        return new LoginResponse(token, "Bearer");
    }
}
