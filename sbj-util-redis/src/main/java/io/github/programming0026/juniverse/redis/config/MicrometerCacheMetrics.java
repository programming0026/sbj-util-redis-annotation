package io.github.programming0026.juniverse.redis.config;

import io.github.programming0026.juniverse.redis.service.CacheMetrics;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Publica los contadores de la caché con coalescencia en Micrometer.
 *
 * <table>
 *   <caption>Métricas</caption>
 *   <tr><th>Nombre</th><th>Etiqueta</th><th>Qué cuenta</th></tr>
 *   <tr><td>{@code sbj.redis.cache.requests}</td><td>{@code result}: hit, miss, coalesced</td>
 *       <td>Peticiones servidas de la caché, que no encontraron valor, o que recibieron el
 *       valor que cargó otra petición</td></tr>
 *   <tr><td>{@code sbj.redis.cache.loads}</td><td></td><td>Ejecuciones del método de negocio</td></tr>
 *   <tr><td>{@code sbj.redis.cache.wait.timeouts}</td><td></td>
 *       <td>Peticiones que agotaron la espera del resultado de otra</td></tr>
 *   <tr><td>{@code sbj.redis.cache.errors}</td><td>{@code operation}</td>
 *       <td>Operaciones contra Redis que fallaron</td></tr>
 * </table>
 *
 * @author Java Universe
 */
public class MicrometerCacheMetrics implements CacheMetrics {

    /** Nombre de la métrica de peticiones. */
    static final String REQUESTS = "sbj.redis.cache.requests";

    /** Nombre de la métrica de cargas. */
    static final String LOADS = "sbj.redis.cache.loads";

    /** Nombre de la métrica de esperas agotadas. */
    static final String WAIT_TIMEOUTS = "sbj.redis.cache.wait.timeouts";

    /** Nombre de la métrica de errores de Redis. */
    static final String ERRORS = "sbj.redis.cache.errors";

    /** Registro de métricas. */
    private final MeterRegistry registry;

    /** Peticiones servidas de la caché. */
    private final Counter hits;

    /** Peticiones que no encontraron valor. */
    private final Counter misses;

    /** Peticiones que recibieron el valor que cargó otra. */
    private final Counter coalesced;

    /** Ejecuciones del método de negocio. */
    private final Counter loads;

    /** Esperas agotadas. */
    private final Counter waitTimeouts;

    /** Errores de Redis, por operación. */
    private final Map<String, Counter> errors = new ConcurrentHashMap<>();

    /**
     * Crea los contadores.
     *
     * @param registry registro de métricas de la aplicación
     */
    public MicrometerCacheMetrics(MeterRegistry registry) {
        this.registry = registry;
        this.hits = requests("hit", "Peticiones servidas de la caché");
        this.misses = requests("miss", "Peticiones que no encontraron su valor en la caché");
        this.coalesced = requests("coalesced", "Peticiones que recibieron el valor que cargó otra");
        this.loads = Counter.builder(LOADS)
                .description("Ejecuciones del método de negocio para cargar un valor")
                .register(registry);
        this.waitTimeouts = Counter.builder(WAIT_TIMEOUTS)
                .description("Peticiones que agotaron la espera del resultado de otra")
                .register(registry);
    }

    private Counter requests(String result, String description) {
        return Counter.builder(REQUESTS).tag("result", result).description(description).register(registry);
    }

    @Override
    public void hit() {
        hits.increment();
    }

    @Override
    public void miss() {
        misses.increment();
    }

    @Override
    public void load() {
        loads.increment();
    }

    @Override
    public void coalesced() {
        coalesced.increment();
    }

    @Override
    public void waitTimeout() {
        waitTimeouts.increment();
    }

    @Override
    public void redisError(String operation) {
        errors.computeIfAbsent(operation, name -> Counter.builder(ERRORS)
                .tag("operation", name)
                .description("Operaciones contra Redis que fallaron")
                .register(registry)).increment();
    }
}
