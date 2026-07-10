package com.example.repository;

import com.example.domain.usuario.Sesion;
import com.example.domain.usuario.Usuario;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.autoconfigure.orm.jpa.TestEntityManager;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.test.context.ActiveProfiles;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.time.Duration;
import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * El borrado de cuenta (RGPD art. 17) contra PostgreSQL real: al borrar la fila
 * de usuarios, el ON DELETE CASCADE arrastra las sesiones (y cualquier tabla
 * hija que añadas siguiendo el mismo patrón), y el email queda libre para
 * registrarse de nuevo. Esto no lo puede cubrir un mock: es el contrato del
 * esquema.
 */
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@ActiveProfiles("test")
@Testcontainers(disabledWithoutDocker = true)
class BorradoCuentaCascadaTest {

    @Container
    @ServiceConnection
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine");

    private static final Instant AHORA = Instant.parse("2026-07-10T10:15:00Z");

    @Autowired
    private UsuarioRepository usuarios;

    @Autowired
    private TestEntityManager em;

    @Test
    @DisplayName("borrar el usuario arrastra sus sesiones (cascade) y libera el email")
    void borradoArrastraTodoYLiberaElEmail() {
        String email = "rgpd-" + UUID.randomUUID() + "@example.com";
        UUID usuarioId = usuarios.saveAndFlush(new Usuario(email, "{noop}hash")).getId();
        em.persistAndFlush(new Sesion(usuarioId, "f".repeat(64), AHORA, AHORA.plus(Duration.ofDays(7))));
        em.clear();

        usuarios.deleteById(usuarioId);
        usuarios.flush();
        em.clear();

        assertThat(usuarios.findById(usuarioId)).isEmpty();
        assertThat(cuenta("sesiones", usuarioId)).isZero();

        // El email vuelve a estar libre: registrarse de nuevo no choca con el UNIQUE.
        assertThat(usuarios.saveAndFlush(new Usuario(email, "{noop}otroHash")).getId())
                .isNotEqualTo(usuarioId);
    }

    private long cuenta(String tabla, UUID usuarioId) {
        return ((Number) em.getEntityManager()
                .createNativeQuery("SELECT count(*) FROM " + tabla + " WHERE usuario_id = :id")
                .setParameter("id", usuarioId)
                .getSingleResult()).longValue();
    }
}
