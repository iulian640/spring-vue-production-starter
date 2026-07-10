package com.example.ratelimit;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Configuración del rate limiting transversal (prefijo {@code app.rate-limit}).
 *
 * <p>Presupuestos independientes: uno estricto por IP para
 * {@code /api/v1/auth/**} (anti fuerza bruta), uno propio para
 * {@code /auth/refresh} (tráfico sostenido legítimo, ver abajo) y otro más
 * generoso para el resto de {@code /api/**}, por usuario autenticado cuando
 * hay un JWT Bearer sintácticamente válido, o por IP en caso contrario.
 *
 * <p>OJO con el presupuesto de auth: la IP de salida se comparte (WiFi del
 * local con toda la plantilla, CGNAT de las operadoras móviles), así que al
 * empezar el turno TODOS los del mismo WiFi hacen login casi a la vez. La
 * ráfaga (30) debe absorber una plantilla entera; la defensa fina contra
 * fuerza bruta sobre UNA cuenta es el bloqueo por cuenta (pendiente de
 * decisión de producto), no estrangular la IP entera.
 *
 * <p>Los valores por defecto (ver {@code application.yml}) están pensados
 * para producción; el perfil {@code test} lo deshabilita ({@code habilitado: false})
 * para no interferir con las suites existentes.
 *
 * @param habilitado     activa o desactiva el filtro por completo.
 * @param confiarEnProxy si es {@code true}, la IP del cliente se toma de la
 *                       cabecera {@code X-Forwarded-For} (primer valor); si
 *                       es {@code false} (por defecto) se usa siempre
 *                       {@code request.getRemoteAddr()}. Activarlo sin un
 *                       proxy de confianza delante permite a cualquier
 *                       cliente falsear su IP y saltarse el límite
 *                       (IP-spoofing) — solo debe activarse cuando de verdad
 *                       hay un proxy/balanceador que sobreescribe esa cabecera.
 * @param auth           presupuesto para login/registro/logout.
 * @param refresh        presupuesto para {@code /auth/refresh} (hallazgo del
 *                       security review): con el access de 15 min cada usuario
 *                       activo refresca ~4 veces/hora durante TODO el turno, y
 *                       la clave es la IP compartida — metido en el bucket de
 *                       auth, una plantilla tras un WiFi agotaría el estricto
 *                       de login (auto-DoS). La fuerza bruta del refresh no es
 *                       la amenaza (256 bits): este bucket solo frena el abuso
 *                       volumétrico sin estrangular a los legítimos.
 * @param api            presupuesto para el resto de la API.
 */
@ConfigurationProperties(prefix = "app.rate-limit")
public record RateLimitProperties(
        boolean habilitado,
        boolean confiarEnProxy,
        Presupuesto auth,
        Presupuesto refresh,
        Presupuesto api) {

    public RateLimitProperties {
        auth = auth != null ? auth : Presupuesto.DEFECTO_AUTH;
        refresh = refresh != null ? refresh : Presupuesto.DEFECTO_REFRESH;
        api = api != null ? api : Presupuesto.DEFECTO_API;
    }

    /**
     * Un presupuesto de peticiones: capacidad máxima (ráfaga) y recarga
     * proporcional al tiempo, expresada en peticiones por minuto.
     *
     * @param capacidad        tope de peticiones que se pueden consumir de golpe (ráfaga).
     * @param recargaPorMinuto peticiones que se recargan cada minuto, en régimen permanente.
     */
    public record Presupuesto(int capacidad, int recargaPorMinuto) {

        static final Presupuesto DEFECTO_AUTH = new Presupuesto(30, 20);
        // Dimensionado: ~100 usuarios tras una misma IP × 4 refresh/hora ≈ 7/min.
        static final Presupuesto DEFECTO_REFRESH = new Presupuesto(60, 40);
        static final Presupuesto DEFECTO_API = new Presupuesto(40, 120);
    }
}
