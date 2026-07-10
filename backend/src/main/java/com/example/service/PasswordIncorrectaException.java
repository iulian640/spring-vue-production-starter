package com.example.service;

/**
 * La contraseña de confirmación de una acción sensible (borrar la cuenta) no
 * coincide (→ 403). No es un 401: el usuario SIGUE autenticado — un 401 haría
 * que el frontend lo expulsara como sesión caducada por equivocarse al teclear.
 */
public class PasswordIncorrectaException extends RuntimeException {

    public PasswordIncorrectaException() {
        super("La contraseña no es correcta");
    }
}
