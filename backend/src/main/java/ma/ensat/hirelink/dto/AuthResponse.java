package ma.ensat.hirelink.dto;

import ma.ensat.hirelink.model.Role;

public record AuthResponse(
        String accessToken,
        String tokenType,
        Long userId,
        String email,
        String fullName,
        Role role,
        boolean mustSetPassword,
        boolean emailVerified
) {
    public static AuthResponse of(String token, ma.ensat.hirelink.model.User u) {
        return new AuthResponse(
                token,
                "Bearer",
                u.getId(),
                u.getEmail(),
                u.getFullName(),
                u.getRole(),
                u.isMustSetPassword(),
                u.isEmailVerified()
        );
    }
}
