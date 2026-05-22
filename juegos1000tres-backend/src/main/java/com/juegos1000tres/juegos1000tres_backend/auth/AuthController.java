package com.juegos1000tres.juegos1000tres_backend.auth;

import java.time.Duration;
import java.util.UUID;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseCookie;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.juegos1000tres.juegos1000tres_backend.modelos.Usuario;
import com.juegos1000tres.juegos1000tres_backend.repositorios.UsuarioRepository;

import jakarta.servlet.http.HttpServletResponse;

@RestController
@RequestMapping("/auth")
public class AuthController {

    private static final String COOKIE_NAME = "auth_token";

    private final UsuarioRepository usuarioRepository;
    private final JwtService jwtService;

    public AuthController(UsuarioRepository usuarioRepository, JwtService jwtService) {
        this.usuarioRepository = usuarioRepository;
        this.jwtService = jwtService;
    }

    @PostMapping("/login")
    public ResponseEntity<AuthUserResponse> login(@RequestBody LoginRequest request,
                                                  HttpServletResponse response) {
        Usuario usuario = usuarioRepository
                .findByEmailAndPassword(request.email(), request.password())
                .orElse(null);

        if (usuario == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }

        AuthUser authUser = new AuthUser(usuario.getNombre(), usuario.getEmail(), AuthRole.USER);
        String token = jwtService.generateToken(authUser);
        ResponseCookie cookie = jwtService.buildCookie(COOKIE_NAME, token);
        response.addHeader("Set-Cookie", cookie.toString());

        return ResponseEntity.ok(AuthUserResponse.from(authUser, usuario.getId()));
    }

    @PostMapping("/guest")
    public ResponseEntity<AuthUserResponse> guest(HttpServletResponse response) {
        String guestId = "guest-" + UUID.randomUUID();
        AuthUser authUser = new AuthUser("Invitado", guestId, AuthRole.GUEST);
        String token = jwtService.generateToken(authUser);
        ResponseCookie cookie = jwtService.buildCookie(COOKIE_NAME, token);
        response.addHeader("Set-Cookie", cookie.toString());

        return ResponseEntity.ok(AuthUserResponse.from(authUser, null));
    }

    @PostMapping("/logout")
    public ResponseEntity<Void> logout(HttpServletResponse response) {
        ResponseCookie cookie = ResponseCookie.from(COOKIE_NAME, "")
                .httpOnly(true)
                .secure(false)
                .path("/")
                .maxAge(Duration.ZERO)
                .sameSite("Lax")
                .build();
        response.addHeader("Set-Cookie", cookie.toString());
        return ResponseEntity.ok().build();
    }

    @GetMapping("/me")
    public ResponseEntity<AuthUserResponse> me() {
        AuthUser current = jwtService.getCurrentUser();
        if (current == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }
        Long id = null;
        if (current.role() == AuthRole.USER) {
            id = usuarioRepository.findByEmailIgnoreCase(current.email())
                .map(Usuario::getId)
                .orElse(null);
        }
        return ResponseEntity.ok(AuthUserResponse.from(current, id));
    }
}
