package com.example.service;

/**
 * Login fallido (→ 401). Mensaje único para email inexistente y contraseña
 * errónea: no se revela cuál de los dos falló (anti enumeración de usuarios).
 */
public class CredencialesInvalidasException extends RuntimeException {

    public CredencialesInvalidasException() {
        super("Email o contraseña incorrectos");
    }
}
