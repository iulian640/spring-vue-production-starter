package com.example.ratelimit;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("RegistroCubetas — una cubeta por clave, con limpieza de las inactivas")
class RegistroCubetasTest {

    private static final Instant AHORA = Instant.parse("2026-07-09T10:00:00Z");

    @Test
    @DisplayName("crea una cubeta nueva a tope de capacidad la primera vez que ve una clave")
    void creaCubetaNuevaATopeDeCapacidadLaPrimeraVez() {
        RelojControlable reloj = new RelojControlable(AHORA);
        RegistroCubetas registro = new RegistroCubetas(reloj);

        assertThat(registro.intentaConsumir("ip:1.2.3.4", 2, 60)).isTrue();
        assertThat(registro.intentaConsumir("ip:1.2.3.4", 2, 60)).isTrue();
        assertThat(registro.intentaConsumir("ip:1.2.3.4", 2, 60)).isFalse();
    }

    @Test
    @DisplayName("claves distintas tienen presupuestos independientes")
    void clavesDistintasTienenPresupuestosIndependientes() {
        RelojControlable reloj = new RelojControlable(AHORA);
        RegistroCubetas registro = new RegistroCubetas(reloj);

        assertThat(registro.intentaConsumir("ip:1.1.1.1", 1, 60)).isTrue();
        assertThat(registro.intentaConsumir("ip:1.1.1.1", 1, 60)).isFalse();

        // Otra clave: presupuesto totalmente aparte, no afectado por la anterior.
        assertThat(registro.intentaConsumir("ip:2.2.2.2", 1, 60)).isTrue();
    }

    @Test
    @DisplayName("segundosHastaReintento() da 0 para una clave que nunca se ha visto")
    void segundosHastaReintentoParaClaveDesconocidaEsCero() {
        RelojControlable reloj = new RelojControlable(AHORA);
        RegistroCubetas registro = new RegistroCubetas(reloj);

        assertThat(registro.segundosHastaReintento("ip:nunca-visto")).isZero();
    }

    @Test
    @DisplayName("segundosHastaReintento() refleja la espera real de la cubeta agotada")
    void segundosHastaReintentoReflejaLaEsperaReal() {
        RelojControlable reloj = new RelojControlable(AHORA);
        RegistroCubetas registro = new RegistroCubetas(reloj);

        registro.intentaConsumir("ip:1.2.3.4", 1, 60);
        assertThat(registro.intentaConsumir("ip:1.2.3.4", 1, 60)).isFalse();
        assertThat(registro.segundosHastaReintento("ip:1.2.3.4")).isEqualTo(1);
    }

    @Test
    @DisplayName("barrido perezoso: retira las cubetas inactivas más de 10 minutos")
    void barridoPerezosoRetiraCubetasInactivas() {
        RelojControlable reloj = new RelojControlable(AHORA);
        // Intervalo de barrido de 1 en 1 para no depender de 256 peticiones reales.
        RegistroCubetas registro = new RegistroCubetas(reloj, 1, 10_000);

        registro.intentaConsumir("ip:vieja", 5, 60);
        assertThat(registro.tamano()).isEqualTo(1);

        reloj.avanza(Duration.ofMinutes(11));
        // Esta llamada crea una cubeta nueva Y dispara el barrido de la vieja.
        registro.intentaConsumir("ip:nueva", 5, 60);

        assertThat(registro.tamano()).isEqualTo(1);
        // La clave vieja ya no existe: se le da capacidad completa de nuevo (prueba indirecta).
        assertThat(registro.intentaConsumir("ip:vieja", 1, 60)).isTrue();
    }

    @Test
    @DisplayName("barrido perezoso: NO retira las cubetas usadas hace menos de 10 minutos")
    void barridoPerezosoNoRetiraCubetasRecientes() {
        RelojControlable reloj = new RelojControlable(AHORA);
        RegistroCubetas registro = new RegistroCubetas(reloj, 1, 10_000);

        registro.intentaConsumir("ip:reciente", 5, 60);
        reloj.avanza(Duration.ofMinutes(5));
        registro.intentaConsumir("ip:otra", 5, 60);

        assertThat(registro.tamano()).isEqualTo(2);
    }

    @Test
    @DisplayName("tope duro de memoria: con el registro lleno, las claves nuevas comparten la cubeta de desbordamiento (review H1)")
    void registroLlenoUsaCubetaDeDesbordamiento() {
        RelojControlable reloj = new RelojControlable(AHORA);
        // Tope minúsculo para el test: 2 cubetas y sin barridos de por medio.
        RegistroCubetas registro = new RegistroCubetas(reloj, 1_000_000, 2);

        registro.intentaConsumir("api:sub:uno", 5, 60);
        registro.intentaConsumir("api:sub:dos", 5, 60);
        // El registro está lleno: mil "subs" forjados más no crean mil cubetas.
        for (int i = 0; i < 1_000; i++) {
            registro.intentaConsumir("api:sub:forjado-" + i, 2, 60);
        }

        // Solo aparece UNA entrada extra (api:desbordamiento): memoria acotada.
        assertThat(registro.tamano()).isEqualTo(3);
        // Y las claves desbordadas COMPARTEN presupuesto: ya está agotado.
        assertThat(registro.intentaConsumir("api:sub:otro-forjado", 2, 60)).isFalse();
        // Mientras que una clave ya conocida conserva su cubeta propia.
        assertThat(registro.intentaConsumir("api:sub:uno", 5, 60)).isTrue();
    }
}
