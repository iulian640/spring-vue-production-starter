package com.example.service;

import com.example.domain.usuario.Usuario;
import com.example.repository.UsuarioRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@DisplayName("CuentaService — borrado de cuenta (RGPD art. 17)")
class CuentaServiceTest {

    private static final UUID USUARIO_ID = UUID.randomUUID();

    private UsuarioRepository usuarios;
    private PasswordEncoder passwordEncoder;
    private CuentaService servicio;
    private Usuario usuario;

    @BeforeEach
    void arranque() {
        usuarios = mock(UsuarioRepository.class);
        passwordEncoder = mock(PasswordEncoder.class);
        servicio = new CuentaService(usuarios, passwordEncoder);

        usuario = new Usuario("borrame@example.com", "$2a$10$hash");
        usuario.setId(USUARIO_ID);
    }

    @Test
    @DisplayName("con la contraseña correcta borra el usuario (el cascade de la BD arrastra el resto)")
    void borraConPasswordCorrecta() {
        when(usuarios.findById(USUARIO_ID)).thenReturn(Optional.of(usuario));
        when(passwordEncoder.matches("laBuena123", "$2a$10$hash")).thenReturn(true);

        assertThatCode(() -> servicio.borraCuenta(USUARIO_ID, "laBuena123"))
                .doesNotThrowAnyException();

        verify(usuarios).delete(usuario);
    }


    @Test
    @DisplayName("contraseña incorrecta → PasswordIncorrectaException y NO se borra nada")
    void passwordIncorrectaNoBorra() {
        when(usuarios.findById(USUARIO_ID)).thenReturn(Optional.of(usuario));
        when(passwordEncoder.matches("laMala", "$2a$10$hash")).thenReturn(false);

        assertThatThrownBy(() -> servicio.borraCuenta(USUARIO_ID, "laMala"))
                .isInstanceOf(PasswordIncorrectaException.class);

        verify(usuarios, never()).delete(any());
    }

    @Test
    @DisplayName("usuario ya inexistente (token vivo de cuenta borrada) → CredencialesInvalidas (401, expulsa)")
    void usuarioInexistente() {
        when(usuarios.findById(USUARIO_ID)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> servicio.borraCuenta(USUARIO_ID, "daIgual123"))
                .isInstanceOf(CredencialesInvalidasException.class);

        verify(usuarios, never()).delete(any());
    }
}
