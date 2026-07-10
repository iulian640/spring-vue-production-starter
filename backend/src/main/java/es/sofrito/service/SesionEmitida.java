package es.sofrito.service;

import java.time.Instant;

/**
 * Lo que recibe el cliente al abrir o refrescar sesión: el access JWT corto
 * (stateless, 15 min) y el refresh opaco largo (revocable, rota en cada uso).
 */
public record SesionEmitida(String token, Instant expiraEn,
                            String refreshToken, Instant refreshExpiraEn) {
}
