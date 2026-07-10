package com.example.controller;

/** 401 genérico: la identidad del token no es utilizable. Sin detalles al cliente. */
public class TokenInvalidoException extends RuntimeException {

    public TokenInvalidoException() {
        super("Token inválido");
    }
}
