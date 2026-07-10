package com.example.ratelimit;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;

/**
 * Reloj de test que se puede avanzar manualmente, para probar la recarga de
 * la cubeta de tokens sin depender de {@code Thread.sleep}.
 */
final class RelojControlable extends Clock {

    private Instant instante;
    private final ZoneId zona;

    RelojControlable(Instant instanteInicial) {
        this(instanteInicial, ZoneId.of("Europe/Madrid"));
    }

    private RelojControlable(Instant instanteInicial, ZoneId zona) {
        this.instante = instanteInicial;
        this.zona = zona;
    }

    void avanza(Duration duracion) {
        this.instante = this.instante.plus(duracion);
    }

    @Override
    public ZoneId getZone() {
        return zona;
    }

    @Override
    public Clock withZone(ZoneId zone) {
        return new RelojControlable(instante, zone);
    }

    @Override
    public Instant instant() {
        return instante;
    }
}
