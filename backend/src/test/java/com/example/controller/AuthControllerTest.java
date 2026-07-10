package com.example.controller;

import com.example.config.SecurityConfig;
import com.example.domain.usuario.Usuario;
import com.example.service.AuthService;
import com.example.service.CredencialesInvalidasException;
import com.example.service.EmailYaRegistradoException;
import com.example.service.SesionEmitida;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Instant;

import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(AuthController.class)
@Import({SecurityConfig.class, GlobalExceptionHandler.class})
class AuthControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private AuthService authService;

    @MockitoBean
    private JwtDecoder jwtDecoder;

    @Test
    @DisplayName("POST /auth/registro válido → 201 con el email (nunca la contraseña)")
    void registro() throws Exception {
        when(authService.registra(anyString(), anyString()))
                .thenReturn(new Usuario("trabajador@example.com", "hash"));

        mockMvc.perform(post("/api/v1/auth/registro")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"email":"trabajador@example.com","password":"una-contraseña-larga"}"""))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.email").value("trabajador@example.com"))
                .andExpect(jsonPath("$.password").doesNotExist());
    }

    @Test
    @DisplayName("registro con contraseña corta → 400 (validación)")
    void registroPasswordCorta() throws Exception {
        mockMvc.perform(post("/api/v1/auth/registro")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"email":"trabajador@example.com","password":"corta"}"""))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("registro con email duplicado → 409")
    void registroDuplicado() throws Exception {
        when(authService.registra(anyString(), anyString()))
                .thenThrow(new EmailYaRegistradoException());

        mockMvc.perform(post("/api/v1/auth/registro")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"email":"trabajador@example.com","password":"una-contraseña-larga"}"""))
                .andExpect(status().isConflict());
    }

    @Test
    @DisplayName("POST /auth/login correcto → access, expiración y refresh")
    void login() throws Exception {
        when(authService.login(anyString(), anyString())).thenReturn(sesionDePrueba());

        mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"email":"trabajador@example.com","password":"una-contraseña-larga"}"""))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.token").value("un.jwt.firmado"))
                .andExpect(jsonPath("$.refreshToken").value("refresh-opaco"))
                .andExpect(jsonPath("$.refreshExpiraEn").exists());
    }

    @Test
    @DisplayName("POST /auth/refresh sin access token → 200 con la sesión rotada (público, como el login)")
    void refresh() throws Exception {
        when(authService.refresca("refresh-opaco")).thenReturn(sesionDePrueba());

        mockMvc.perform(post("/api/v1/auth/refresh")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"refreshToken":"refresh-opaco"}"""))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.token").value("un.jwt.firmado"))
                .andExpect(jsonPath("$.refreshToken").value("refresh-opaco"))
                .andExpect(jsonPath("$.refreshExpiraEn").exists());
    }

    @Test
    @DisplayName("refresh inválido/reutilizado → 401 idéntico al de credenciales (sin oráculo)")
    void refreshInvalido() throws Exception {
        when(authService.refresca(anyString())).thenThrow(new CredencialesInvalidasException());

        mockMvc.perform(post("/api/v1/auth/refresh")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"refreshToken":"robado-o-caducado"}"""))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("refresh sin body válido → 400, el servicio ni se llama")
    void refreshSinToken() throws Exception {
        mockMvc.perform(post("/api/v1/auth/refresh")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isBadRequest());

        verify(authService, never()).refresca(anyString());
    }

    @Test
    @DisplayName("POST /auth/logout → 204 siempre (idempotente, revoca en servidor)")
    void logout() throws Exception {
        mockMvc.perform(post("/api/v1/auth/logout")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"refreshToken":"refresh-opaco"}"""))
                .andExpect(status().isNoContent());

        verify(authService).cierraSesion("refresh-opaco");
    }

    @Test
    @DisplayName("logout sin body válido → 400, el servicio ni se llama (mismo @Valid que el refresh)")
    void logoutSinToken() throws Exception {
        mockMvc.perform(post("/api/v1/auth/logout")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isBadRequest());

        verify(authService, never()).cierraSesion(anyString());
    }

    private static SesionEmitida sesionDePrueba() {
        return new SesionEmitida("un.jwt.firmado", Instant.parse("2026-07-09T12:00:00Z"),
                "refresh-opaco", Instant.parse("2026-07-16T12:00:00Z"));
    }

    @Test
    @DisplayName("login con credenciales malas → 401 con mensaje único (anti enumeración)")
    void loginIncorrecto() throws Exception {
        when(authService.login(anyString(), anyString()))
                .thenThrow(new CredencialesInvalidasException());

        mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"email":"trabajador@example.com","password":"mala"}"""))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.detail").value("Email o contraseña incorrectos"));
    }

    @Test
    @DisplayName("GET /me sin token → 401; con JWT → email del token")
    void me() throws Exception {
        mockMvc.perform(get("/api/v1/me"))
                .andExpect(status().isUnauthorized());

        mockMvc.perform(get("/api/v1/me")
                        .with(jwt().jwt(j -> j.claim("email", "trabajador@example.com"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.email").value("trabajador@example.com"));
    }
}
