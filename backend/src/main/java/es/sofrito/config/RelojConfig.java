package es.sofrito.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Clock;
import java.time.ZoneId;

/**
 * Reloj inyectable: los servicios que dependen del "ahora" (sellado de la
 * libreta, D38) lo reciben por constructor y los tests lo fijan.
 * Zona peninsular; pendiente Canarias cuando el perfil tenga zona horaria.
 */
@Configuration
public class RelojConfig {

    @Bean
    public Clock reloj() {
        return Clock.system(ZoneId.of("Europe/Madrid"));
    }
}
