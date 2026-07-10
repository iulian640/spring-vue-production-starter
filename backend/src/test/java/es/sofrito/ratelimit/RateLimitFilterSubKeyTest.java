package es.sofrito.ratelimit;

import es.sofrito.config.RelojConfig;
import es.sofrito.config.SecurityConfig;
import es.sofrito.controller.AuthController;
import es.sofrito.controller.GlobalExceptionHandler;
import es.sofrito.service.AuthService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpHeaders;
import org.springframework.security.oauth2.jwt.BadJwtException;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.nio.charset.StandardCharsets;
import java.util.Base64;

import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * El presupuesto del resto de {@code /api/**} se reparte por el claim
 * {@code sub} del JWT cuando la cabecera trae un Bearer sintácticamente
 * válido (no hace falta que la firma sea correcta: aquí solo es la clave de
 * la cubeta — la firma la verifica Spring Security más adelante).
 */
@WebMvcTest(AuthController.class)
@Import({SecurityConfig.class, GlobalExceptionHandler.class, RateLimitConfig.class, RelojConfig.class})
@TestPropertySource(properties = {
        "sofrito.rate-limit.habilitado=true",
        "sofrito.rate-limit.auth.capacidad=100",
        "sofrito.rate-limit.auth.recarga-por-minuto=100",
        "sofrito.rate-limit.api.capacidad=1",
        "sofrito.rate-limit.api.recarga-por-minuto=1"
})
class RateLimitFilterSubKeyTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private AuthService authService;

    @MockitoBean
    private JwtDecoder jwtDecoder;

    @Test
    @DisplayName("mismo sub agota su presupuesto; un sub distinto tiene el suyo propio")
    void elMismoSubAgotaSuPresupuestoPeroOtroSubTieneElSuyo() throws Exception {
        when(jwtDecoder.decode(anyString())).thenThrow(new BadJwtException("token no verificado en este test"));

        mockMvc.perform(get("/api/v1/me").header(HttpHeaders.AUTHORIZATION, "Bearer " + jwtConSub("usuario-a")))
                .andExpect(status().isUnauthorized());
        mockMvc.perform(get("/api/v1/me").header(HttpHeaders.AUTHORIZATION, "Bearer " + jwtConSub("usuario-a")))
                .andExpect(status().isTooManyRequests());

        // usuario-b: cubeta propia, todavía con presupuesto.
        mockMvc.perform(get("/api/v1/me").header(HttpHeaders.AUTHORIZATION, "Bearer " + jwtConSub("usuario-b")))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("Bearer sintácticamente inválido (no son 3 partes) cae a rate limiting por IP")
    void bearerSintacticamenteInvalidoCaeAIp() throws Exception {
        when(jwtDecoder.decode(anyString())).thenThrow(new BadJwtException("token no verificado en este test"));

        mockMvc.perform(get("/api/v1/me").header(HttpHeaders.AUTHORIZATION, "Bearer no-es-un-jwt"))
                .andExpect(status().isUnauthorized());
        // Segunda petición con el MISMO fallback por IP: agota el presupuesto de 1.
        mockMvc.perform(get("/api/v1/me").header(HttpHeaders.AUTHORIZATION, "Bearer tampoco-es-un-jwt"))
                .andExpect(status().isTooManyRequests());
    }

    private static String jwtConSub(String sub) {
        String header = base64Url("{\"alg\":\"none\"}");
        String payload = base64Url("{\"sub\":\"" + sub + "\"}");
        return header + "." + payload + ".firma-cualquiera";
    }

    private static String base64Url(String json) {
        return Base64.getUrlEncoder().withoutPadding().encodeToString(json.getBytes(StandardCharsets.UTF_8));
    }

    /**
     * Cada test que cae a la cubeta por IP usa una IP propia: el registro de
     * cubetas vive en el contexto compartido de la clase y sin esto un test
     * agotaría el presupuesto del siguiente.
     */
    private static org.springframework.test.web.servlet.request.RequestPostProcessor desdeIp(String ip) {
        return request -> {
            request.setRemoteAddr(ip);
            return request;
        };
    }

    @Test
    @DisplayName("un sub kilométrico (token forjado para inflar memoria) no crea cubeta propia: cae a IP (review H1)")
    void subKilometricoCaeAIp() throws Exception {
        when(jwtDecoder.decode(anyString())).thenThrow(new BadJwtException("token no verificado en este test"));
        String subGigante = "x".repeat(500);

        mockMvc.perform(get("/api/v1/me").with(desdeIp("10.9.9.51"))
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + jwtConSub(subGigante)))
                .andExpect(status().isUnauthorized());
        // Con otro sub gigante distinto: si crease cubeta por sub, tendría
        // presupuesto propio; como cae a IP, comparte la cubeta y se agota.
        mockMvc.perform(get("/api/v1/me").with(desdeIp("10.9.9.51"))
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + jwtConSub("y".repeat(500))))
                .andExpect(status().isTooManyRequests());
    }

    @Test
    @DisplayName("el health check bajo /api/ está exento del filtro: ningún sondeo se come un 429 (review M1)")
    void healthExento() throws Exception {
        // Este @WebMvcTest no monta HealthController (404), pero para el filtro
        // es suficiente: 5 sondeos seguidos y NINGUNO devuelve 429.
        for (int i = 0; i < 5; i++) {
            mockMvc.perform(get("/api/v1/health").with(desdeIp("10.9.9.52")))
                    .andExpect(status().isNotFound());
        }
    }

    @Test
    @DisplayName("el preflight OPTIONS no consume presupuesto (CORS del futuro, review L2)")
    void optionsNoConsume() throws Exception {
        when(jwtDecoder.decode(anyString())).thenThrow(new BadJwtException("token no verificado en este test"));

        for (int i = 0; i < 5; i++) {
            mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders
                    .options("/api/v1/me").with(desdeIp("10.9.9.53"))).andExpect(status().isUnauthorized());
        }
        // Y el GET sigue teniendo su presupuesto intacto (1 permitido, no 429).
        mockMvc.perform(get("/api/v1/me").with(desdeIp("10.9.9.53"))).andExpect(status().isUnauthorized());
    }
}
