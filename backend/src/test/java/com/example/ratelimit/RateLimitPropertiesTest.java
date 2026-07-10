package com.example.ratelimit;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("RateLimitProperties — presupuestos por defecto cuando faltan en la configuración")
class RateLimitPropertiesTest {

    @Test
    @DisplayName("presupuestos nulos -> se sustituyen por los valores por defecto")
    void authYApiNulosSeSustituyenPorLosPresupuestosPorDefecto() {
        RateLimitProperties propiedades = new RateLimitProperties(true, false, null, null, null);

        assertThat(propiedades.auth().capacidad()).isEqualTo(30);
        assertThat(propiedades.auth().recargaPorMinuto()).isEqualTo(20);
        // El de refresh es más ancho que el de auth (B4): tráfico sostenido
        // legítimo de toda una plantilla tras una misma IP, ~4/hora por usuario.
        assertThat(propiedades.refresh().capacidad()).isEqualTo(60);
        assertThat(propiedades.refresh().recargaPorMinuto()).isEqualTo(40);
        assertThat(propiedades.api().capacidad()).isEqualTo(40);
        assertThat(propiedades.api().recargaPorMinuto()).isEqualTo(120);
    }

    @Test
    @DisplayName("presupuestos explícitos -> se respetan tal cual")
    void authYApiExplicitosSeRespetanTalCual() {
        RateLimitProperties.Presupuesto auth = new RateLimitProperties.Presupuesto(5, 5);
        RateLimitProperties.Presupuesto refresh = new RateLimitProperties.Presupuesto(9, 9);
        RateLimitProperties.Presupuesto api = new RateLimitProperties.Presupuesto(50, 200);

        RateLimitProperties propiedades = new RateLimitProperties(true, true, auth, refresh, api);

        assertThat(propiedades.auth()).isEqualTo(auth);
        assertThat(propiedades.refresh()).isEqualTo(refresh);
        assertThat(propiedades.api()).isEqualTo(api);
        assertThat(propiedades.habilitado()).isTrue();
        assertThat(propiedades.confiarEnProxy()).isTrue();
    }
}
