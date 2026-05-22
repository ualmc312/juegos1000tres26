package com.juegos1000tres.juegos1000tres_backend.auth;

public record AuthUserResponse(Long id, String nombre, String email, String role) {

    public static AuthUserResponse from(AuthUser user, Long id) {
        return new AuthUserResponse(id, user.nombre(), user.email(), user.role().name());
    }
}
