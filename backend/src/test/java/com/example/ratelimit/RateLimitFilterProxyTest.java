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
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doThrow;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Con {@code confiar-en-proxy=true} (producción: nginx delante), la IP del
 * cliente se toma del ÚLTIMO valor de {@code X-Forwarded-For} — el que anexa
 * nuestro proxy — y NO del primero, que el cliente controla. Este test blinda
 * el fallo de seguridad corregido: si se tomara el primer valor, un atacante
 * rotándolo se saltaría por completo el límite anti-fuerza-bruta del login.
 */
@WebMvcTest(AuthController.class)
@Import({SecurityConfig.class, GlobalExceptionHandler.class, RateLimitConfig.class, RelojConfig.class})
@TestPropertySource(properties = {
        "app.rate-limit.habilitado=true",
        "app.rate-limit.confiar-en-proxy=true",
        "app.rate-limit.auth.capacidad=2",
        "app.rate-limit.auth.recarga-por-minuto=2",
        "app.rate-limit.api.capacidad=5",
        "app.rate-limit.api.recarga-por-minuto=5"
})
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_EACH_TEST_METHOD)
class RateLimitFilterProxyTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private AuthService authService;

    @MockitoBean
    private JwtDecoder jwtDecoder;

    private void loginMalo(String xff, int esperado) throws Exception {
        // doThrow (no when().thenThrow()): re-grabar con thenThrow dispararía el
        // stub anterior al ejecutar authService.login() dentro de when().
        doThrow(new CredencialesInvalidasException()).when(authService).login(anyString(), anyString());
        mockMvc.perform(post("/api/v1/auth/login")
                        .header("X-Forwarded-For", xff)
                        .contentType("application/json")
                        .content("{\"email\":\"a@b.dev\",\"password\":\"malapass-123\"}"))
                .andExpect(status().is(esperado));
    }

    @Test
    @DisplayName("rotar el PRIMER valor de X-Forwarded-For NO evade el límite: la IP real es el último salto")
    void spoofingDelPrimerValorNoEvade() throws Exception {
        // El atacante forja un primer valor distinto en cada petición; nginx le
        // anexa siempre la misma IP real al final ("203.0.113.9").
        loginMalo("1.1.1.1, 203.0.113.9", 401); // capacidad 2: 1ª pasa
        loginMalo("2.2.2.2, 203.0.113.9", 401); // 2ª pasa
        // La 3ª: aunque el primer valor cambie, la IP efectiva (203.0.113.9) ya
        // agotó su cubeta → 429. Con el bug (tomar el primero) esto sería 401.
        loginMalo("3.3.3.3, 203.0.113.9", 429);
    }

    @Test
    @DisplayName("dos clientes reales distintos (según el último salto) tienen cada uno su presupuesto")
    void ipsRealesDistintasNoCompartenCubeta() throws Exception {
        loginMalo("10.0.0.1", 401);
        loginMalo("10.0.0.1", 401);
        loginMalo("10.0.0.1", 429); // el cliente 10.0.0.1 agotó lo suyo
        // Otro cliente real: cubeta propia, sigue con presupuesto.
        loginMalo("10.0.0.2", 401);
    }
}
