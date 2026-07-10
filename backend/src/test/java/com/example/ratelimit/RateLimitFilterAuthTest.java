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
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * El filtro de rate limiting ({@link RateLimitFilter}) cableado de verdad en
 * la cadena de seguridad, con presupuestos pequeños y deterministas
 * (vía {@link TestPropertySource}) para poder agotarlos en pocas peticiones.
 */
@WebMvcTest(AuthController.class)
@Import({SecurityConfig.class, GlobalExceptionHandler.class, RateLimitConfig.class, RelojConfig.class})
@TestPropertySource(properties = {
        "app.rate-limit.habilitado=true",
        "app.rate-limit.confiar-en-proxy=false",
        "app.rate-limit.auth.capacidad=2",
        "app.rate-limit.auth.recarga-por-minuto=2",
        "app.rate-limit.api.capacidad=5",
        "app.rate-limit.api.recarga-por-minuto=5"
})
// Cada test agota deliberadamente el presupuesto de /auth; sin un contexto (y por
// tanto un RegistroCubetas) nuevo por test, el segundo test heredaría la cubeta
// ya agotada del primero.
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_EACH_TEST_METHOD)
class RateLimitFilterAuthTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private AuthService authService;

    @MockitoBean
    private JwtDecoder jwtDecoder;

    @Test
    @DisplayName("supera el presupuesto de /api/v1/auth/** (por IP) -> 429 con problem+json y Retry-After")
    void superaElPresupuestoDeAuthDevuelve429() throws Exception {
        when(authService.login(anyString(), anyString())).thenThrow(new CredencialesInvalidasException());

        mockMvc.perform(loginRequest()).andExpect(status().isUnauthorized());
        mockMvc.perform(loginRequest()).andExpect(status().isUnauthorized());

        mockMvc.perform(loginRequest())
                .andExpect(status().isTooManyRequests())
                .andExpect(header().exists("Retry-After"))
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.status").value(429))
                .andExpect(jsonPath("$.detail").value(
                        "Has superado el límite de peticiones. Inténtalo de nuevo en unos segundos."));
    }

    @Test
    @DisplayName("el Retry-After es un número positivo de segundos, no eco de datos del cliente")
    void elRetryAfterEsUnNumeroPositivoDeSegundos() throws Exception {
        when(authService.login(anyString(), anyString())).thenThrow(new CredencialesInvalidasException());

        mockMvc.perform(loginRequest()).andExpect(status().isUnauthorized());
        mockMvc.perform(loginRequest()).andExpect(status().isUnauthorized());

        mockMvc.perform(loginRequest())
                .andExpect(status().isTooManyRequests())
                .andExpect(result -> {
                    String retryAfter = result.getResponse().getHeader("Retry-After");
                    org.assertj.core.api.Assertions.assertThat(Long.parseLong(retryAfter)).isPositive();
                });
    }

    @Test
    @DisplayName("el presupuesto de /api/v1/auth/** y el del resto de /api/** son independientes")
    void presupuestoDeAuthYApiSonIndependientes() throws Exception {
        when(authService.login(anyString(), anyString())).thenThrow(new CredencialesInvalidasException());

        mockMvc.perform(loginRequest()).andExpect(status().isUnauthorized());
        mockMvc.perform(loginRequest()).andExpect(status().isUnauthorized());
        mockMvc.perform(loginRequest()).andExpect(status().isTooManyRequests());

        // /me consume el presupuesto "api", que sigue intacto: 401 (sin token), nunca 429.
        mockMvc.perform(get("/api/v1/me")).andExpect(status().isUnauthorized());
    }

    private MockHttpServletRequestBuilder loginRequest() {
        return post("/api/v1/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"email":"trabajador@example.com","password":"mala"}""");
    }
}
