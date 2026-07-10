package com.example.service;

import com.example.domain.usuario.Usuario;
import com.example.repository.SesionRepository;
import com.example.repository.UsuarioRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.security.crypto.factory.PasswordEncoderFactories;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.time.Clock;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;

/**
 * El ciclo de sesiones (B4) con TRANSACCIONES REALES contra PostgreSQL. Los
 * mocks no pueden ver el bug que caza esto: la revocación en bloque del camino
 * de reuso lanzaba el 401 y el rollback de la propia transacción DESHACÍA la
 * revocación — el ladrón (y el legítimo) seguían dentro. Aquí el servicio corre
 * con su proxy @Transactional de verdad y los test methods SIN transacción
 * envolvente (commits reales, como en producción).
 */
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@ActiveProfiles("test")
@Testcontainers(disabledWithoutDocker = true)
@Import(AuthServiceSesionesIntegracionTest.Config.class)
@Transactional(propagation = Propagation.NOT_SUPPORTED)
class AuthServiceSesionesIntegracionTest {

    @Container
    @ServiceConnection
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine");

    private static final String PASSWORD = "una-contraseña-larga";

    @TestConfiguration
    static class Config {
        @Bean
        PasswordEncoder passwordEncoder() {
            return PasswordEncoderFactories.createDelegatingPasswordEncoder();
        }

        @Bean
        JwtEncoder jwtEncoder() {
            return JwtTestSupport.claves().encoder();
        }

        @Bean
        Clock reloj() {
            return Clock.systemUTC();
        }

        @Bean
        AuthService authService(UsuarioRepository usuarios, SesionRepository sesiones,
                                PasswordEncoder passwordEncoder, JwtEncoder jwtEncoder, Clock reloj) {
            return new AuthService(usuarios, sesiones, passwordEncoder, jwtEncoder, reloj,
                    JwtTestSupport.DURACION, java.time.Duration.ofDays(7));
        }
    }

    @Autowired
    private AuthService servicio;

    @Autowired
    private SesionRepository sesiones;

    @Test
    @DisplayName("SEGURIDAD: la revocación por reuso SOBREVIVE al 401 (no la deshace el rollback)")
    void revocacionPorReusoSobreviveAl401() {
        String email = "b4-" + UUID.randomUUID() + "@example.com";
        servicio.registra(email, PASSWORD);
        SesionEmitida sesion1 = servicio.login(email, PASSWORD);

        // Rotación normal: R1 se gasta y nace R2 (la sesión legítima).
        SesionEmitida sesion2 = servicio.refresca(sesion1.refreshToken());

        // Llega OTRA VEZ R1 (robo simulado): 401...
        assertThatExceptionOfType(CredencialesInvalidasException.class)
                .isThrownBy(() -> servicio.refresca(sesion1.refreshToken()));

        // ...y la revocación en bloque tiene que haberse COMMITEADO: R2 muerto.
        assertThatExceptionOfType(CredencialesInvalidasException.class)
                .isThrownBy(() -> servicio.refresca(sesion2.refreshToken()));
    }

    @Test
    @DisplayName("logout revoca de verdad: el refresh deja de rotar")
    void logoutRevoca() {
        String email = "b4-" + UUID.randomUUID() + "@example.com";
        Usuario usuario = servicio.registra(email, PASSWORD);
        SesionEmitida sesion = servicio.login(email, PASSWORD);

        servicio.cierraSesion(sesion.refreshToken());

        assertThatExceptionOfType(CredencialesInvalidasException.class)
                .isThrownBy(() -> servicio.refresca(sesion.refreshToken()));
        assertThat(sesiones.findAll().stream()
                .filter(s -> s.getUsuarioId().equals(usuario.getId()))
                .allMatch(s -> s.getRevocadaEn() != null)).isTrue();
    }
}
