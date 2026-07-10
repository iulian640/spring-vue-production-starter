package com.example.service;

import com.example.domain.usuario.Sesion;
import com.example.domain.usuario.Usuario;
import com.example.repository.SesionRepository;
import com.example.repository.UsuarioRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.security.crypto.factory.PasswordEncoderFactories;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.jwt.JwtDecoder;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@DisplayName("AuthService — registro, login y ciclo de sesión (D13.4 + B4)")
class AuthServiceTest {

    private static final String EMAIL = "trabajador@example.com";
    private static final String PASSWORD = "una-contraseña-larga";
    private static final Instant AHORA = Instant.parse("2026-07-10T21:00:00Z");
    private static final Duration DURACION_REFRESH = Duration.ofDays(7);

    private UsuarioRepository repositorio;
    private SesionRepository sesiones;
    private PasswordEncoder passwordEncoder;
    private AuthService servicio;
    private JwtDecoder jwtDecoder;

    @BeforeEach
    void arranque() {
        repositorio = mock(UsuarioRepository.class);
        sesiones = mock(SesionRepository.class);
        passwordEncoder = PasswordEncoderFactories.createDelegatingPasswordEncoder();
        var claves = JwtTestSupport.claves();
        jwtDecoder = claves.decoder();
        servicio = new AuthService(repositorio, sesiones, passwordEncoder, claves.encoder(),
                Clock.fixed(AHORA, ZoneOffset.UTC), JwtTestSupport.DURACION, DURACION_REFRESH);
    }

    @Test
    @DisplayName("registro: guarda el email normalizado y el hash (nunca la contraseña en claro)")
    void registroGuardaHash() {
        when(repositorio.findByEmail(EMAIL)).thenReturn(Optional.empty());
        when(repositorio.saveAndFlush(any(Usuario.class))).thenAnswer(inv -> inv.getArgument(0));

        Usuario usuario = servicio.registra("  Trabajador@Example.com ", PASSWORD);

        assertThat(usuario.getEmail()).isEqualTo(EMAIL);
        assertThat(usuario.getPasswordHash()).doesNotContain(PASSWORD);
        assertThat(passwordEncoder.matches(PASSWORD, usuario.getPasswordHash())).isTrue();
        verify(repositorio).saveAndFlush(any(Usuario.class));
    }

    @Test
    @DisplayName("registro con email ya usado → EmailYaRegistradoException")
    void registroDuplicado() {
        when(repositorio.findByEmail(EMAIL)).thenReturn(Optional.of(usuarioExistente()));

        assertThatExceptionOfType(EmailYaRegistradoException.class)
                .isThrownBy(() -> servicio.registra(EMAIL, PASSWORD));
    }

    @Test
    @DisplayName("registro con contraseña corta → IllegalArgumentException")
    void registroPasswordCorta() {
        assertThatExceptionOfType(IllegalArgumentException.class)
                .isThrownBy(() -> servicio.registra(EMAIL, "corta"));
    }

    @Test
    @DisplayName("login correcto → JWT con el id como subject Y refresh opaco persistido HASHEADO")
    void loginCorrecto() {
        Usuario usuario = usuarioExistente();
        when(repositorio.findByEmail(EMAIL)).thenReturn(Optional.of(usuario));

        SesionEmitida sesion = servicio.login(EMAIL, PASSWORD);

        assertThat(sesion.token()).isNotBlank();
        var jwt = jwtDecoder.decode(sesion.token());
        assertThat(jwt.getSubject()).isEqualTo(usuario.getId().toString());
        assertThat(jwt.getClaimAsString("email")).isEqualTo(EMAIL);

        // El refresh que viaja al cliente NUNCA toca la BD en claro: se guarda
        // su SHA-256 (64 hex) y caduca según su propia duración, no la del JWT.
        assertThat(sesion.refreshToken()).isNotBlank();
        assertThat(sesion.refreshExpiraEn()).isEqualTo(AHORA.plus(DURACION_REFRESH));
        ArgumentCaptor<Sesion> guardada = ArgumentCaptor.forClass(Sesion.class);
        verify(sesiones).save(guardada.capture());
        assertThat(guardada.getValue().getTokenHash())
                .hasSize(64)
                .matches("[0-9a-f]{64}")
                .isNotEqualTo(sesion.refreshToken());
        assertThat(guardada.getValue().getUsuarioId()).isEqualTo(usuario.getId());
    }

    @Test
    @DisplayName("login con contraseña errónea o email inexistente → CredencialesInvalidasException (mismo error, sin filtrar cuál)")
    void loginIncorrecto() {
        when(repositorio.findByEmail(EMAIL)).thenReturn(Optional.of(usuarioExistente()));
        when(repositorio.findByEmail("nadie@example.com")).thenReturn(Optional.empty());

        assertThatExceptionOfType(CredencialesInvalidasException.class)
                .isThrownBy(() -> servicio.login(EMAIL, "otra-contraseña-mala"));
        assertThatExceptionOfType(CredencialesInvalidasException.class)
                .isThrownBy(() -> servicio.login("nadie@example.com", PASSWORD));
    }

    // --- Refresh: rotación y revocación (B4) ---

    @Test
    @DisplayName("refresca: gasta la sesión vieja (atómico) y emite access + refresh NUEVOS")
    void refrescaRota() {
        Usuario usuario = usuarioExistente();
        Sesion viva = sesionViva(usuario);
        when(sesiones.findByTokenHash(any())).thenReturn(Optional.of(viva));
        when(sesiones.marcaUsadaSiIntacta(eq(viva.getId()), any())).thenReturn(1);
        when(repositorio.findById(usuario.getId())).thenReturn(Optional.of(usuario));

        SesionEmitida nueva = servicio.refresca("refresh-que-viaja");

        assertThat(nueva.token()).isNotBlank();
        assertThat(nueva.refreshToken()).isNotBlank();
        verify(sesiones).marcaUsadaSiIntacta(eq(viva.getId()), any());
        verify(sesiones).save(any(Sesion.class)); // la sesión rotada
        verify(sesiones, never()).revocaTodas(any(), any());
    }

    @Test
    @DisplayName("SEGURIDAD: un refresh YA GASTADO (posible robo) revoca TODAS las sesiones del usuario")
    void refrescaReusadoRevocaTodo() {
        Usuario usuario = usuarioExistente();
        Sesion viva = sesionViva(usuario);
        when(sesiones.findByTokenHash(any())).thenReturn(Optional.of(viva));
        // La reclamación atómica pierde: alguien lo gastó antes.
        when(sesiones.marcaUsadaSiIntacta(eq(viva.getId()), any())).thenReturn(0);

        assertThatExceptionOfType(CredencialesInvalidasException.class)
                .isThrownBy(() -> servicio.refresca("refresh-robado"));

        verify(sesiones).revocaTodas(eq(usuario.getId()), any());
        verify(sesiones, never()).save(any(Sesion.class));
    }

    @Test
    @DisplayName("refresh desconocido, caducado o revocado → el MISMO 401 (sin oráculo)")
    void refrescaInvalido() {
        when(sesiones.findByTokenHash(any())).thenReturn(Optional.empty());
        assertThatExceptionOfType(CredencialesInvalidasException.class)
                .isThrownBy(() -> servicio.refresca("desconocido"));

        Usuario usuario = usuarioExistente();
        Sesion caducada = new Sesion(usuario.getId(), "b".repeat(64),
                AHORA.minus(Duration.ofDays(9)), AHORA.minus(Duration.ofDays(2)));
        when(sesiones.findByTokenHash(any())).thenReturn(Optional.of(caducada));
        assertThatExceptionOfType(CredencialesInvalidasException.class)
                .isThrownBy(() -> servicio.refresca("caducado"));

        // Revocada (logout previo): mismo 401, y tampoco escala a reuso.
        Sesion revocada = sesionViva(usuario);
        when(sesiones.findByTokenHash(any())).thenReturn(Optional.of(revocada));
        when(sesiones.revocaPorHash(any(), any())).thenReturn(1);
        servicio.cierraSesion("da-igual");
        // Simula el estado revocado que vería el refresca posterior.
        Sesion conRevocacion = org.mockito.Mockito.spy(revocada);
        org.mockito.Mockito.doReturn(AHORA.minusSeconds(60)).when(conRevocacion).getRevocadaEn();
        when(sesiones.findByTokenHash(any())).thenReturn(Optional.of(conRevocacion));
        assertThatExceptionOfType(CredencialesInvalidasException.class)
                .isThrownBy(() -> servicio.refresca("revocado"));

        // Ni caducado ni revocado son reuso: sin revocación en bloque ni sesión nueva.
        verify(sesiones, never()).revocaTodas(any(), any());
        verify(sesiones, never()).save(any(Sesion.class));
    }

    @Test
    @DisplayName("cierraSesion revoca por hash y es idempotente (un token desconocido no explota ni revela nada)")
    void cierraSesion() {
        when(sesiones.revocaPorHash(any(), any())).thenReturn(0);

        servicio.cierraSesion("da-igual-si-existe");

        verify(sesiones).revocaPorHash(any(), any());
    }

    private Sesion sesionViva(Usuario usuario) {
        return new Sesion(usuario.getId(), "a".repeat(64), AHORA.minusSeconds(600),
                AHORA.plus(Duration.ofDays(6)));
    }

    private Usuario usuarioExistente() {
        Usuario usuario = new Usuario(EMAIL, passwordEncoder.encode(PASSWORD));
        usuario.setId(UUID.randomUUID());
        return usuario;
    }
}
