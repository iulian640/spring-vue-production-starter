package com.example.ratelimit;

import java.time.Clock;
import java.time.Instant;

/**
 * Cubeta de tokens (token bucket) thread-safe para un único cliente/ruta.
 *
 * <p>Cada petición admitida consume un token. La cubeta se recarga de forma
 * proporcional al tiempo transcurrido desde la última recarga (no de golpe
 * cada minuto), hasta el tope de la {@code capacidad} (la ráfaga máxima
 * permitida). El "ahora" viene de un {@link Clock} inyectado, para poder
 * testear la recarga sin depender de {@code Thread.sleep}.
 *
 * <p>Toda la mutación pasa por métodos {@code synchronized}: es la unidad
 * de concurrencia más pequeña posible (un cliente puede tener varias
 * peticiones simultáneas contra la misma cubeta).
 */
final class CubetaTokens {

    private static final double NANOS_POR_MINUTO = 60_000_000_000.0;

    private final long capacidad;
    private final double tokensPorNanosegundo;
    private final Clock reloj;

    private double tokensDisponibles;
    private long ultimaRecargaNanos;

    /**
     * @param capacidad        tope de tokens (ráfaga máxima); debe ser mayor que 0.
     * @param recargaPorMinuto tokens que se recargan cada minuto en régimen permanente.
     * @param reloj            fuente del "ahora" para calcular la recarga.
     */
    CubetaTokens(long capacidad, double recargaPorMinuto, Clock reloj) {
        if (capacidad <= 0) {
            throw new IllegalArgumentException("La capacidad de la cubeta debe ser mayor que 0");
        }
        if (recargaPorMinuto <= 0) {
            throw new IllegalArgumentException("La recarga por minuto debe ser mayor que 0");
        }
        this.capacidad = capacidad;
        this.tokensPorNanosegundo = recargaPorMinuto / NANOS_POR_MINUTO;
        this.reloj = reloj;
        this.tokensDisponibles = capacidad;
        this.ultimaRecargaNanos = nanosAhora();
    }

    /** Intenta consumir un token. Devuelve {@code true} si había uno disponible. */
    synchronized boolean intentaConsumir() {
        recarga();
        if (tokensDisponibles >= 1.0) {
            tokensDisponibles -= 1.0;
            return true;
        }
        return false;
    }

    /**
     * Segundos que faltan hasta que haya al menos un token disponible
     * (redondeado hacia arriba), para la cabecera {@code Retry-After}.
     * Devuelve 0 si ya hay un token disponible ahora mismo.
     */
    synchronized long segundosHastaProximoToken() {
        recarga();
        if (tokensDisponibles >= 1.0) {
            return 0L;
        }
        double tokensQueFaltan = 1.0 - tokensDisponibles;
        double nanosNecesarios = tokensQueFaltan / tokensPorNanosegundo;
        return (long) Math.ceil(nanosNecesarios / 1_000_000_000.0);
    }

    private void recarga() {
        long ahora = nanosAhora();
        long transcurridos = ahora - ultimaRecargaNanos;
        if (transcurridos > 0) {
            tokensDisponibles = Math.min(capacidad, tokensDisponibles + transcurridos * tokensPorNanosegundo);
            ultimaRecargaNanos = ahora;
        }
    }

    private long nanosAhora() {
        Instant instante = reloj.instant();
        return Math.addExact(Math.multiplyExact(instante.getEpochSecond(), 1_000_000_000L), instante.getNano());
    }
}
