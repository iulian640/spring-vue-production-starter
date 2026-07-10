package com.example.repository;

import com.example.domain.usuario.Sesion;
import com.example.domain.usuario.Usuario;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.autoconfigure.orm.jpa.TestEntityManager;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.test.context.ActiveProfiles;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.time.Duration;
import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;

/**
 * Las transiciones de estado de las sesiones (B4) contra PostgreSQL real: la
 * reclamación ATÓMICA del refresh es lo que sostiene la detección de robo por
 * reuso — si dos peticiones pudieran gastar el mismo token, la defensa entera
 * sería teatro. Un mock no puede probar el WHERE.
 */
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@ActiveProfiles("test")
@Testcontainers(disabledWithoutDocker = true)
class SesionRepositoryTest {

    @Container
    @ServiceConnection
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine");

    private static final Instant AHORA = Instant.parse("2026-07-10T21:00:00Z");

    @Autowired
    private SesionRepository sesiones;

    @Autowired
    private UsuarioRepository usuarios;

    @Autowired
    private TestEntityManager em;

    private UUID usuarioId;

    @BeforeEach
    void arranque() {
        usuarioId = usuarios.saveAndFlush(
                new Usuario("sesion-" + UUID.randomUUID() + "@example.com", "{noop}hash")).getId();
    }

    @Test
    @DisplayName("marcaUsadaSiIntacta gana UNA vez: la segunda reclamación del mismo refresh pierde")
    void reclamacionAtomica() {
        Sesion sesion = sesiones.saveAndFlush(sesionNueva("a1"));

        assertThat(sesiones.marcaUsadaSiIntacta(sesion.getId(), AHORA)).isEqualTo(1);
        assertThat(sesiones.marcaUsadaSiIntacta(sesion.getId(), AHORA.plusSeconds(1))).isZero();
    }

    @Test
    @DisplayName("una sesión revocada tampoco se puede reclamar")
    void revocadaNoSeReclama() {
        Sesion sesion = sesiones.saveAndFlush(sesionNueva("a2"));
        sesiones.revocaPorHash(sesion.getTokenHash(), AHORA);

        assertThat(sesiones.marcaUsadaSiIntacta(sesion.getId(), AHORA)).isZero();
    }

    @Test
    @DisplayName("revocaTodas apaga las sesiones vivas del usuario y respeta las de otros")
    void revocaTodasDelUsuario() {
        sesiones.saveAndFlush(sesionNueva("b1"));
        sesiones.saveAndFlush(sesionNueva("b2"));
        UUID otroUsuario = usuarios.saveAndFlush(
                new Usuario("otro-" + UUID.randomUUID() + "@example.com", "{noop}hash")).getId();
        Sesion ajena = sesiones.saveAndFlush(
                new Sesion(otroUsuario, hash("c1"), AHORA, AHORA.plus(Duration.ofDays(7))));

        assertThat(sesiones.revocaTodas(usuarioId, AHORA)).isEqualTo(2);
        // Repetirla no re-revoca nada (idempotente sobre las ya apagadas).
        assertThat(sesiones.revocaTodas(usuarioId, AHORA.plusSeconds(5))).isZero();

        em.clear();
        assertThat(sesiones.findById(ajena.getId()).orElseThrow().getRevocadaEn()).isNull();
    }

    @Test
    @DisplayName("la purga barre lo caducado y lo revocado viejo; respeta lo vivo y lo revocado reciente")
    void purga() {
        // Viva: se queda.
        Sesion viva = sesiones.saveAndFlush(sesionNueva("e1"));
        // Caducada: fuera.
        Sesion caducada = sesiones.saveAndFlush(new Sesion(usuarioId, hash("e2"),
                AHORA.minus(Duration.ofDays(9)), AHORA.minus(Duration.ofDays(2))));
        // Revocada hace mucho (más allá de la retención forense): fuera.
        Sesion revocadaVieja = sesiones.saveAndFlush(sesionNueva("e3"));
        sesiones.revocaPorHash(revocadaVieja.getTokenHash(), AHORA.minus(Duration.ofDays(40)));
        // Revocada ayer (dentro de la retención): se queda para el forense.
        Sesion revocadaReciente = sesiones.saveAndFlush(sesionNueva("e4"));
        sesiones.revocaPorHash(revocadaReciente.getTokenHash(), AHORA.minus(Duration.ofDays(1)));

        int borradas = sesiones.purga(AHORA, AHORA.minus(Duration.ofDays(30)));

        em.clear();
        assertThat(borradas).isEqualTo(2);
        assertThat(sesiones.findById(viva.getId())).isPresent();
        assertThat(sesiones.findById(caducada.getId())).isEmpty();
        assertThat(sesiones.findById(revocadaVieja.getId())).isEmpty();
        assertThat(sesiones.findById(revocadaReciente.getId())).isPresent();
    }

    @Test
    @DisplayName("el hash del refresh es UNIQUE: dos sesiones no pueden compartir token")
    void hashUnico() {
        sesiones.saveAndFlush(sesionNueva("d1"));

        assertThatExceptionOfType(DataIntegrityViolationException.class)
                .isThrownBy(() -> sesiones.saveAndFlush(sesionNueva("d1")));
    }

    private Sesion sesionNueva(String semilla) {
        return new Sesion(usuarioId, hash(semilla), AHORA, AHORA.plus(Duration.ofDays(7)));
    }

    /** 64 hex deterministas por semilla, como el SHA-256 real. */
    private static String hash(String semilla) {
        return (semilla + "0".repeat(64)).substring(0, 64).replaceAll("[^0-9a-f]", "e");
    }
}
