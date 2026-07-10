package com.example.ratelimit;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DisplayName("CubetaTokens — token bucket con recarga proporcional al tiempo")
class CubetaTokensTest {

    private static final Instant AHORA = Instant.parse("2026-07-09T10:00:00Z");

    @Test
    @DisplayName("permite consumir hasta la capacidad y luego rechaza")
    void consumeHastaLaCapacidadYLuegoRechaza() {
        RelojControlable reloj = new RelojControlable(AHORA);
        CubetaTokens cubeta = new CubetaTokens(3, 60, reloj);

        assertThat(cubeta.intentaConsumir()).isTrue();
        assertThat(cubeta.intentaConsumir()).isTrue();
        assertThat(cubeta.intentaConsumir()).isTrue();
        assertThat(cubeta.intentaConsumir()).isFalse();
    }

    @Test
    @DisplayName("recarga proporcional al tiempo transcurrido, no de golpe")
    void recargaProporcionalAlTiempoTranscurrido() {
        RelojControlable reloj = new RelojControlable(AHORA);
        // 60 tokens/minuto = 1 token/segundo.
        CubetaTokens cubeta = new CubetaTokens(5, 60, reloj);
        for (int i = 0; i < 5; i++) {
            assertThat(cubeta.intentaConsumir()).isTrue();
        }
        assertThat(cubeta.intentaConsumir()).isFalse();

        reloj.avanza(Duration.ofSeconds(3));

        // Se han recargado ~3 tokens (no toda la capacidad de golpe).
        assertThat(cubeta.intentaConsumir()).isTrue();
        assertThat(cubeta.intentaConsumir()).isTrue();
        assertThat(cubeta.intentaConsumir()).isTrue();
        assertThat(cubeta.intentaConsumir()).isFalse();
    }

    @Test
    @DisplayName("la recarga nunca supera la capacidad (tope de ráfaga)")
    void laRecargaNuncaSuperaLaCapacidad() {
        RelojControlable reloj = new RelojControlable(AHORA);
        CubetaTokens cubeta = new CubetaTokens(2, 60, reloj);

        // Sin consumir nada, avanzamos mucho tiempo: no debe acumular de más.
        reloj.avanza(Duration.ofHours(1));

        assertThat(cubeta.intentaConsumir()).isTrue();
        assertThat(cubeta.intentaConsumir()).isTrue();
        assertThat(cubeta.intentaConsumir()).isFalse();
    }

    @Test
    @DisplayName("permite una ráfaga completa desde el arranque (la cubeta nace llena)")
    void permiteUnaRafagaCompletaDesdeElArranque() {
        RelojControlable reloj = new RelojControlable(AHORA);
        CubetaTokens cubeta = new CubetaTokens(10, 10, reloj);

        for (int i = 0; i < 10; i++) {
            assertThat(cubeta.intentaConsumir()).isTrue();
        }
        assertThat(cubeta.intentaConsumir()).isFalse();
    }

    @Test
    @DisplayName("segundosHastaProximoToken() da 0 cuando hay tokens disponibles")
    void segundosHastaProximoTokenEsCeroConTokensDisponibles() {
        RelojControlable reloj = new RelojControlable(AHORA);
        CubetaTokens cubeta = new CubetaTokens(2, 60, reloj);

        assertThat(cubeta.segundosHastaProximoToken()).isZero();
    }

    @Test
    @DisplayName("segundosHastaProximoToken() calcula el tiempo real de espera, redondeado hacia arriba")
    void segundosHastaProximoTokenCalculaLaEsperaRealRedondeadaHaciaArriba() {
        RelojControlable reloj = new RelojControlable(AHORA);
        // 60 tokens/minuto = 1 token/segundo -> agotada, hace falta 1s exacto.
        CubetaTokens cubeta = new CubetaTokens(1, 60, reloj);
        assertThat(cubeta.intentaConsumir()).isTrue();

        assertThat(cubeta.segundosHastaProximoToken()).isEqualTo(1);
    }

    @Test
    @DisplayName("dos cubetas independientes no se pisan entre sí")
    void dosCubetasIndependientesNoSePisan() {
        RelojControlable reloj = new RelojControlable(AHORA);
        CubetaTokens cubetaA = new CubetaTokens(1, 60, reloj);
        CubetaTokens cubetaB = new CubetaTokens(1, 60, reloj);

        assertThat(cubetaA.intentaConsumir()).isTrue();
        assertThat(cubetaA.intentaConsumir()).isFalse();
        assertThat(cubetaB.intentaConsumir()).isTrue();
    }

    @Test
    @DisplayName("capacidad no positiva → error de argumento")
    void capacidadNoPositivaLanzaError() {
        RelojControlable reloj = new RelojControlable(AHORA);
        assertThatThrownBy(() -> new CubetaTokens(0, 60, reloj))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("recarga por minuto no positiva → error de argumento")
    void recargaNoPositivaLanzaError() {
        RelojControlable reloj = new RelojControlable(AHORA);
        assertThatThrownBy(() -> new CubetaTokens(10, 0, reloj))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
