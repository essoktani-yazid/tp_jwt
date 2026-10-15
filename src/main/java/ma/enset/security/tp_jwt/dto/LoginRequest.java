package ma.enset.security.tp_jwt.dto;

public record LoginRequest(
        String email,
        String password
) {
}