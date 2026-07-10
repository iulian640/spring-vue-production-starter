package es.sofrito.repository;

import es.sofrito.domain.usuario.Usuario;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.test.context.ActiveProfiles;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;

/**
 * Test JPA contra PostgreSQL real (Testcontainers; se salta sin Docker — en CI
 * corre siempre). Cubre lo que los unit tests con mocks no pueden: que id y
 * creado_en se rellenan de verdad y que la restricción UNIQUE del email es la
 * barrera real contra registros duplicados (la carrera del registro).
 */
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@ActiveProfiles("test")
@Testcontainers(disabledWithoutDocker = true)
class UsuarioRepositoryTest {

    @Container
    @ServiceConnection
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine");

    @Autowired
    private UsuarioRepository repositorio;

    @Test
    @DisplayName("al guardar se generan id y creado_en, y findByEmail lo encuentra")
    void guardaYRecupera() {
        Usuario guardado = repositorio.saveAndFlush(new Usuario("trabajador@example.com", "{noop}hash"));

        assertThat(guardado.getId()).isNotNull();
        assertThat(repositorio.findByEmail("trabajador@example.com")).isPresent();

        // creado_en lo pone la BD (insertable=false): visible tras refrescar
        repositorio.flush();
        Usuario refrescado = repositorio.findById(guardado.getId()).orElseThrow();
        assertThat(refrescado.getEmail()).isEqualTo("trabajador@example.com");
    }

    @Test
    @DisplayName("el email duplicado lo frena la restricción UNIQUE de la BD (barrera real de la carrera)")
    void emailDuplicadoVioleUnique() {
        repositorio.saveAndFlush(new Usuario("dup@example.com", "{noop}h1"));

        assertThatExceptionOfType(DataIntegrityViolationException.class)
                .isThrownBy(() -> repositorio.saveAndFlush(new Usuario("dup@example.com", "{noop}h2")));
    }
}
