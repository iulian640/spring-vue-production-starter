package es.sofrito.controller;

import org.springframework.security.oauth2.jwt.Jwt;

import java.util.UUID;

/**
 * Extrae el id del usuario del token. Único punto para todos los controllers
 * (review M3): identidad rara en un token válido — subject nulo o que no es
 * UUID — es 401 genérico, sin eco del valor.
 */
final class UsuarioAutenticado {

    private UsuarioAutenticado() {
    }

    static UUID id(Jwt jwt) {
        try {
            return UUID.fromString(jwt.getSubject());
        } catch (IllegalArgumentException | NullPointerException e) {
            throw new TokenInvalidoException();
        }
    }
}
