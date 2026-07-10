package com.example.dto;

import java.time.Instant;

/** Sesión abierta o refrescada: access JWT corto + refresh opaco revocable (B4). */
public record TokenResponse(String token, Instant expiraEn,
                            String refreshToken, Instant refreshExpiraEn) {
}
