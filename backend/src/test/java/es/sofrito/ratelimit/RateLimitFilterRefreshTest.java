package es.sofrito.ratelimit;

import es.sofrito.config.RelojConfig;
import es.sofrito.config.SecurityConfig;
import es.sofrito.controller.AuthController;
import es.sofrito.controller.GlobalExceptionHandler;
import es.sofrito.service.AuthService;
import es.sofrito.service.CredencialesInvalidasException;
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
 * El bucket PROPIO de {@code /auth/refresh} (security review de B4): con el
 * access de 15 min, el refresh es tráfico sostenido de toda la plantilla tras
 * una misma IP — metido en el bucket estricto de login, los legítimos se
 * auto-bloquearían el login. Aquí se prueba que ambos cubos son independientes.
 */
@WebMvcTest(AuthController.class)
@Import({SecurityConfig.class, GlobalExceptionHandler.class, RateLimitConfig.class, RelojConfig.class})
@TestPropertySource(properties = {
        "sofrito.rate-limit.habilitado=true",
        "sofrito.rate-limit.confiar-en-proxy=false",
        "sofrito.rate-limit.refresh.capacidad=2",
        "sofrito.rate-limit.refresh.recarga-por-minuto=1",
        "sofrito.rate-limit.auth.capacidad=50",
        "sofrito.rate-limit.auth.recarga-por-minuto=50"
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
