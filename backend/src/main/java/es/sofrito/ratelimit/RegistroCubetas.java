package es.sofrito.ratelimit;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Registro de cubetas de tokens, una por clave (IP o usuario, con el grupo
 * de presupuesto ya incluido en la clave). Thread-safe vía
 * {@link ConcurrentHashMap}; cada cubeta individual ya es thread-safe por su
 * cuenta ({@link CubetaTokens}).
 *
 * <p>Para no crecer sin límite en memoria (un atacante podría generar claves
 * distintas sin parar — p. ej. tokens forjados con un {@code sub} distinto en
 * cada petición), hay DOS defensas: el barrido perezoso retira las cubetas que
 * llevan más de {@link #TTL_INACTIVIDAD} sin usarse (cada
 * {@link #PETICIONES_ENTRE_BARRIDOS} peticiones, no en cada una), y un TOPE
 * duro de {@link #MAX_CUBETAS} entradas. Con el registro lleno, las claves
 * NUEVAS comparten una única cubeta de desbordamiento por grupo: la memoria
 * queda acotada y el tráfico del atacante se estrangula a sí mismo, mientras
 * que las claves ya conocidas siguen con su cubeta propia.
 */
final class RegistroCubetas {

    private static final Duration TTL_INACTIVIDAD = Duration.ofMinutes(10);
    private static final long PETICIONES_ENTRE_BARRIDOS = 256;
    private static final int MAX_CUBETAS = 10_000;
    static final String SUFIJO_DESBORDAMIENTO = ":desbordamiento";

    private final ConcurrentHashMap<String, Entrada> cubetas = new ConcurrentHashMap<>();
    private final Clock reloj;
    private final long peticionesEntreBarridos;
    private final int maxCubetas;
    private final AtomicLong contadorPeticiones = new AtomicLong();

    RegistroCubetas(Clock reloj) {
        this(reloj, PETICIONES_ENTRE_BARRIDOS, MAX_CUBETAS);
    }

    /** Constructor de test: intervalo de barrido y tope de cubetas pequeños. */
    RegistroCubetas(Clock reloj, long peticionesEntreBarridos, int maxCubetas) {
        this.reloj = reloj;
        this.peticionesEntreBarridos = peticionesEntreBarridos;
        this.maxCubetas = maxCubetas;
    }

    /**
     * Intenta consumir un token de la cubeta identificada por {@code clave},
     * creándola (a tope de capacidad) si es la primera vez que se ve. Si el
     * registro está lleno y la clave es nueva, se usa la cubeta de
     * desbordamiento de su grupo (el prefijo hasta el primer ':').
     */
    boolean intentaConsumir(String clave, long capacidad, double recargaPorMinuto) {
        Entrada entrada = buscaOCrea(clave, capacidad, recargaPorMinuto);
        entrada.ultimoUso = reloj.instant();
        boolean permitido = entrada.cubeta.intentaConsumir();
        barreLoPerezosoQueHagaFalta();
        return permitido;
    }

    private Entrada buscaOCrea(String clave, long capacidad, double recargaPorMinuto) {
        Entrada existente = cubetas.get(clave);
        if (existente != null) {
            return existente;
        }
        if (cubetas.size() >= maxCubetas && !clave.endsWith(SUFIJO_DESBORDAMIENTO)) {
            // Lleno: la clave nueva comparte la cubeta de desbordamiento de su
            // grupo. Nada de barridos O(n) por petición ni de crecer sin tope.
            int separador = clave.indexOf(':');
            String grupo = separador < 0 ? "" : clave.substring(0, separador);
            return buscaOCrea(grupo + SUFIJO_DESBORDAMIENTO, capacidad, recargaPorMinuto);
        }
        return cubetas.computeIfAbsent(clave,
                k -> new Entrada(new CubetaTokens(capacidad, recargaPorMinuto, reloj)));
    }

    /**
     * Segundos hasta el próximo token de la cubeta de {@code clave}. Si esa
     * clave no tiene cubeta propia (el registro estaba lleno y la petición cayó
     * en la de desbordamiento de su grupo), se consulta esa — así el
     * Retry-After refleja la recarga real y no un 1s falso que invitaría a
     * reintentar en bucle bajo saturación.
     */
    long segundosHastaReintento(String clave) {
        Entrada entrada = cubetas.get(clave);
        if (entrada == null && !clave.endsWith(SUFIJO_DESBORDAMIENTO)) {
            int separador = clave.indexOf(':');
            String grupo = separador < 0 ? "" : clave.substring(0, separador);
            entrada = cubetas.get(grupo + SUFIJO_DESBORDAMIENTO);
        }
        return entrada == null ? 0L : entrada.cubeta.segundosHastaProximoToken();
    }

    /** Número de cubetas vivas ahora mismo (visibilidad para tests). */
    int tamano() {
        return cubetas.size();
    }

    private void barreLoPerezosoQueHagaFalta() {
        if (contadorPeticiones.incrementAndGet() % peticionesEntreBarridos != 0) {
            return;
        }
        Instant limite = reloj.instant().minus(TTL_INACTIVIDAD);
        cubetas.entrySet().removeIf(e -> e.getValue().ultimoUso.isBefore(limite));
    }

    private static final class Entrada {
        final CubetaTokens cubeta;
        volatile Instant ultimoUso;

        Entrada(CubetaTokens cubeta) {
            this.cubeta = cubeta;
            this.ultimoUso = Instant.EPOCH;
        }
    }
}
