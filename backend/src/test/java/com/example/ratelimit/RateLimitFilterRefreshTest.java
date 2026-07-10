package com.example.ratelimit;

import com.example.config.RelojConfig;
import com.example.config.SecurityConfig;
import com.example.controller.AuthController;
import com.example.controller.GlobalExceptionHandler;
import com.example.service.AuthService;
import com.example.service.CredencialesInvalidasException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * El bucket PROPIO de {@code /auth/refresh} (security review): con el
 * access de 15 min, el refresh es tráfico sostenido de toda la plantilla tras
 * una misma IP — metido en el bucket estricto de login, los legítimos se
 * auto-bloquearían el login. Aquí se prueba que ambos cubos son independientes.
 */
@WebMvcTest(AuthController.class)
@Import({SecurityConfig.class, GlobalExceptionHandler.class, RateLimitConfig.class, RelojConfig.class})
@TestPropertySource(properties = {
        "app.rate-limit.habilitado=true",
        "app.rate-limit.confiar-en-proxy=false",
        "app.rate-limit.refresh.capacidad=2",
        "app.rate-limit.refresh.recarga-por-minuto=1",
        "app.rate-limit.auth.capacidad=50",
        "app.rate-limit.auth.recarga-por-minuto=50"
})
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_EACH_TEST_METHOD)
class RateLimitFilterRefreshTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private AuthService authService;

    @MockitoBean
    private JwtDecoder jwtDecoder;

    @Test
    @DisplayName("el refresh tiene su cubo propio: agotarlo no toca el presupuesto del login")
    void cuboPropioDeRefresh() throws Exception {
        when(authService.refresca(anyString())).thenThrow(new CredencialesInvalidasException());
        when(authService.login(anyString(), anyString())).thenThrow(new CredencialesInvalidasException());

        mockMvc.perform(post("/api/v1/auth/refresh").contentType(MediaType.APPLICATION_JSON)
                .content("{\"refreshToken\":\"x\"}")).andExpect(status().isUnauthorized());
        mockMvc.perform(post("/api/v1/auth/refresh").contentType(MediaType.APPLICATION_JSON)
                .content("{\"refreshToken\":\"x\"}")).andExpect(status().isUnauthorized());
        mockMvc.perform(post("/api/v1/auth/refresh").contentType(MediaType.APPLICATION_JSON)
                .content("{\"refreshToken\":\"x\"}")).andExpect(status().isTooManyRequests());

        // El login sigue con su presupuesto intacto: 401 de credenciales, nunca 429.
        mockMvc.perform(post("/api/v1/auth/login").contentType(MediaType.APPLICATION_JSON)
                .content("{\"email\":\"a@b.c\",\"password\":\"da-igual-123\"}"))
                .andExpect(status().isUnauthorized());
    }
}
