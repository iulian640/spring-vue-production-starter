package com.example.service;

import com.example.config.RequiereBaseDeDatos;
import com.example.repository.SesionRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;

/**
 * Barrido diario de la tabla de sesiones (security review de B4): cada login y
 * cada refresh insertan una fila y sin purga la tabla crecería sin techo
 * (~96 filas/día por sesión activa con el access de 15 min). Se borra lo
 * caducado; lo revocado se conserva 30 días por si hay que mirar un incidente.
 */
@Component
@RequiereBaseDeDatos
public class SesionesPurga {

    private static final Logger log = LoggerFactory.getLogger(SesionesPurga.class);

    /** Cuánto se conservan las sesiones revocadas (forense de un posible robo). */
    static final Duration RETENCION_REVOCADAS = Duration.ofDays(30);

    private final SesionRepository sesiones;
    private final Clock reloj;

    public SesionesPurga(SesionRepository sesiones, Clock reloj) {
        this.sesiones = sesiones;
        this.reloj = reloj;
    }

    /** De madrugada, cuando nadie ficha. Cron configurable por si molesta. */
    @Scheduled(cron = "${app.sesiones.purga-cron:0 40 4 * * *}", zone = "Europe/Madrid")
    @Transactional
    public void purga() {
        Instant ahora = Instant.now(reloj);
        int borradas = sesiones.purga(ahora, ahora.minus(RETENCION_REVOCADAS));
        if (borradas > 0) {
            log.info("Purga de sesiones: {} filas caducadas/revocadas fuera", borradas);
        }
    }
}
