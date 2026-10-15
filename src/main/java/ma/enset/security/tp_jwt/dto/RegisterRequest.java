package ma.enset.security.tp_jwt.dto;

public record RegisterRequest(
        String email,
        String password
) {
}