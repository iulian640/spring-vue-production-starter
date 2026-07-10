package com.example.controller;

import com.example.config.SecurityConfig;
import com.example.service.CredencialesInvalidasException;
import com.example.service.CuentaService;
import com.example.service.PasswordIncorrectaException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.UUID;

import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(CuentaController.class)
@Import({SecurityConfig.class, GlobalExceptionHandler.class})
class CuentaControllerTest {

    private static final UUID USUARIO = UUID.randomUUID();

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private CuentaService cuentaService;

    @MockitoBean
    private JwtDecoder jwtDecoder;

    private static org.springframework.test.web.servlet.request.RequestPostProcessor comoUsuario() {
        return jwt().jwt(j -> j.subject(USUARIO.toString()).claim("email", "t@example.com"));
    }

    @Test
    @DisplayName("sin token → 401 (borrar una cuenta exige estar dentro de ella)")
    void sinToken() throws Exception {
        mockMvc.perform(delete("/api/v1/cuenta")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"password":"loQueSea123"}"""))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("DELETE con token y contraseña → 204 y el servicio recibe el id DEL TOKEN")
    void borraCuenta() throws Exception {
        mockMvc.perform(delete("/api/v1/cuenta").with(comoUsuario())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"password":"laBuena123"}"""))
                .andExpect(status().isNoContent());

        verify(cuentaService).borraCuenta(USUARIO, "laBuena123");
    }

    @Test
    @DisplayName("contraseña incorrecta → 403 RFC 7807 (NO 401: un 401 expulsaría la sesión en el frontend)")
    void passwordIncorrecta() throws Exception {
        doThrow(new PasswordIncorrectaException())
                .when(cuentaService).borraCuenta(eq(USUARIO), anyString());

        mockMvc.perform(delete("/api/v1/cuenta").with(comoUsuario())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"password":"laMala1234"}"""))
                .andExpect(status().isForbidden())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.status").value(403))
                .andExpect(jsonPath("$.detail").value("La contraseña no es correcta"));
    }

    @Test
    @DisplayName("body sin contraseña → 400 de validación, el servicio ni se llama")
    void sinPassword() throws Exception {
        mockMvc.perform(delete("/api/v1/cuenta").with(comoUsuario())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isBadRequest());

        verify(cuentaService, Mockito.never()).borraCuenta(Mockito.any(), anyString());
    }

    @Test
    @DisplayName("cuenta ya borrada con token todavía vivo → 401 (esta vez expulsar SÍ es lo correcto)")
    void cuentaYaBorrada() throws Exception {
        doThrow(new CredencialesInvalidasException())
                .when(cuentaService).borraCuenta(eq(USUARIO), anyString());

        mockMvc.perform(delete("/api/v1/cuenta").with(comoUsuario())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"password":"daIgual123"}"""))
                .andExpect(status().isUnauthorized());
    }
}
