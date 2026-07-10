package es.sofrito.ratelimit;

import es.sofrito.config.RelojConfig;
import es.sofrito.config.SecurityConfig;
import es.sofrito.controller.CuentaController;
import es.sofrito.controller.GlobalExceptionHandler;
import es.sofrito.service.CuentaService;
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

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * El borrado de cuenta re-confirma la contraseña: es el mismo control
 * anti-fuerza-bruta que el login, así que usa el presupuesto ESTRICTO de auth,
 * no el genérico de la API (hallazgo de las reviews de seguridad y de Java:
 * con el genérico, un token robado permitía ~120 intentos de contraseña por
 * minuto contra una acción destructiva e irreversible).
 */
@WebMvcTest(CuentaController.class)
@Import({SecurityConfig.class, GlobalExceptionHandler.class, RateLimitConfig.class, RelojConfig.class})
@TestPropertySource(properties = {
        "sofrito.rate-limit.habilitado=true",
        "sofrito.rate-limit.confiar-en-proxy=false",
        "sofrito.rate-limit.auth.capacidad=2",
        "sofrito.rate-limit.auth.recarga-por-minuto=1",
        "sofrito.rate-limit.api.capacidad=50",
        "sofrito.rate-limit.api.recarga-por-minuto=50"
})
// Cada test agota el presupuesto; contexto nuevo por test para no heredar cubetas.
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_EACH_TEST_METHOD)
class RateLimitFilterCuentaTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private CuentaService cuentaService;

    @MockitoBean
    private JwtDecoder jwtDecoder;

    @Test
    @DisplayName("DELETE /cuenta gasta el presupuesto estricto de auth, no el genérico de la API")
    void presupuestoEstrictoParaElBorrado() throws Exception {
        // El filtro corre ANTES de la autenticación: dos intentos (aquí 401 por
        // ir sin token) consumen el cubo estricto; el tercero ya es 429 aunque
        // el presupuesto genérico de la API (50) esté intacto.
        mockMvc.perform(delete("/api/v1/cuenta").contentType(MediaType.APPLICATION_JSON)
                .content("{\"password\":\"x\"}")).andExpect(status().isUnauthorized());
        mockMvc.perform(delete("/api/v1/cuenta").contentType(MediaType.APPLICATION_JSON)
                .content("{\"password\":\"x\"}")).andExpect(status().isUnauthorized());
        mockMvc.perform(delete("/api/v1/cuenta").contentType(MediaType.APPLICATION_JSON)
                .content("{\"password\":\"x\"}")).andExpect(status().isTooManyRequests());

        // El resto de la API sigue con su presupuesto: 401 (sin token), nunca 429.
        mockMvc.perform(get("/api/v1/me")).andExpect(status().isUnauthorized());
    }
}
