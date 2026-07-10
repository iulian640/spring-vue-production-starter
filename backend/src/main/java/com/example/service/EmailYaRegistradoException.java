package com.example.service;

/** El email ya tiene cuenta (→ 409 en la API). */
public class EmailYaRegistradoException extends RuntimeException {

    public EmailYaRegistradoException() {
        super("Ya existe una cuenta con ese email");
    }
}
