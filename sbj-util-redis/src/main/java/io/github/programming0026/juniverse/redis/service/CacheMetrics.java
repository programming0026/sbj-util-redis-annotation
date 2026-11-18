package io.github.programming0026.juniverse.redis.service;

/**
 * Contadores de lo que ocurre en la caché con coalescencia.
 *
 * <p>La librería trae una implementación para Micrometer, que se activa sola cuando la aplicación
 * tiene un {@code MeterRegistry}. Para enviar los contadores a otro sistema basta con definir un
 * bean de este tipo.</p>
 *
 * @author Java Universe
 */
public interface CacheMetrics {

    /** Implementación que no hace nada, para cuando no hay sistema de métricas. */
    CacheMetrics NOOP = new CacheMetrics() {
    };

    /**
     * Una petición encontró su valor en la caché.
     */
    default void hit() {
    }

    /**
     * Una petición no encontró su valor en la caché.
     */
    default void miss() {
    }

    /**
     * Se ejecutó el método de negocio para cargar un valor.
     */
    default void load() {
    }

    /**
     * Una petición recibió el valor que cargó otra, sin ejecutar el método de negocio.
     */
    default void coalesced() {
    }

    /**
     * Una petición agotó la espera del resultado que cargaba otra.
     */
    default void waitTimeout() {
    }

    /**
     * Falló una operación contra Redis.
     *
     * @param operation operación que falló: {@code read}, {@code write}, {@code lock},
     *                  {@code unlock}, {@code publish}, {@code evict}, {@code exists},
     *                  {@code ttl} o {@code expire}
     */
    default void redisError(String operation) {
    }
}
