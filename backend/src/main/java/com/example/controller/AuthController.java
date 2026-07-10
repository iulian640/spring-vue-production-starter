package com.example.controller;

import com.example.dto.LoginRequest;
import com.example.dto.RefreshRequest;
import com.example.dto.RegistroRequest;
import com.example.dto.TokenResponse;
import com.example.dto.UsuarioResponse;
import com.example.service.AuthService;
import com.example.service.SesionEmitida;
import jakarta.validation.Valid;
import com.example.config.RequiereBaseDeDatos;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/**
 * Registro, login y ciclo de sesión: refresh rotativo y logout con revocación
 * (D13.4 + B4). Fuera del perfil `local` (sin BD no hay usuarios; ese perfil
 * es solo para consultar convenios).
 */
@RestController
@RequestMapping("/api/v1")
@RequiereBaseDeDatos
public class AuthController {

    private final AuthService auth;

    public AuthController(AuthService auth) {
        this.auth = auth;
    }

    @PostMapping("/auth/registro")
    @ResponseStatus(HttpStatus.CREATED)
    public UsuarioResponse registro(@Valid @RequestBody RegistroRequest peticion) {
        return new UsuarioResponse(auth.registra(peticion.email(), peticion.password()).getEmail());
    }

    @PostMapping("/auth/login")
    public TokenResponse login(@Valid @RequestBody LoginRequest peticion) {
        return aRespuesta(auth.login(peticion.email(), peticion.password()));
    }

    /**
     * Rota un refresh vivo por una sesión nueva (B4). Público como el login:
     * quien refresca no tiene (o ya no le vale) el access token.
     */
    @PostMapping("/auth/refresh")
    public TokenResponse refresh(@Valid @RequestBody RefreshRequest peticion) {
        return aRespuesta(auth.refresca(peticion.refreshToken()));
    }

    /** Logout real: revoca el refresh en el servidor. Idempotente, siempre 204. */
    @PostMapping("/auth/logout")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void logout(@Valid @RequestBody RefreshRequest peticion) {
        auth.cierraSesion(peticion.refreshToken());
    }

    private static TokenResponse aRespuesta(SesionEmitida sesion) {
        return new TokenResponse(sesion.token(), sesion.expiraEn(),
                sesion.refreshToken(), sesion.refreshExpiraEn());
    }

    /** Quién soy: valida el token y devuelve el email del usuario autenticado. */
    @GetMapping("/me")
    public UsuarioResponse me(@AuthenticationPrincipal Jwt jwt) {
        return new UsuarioResponse(jwt.getClaimAsString("email"));
    }
}
