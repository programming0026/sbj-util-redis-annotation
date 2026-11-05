package com.juniverse.redis.service;

import com.juniverse.redis.dto.CacheResult;
import com.juniverse.redis.util.Validator;

import java.time.Duration;
import java.util.function.Consumer;
import java.util.function.Supplier;

/**
 * Constructor de operaciones de caché con API fluida.
 *
 * <p><b>Seguridad en hilos:</b> esta clase NO es segura para hilos y no debe
 * compartirse entre hilos. Cada instancia está pensada para usarse en un solo
 * hilo. Use {@link Factory#create()} para obtener una instancia nueva por cada
 * operación de caché.</p>
 *
 * <p><b>Uso:</b></p>
 * <pre>{@code
 * @Autowired
 * private CacheOperationBuilder.Factory builderFactory;
 *
 * public User getUser(String userId) {
 *     // Obtiene una instancia nueva del constructor por cada operación
 *     CacheOperationBuilder<User> builder = builderFactory.create();
 *     return builder
 *         .cacheName("users")
 *         .key(userId)
 *         .loader(() -> userRepository.findById(userId))
 *         .ttl(Duration.ofMinutes(30))
 *         .cacheable();
 * }
 * }</pre>
 *
 * @author ajuar
 * @param <T> tipo genérico
 * @see Factory
 */
public final class CacheOperationBuilder<T> {

    /** Servicio de caché. */
    private final RedisCacheService cacheService;

    /** Nombre de caché. */
    private String cacheName;

    /** Clave. */
    private String key;

    /** Cargador del valor. */
    private Supplier<T> loader;

    /** Tiempo de vida. */
    private Duration ttl = Duration.ofMinutes(10);

    /** Condición para usar la caché. */
    private boolean condition = true;

    /** Retrollamada ante un acierto. */
    private Consumer<T> onHit;

    /** Retrollamada ante un fallo. */
    private Consumer<T> onMiss;

    /**
     * Constructor privado: use {@link Factory#create()} para crear instancias.
     *
     * @param cacheService servicio de caché
     */
    private CacheOperationBuilder(RedisCacheService cacheService) {
        this.cacheService = cacheService;
    }

    /**
     * Nombre de la caché.
     *
     * @param cacheName nombre de caché
     * @return constructor de operación de caché
     */
    public CacheOperationBuilder<T> cacheName(String cacheName) {
        this.cacheName = cacheName;
        return this;
    }

    /**
     * Clave.
     *
     * @param key clave
     * @return constructor de operación de caché
     */
    public CacheOperationBuilder<T> key(String key) {
        this.key = key;
        return this;
    }

    /**
     * Cargador.
     *
     * @param loader función que carga el valor
     * @return constructor de operación de caché
     */
    public CacheOperationBuilder<T> loader(Supplier<T> loader) {
        this.loader = loader;
        return this;
    }

    /**
     * Tiempo de vida (TTL).
     *
     * @param ttl tiempo de vida
     * @return constructor de operación de caché
     */
    public CacheOperationBuilder<T> ttl(Duration ttl) {
        this.ttl = ttl;
        return this;
    }

    /**
     * Condición.
     *
     * @param condition expresión de la condición
     * @return constructor de operación de caché
     */
    public CacheOperationBuilder<T> condition(boolean condition) {
        this.condition = condition;
        return this;
    }

    /**
     * Ante un acierto de caché.
     *
     * @param callback retrollamada
     * @return constructor de operación de caché
     */
    public CacheOperationBuilder<T> onCacheHit(Consumer<T> callback) {
        this.onHit = callback;
        return this;
    }

    /**
     * Ante un fallo de caché.
     *
     * @param callback retrollamada
     * @return constructor de operación de caché
     */
    public CacheOperationBuilder<T> onCacheMiss(Consumer<T> callback) {
        this.onMiss = callback;
        return this;
    }

    /**
     * Obtiene el valor de la caché o lo carga.
     *
     * <p><b>Comportamiento de las retrollamadas:</b></p>
     * <ul>
     *   <li>Si {@code condition} es falsa, el cargador se ejecuta directamente
     *       y NO se invoca ninguna retrollamada (onHit/onMiss).</li>
     *   <li>Si {@code condition} es verdadera y hay un acierto: se invoca {@code onHit}
     *       (si se indicó).</li>
     *   <li>Si {@code condition} es verdadera y hay un fallo: se invoca {@code onMiss}
     *       (si se indicó).</li>
     * </ul>
     *
     * @return valor de la caché o cargado
     */
    public T cacheable() {
        Validator.validateRequiredFields(cacheName, key, loader);

        if (!condition) {
            return loader.get();
        }

        CacheResult<T> result = cacheService.cacheableWithResult(cacheName, key, loader, ttl);

        if (result.isCacheHit() && onHit != null) {
            onHit.accept(result.getValue());
        } else if (result.isCacheMiss() && onMiss != null) {
            onMiss.accept(result.getValue());
        }

        return result.getValue();
    }


    /**
     * Guarda en la caché.
     *
     * @return resultado de la evaluación
     */
    public T cachePut() {
        Validator.validateRequiredFields(cacheName, key, loader);

        return cacheService.cachePut(cacheName, key, loader, ttl);
    }

    /**
     * Invalida una clave de la caché.
     */
    public void cacheEvict() {
        Validator.validateCacheEvict(cacheName, key);
        cacheService.cacheEvict(cacheName, key);
    }

    /**
     * Invalida todas las entradas de la caché indicada.
     *
     * <p>Equivale a {@code @CacheEvict(allEntries = true)}. Este método elimina
     * todas las claves que coinciden con el patrón {@code cacheName:*}. Usa SCAN para recorrer
     * las claves y las elimina por lotes, para no cargarlas todas en memoria
     * de una vez, lo que evita {@code OutOfMemoryError} con conjuntos de datos grandes.</p>
     */
    public void cacheEvictAll() {
        Validator.validateCacheEvict(cacheName);
        cacheService.cacheEvictAll(cacheName);
    }

    /**
     * Fábrica de instancias de {@link CacheOperationBuilder}.
     *
     * <p>Esta fábrica permite obtener instancias nuevas del constructor de forma segura para hilos.
     * La fábrica es un bean singleton y se puede inyectar y compartir
     * entre hilos sin problema. Cada llamada a {@link #create()} devuelve una instancia
     * nueva e independiente del constructor.</p>
     *
     * <p><b>Seguridad en hilos:</b> esta clase es totalmente segura para hilos. Varios hilos
     * pueden llamar a {@link #create()} al mismo tiempo.</p>
     *
     * <p><b>Uso:</b></p>
     * <pre>{@code
     * @Service
     * public class UserService {
     *     @Autowired
     *     private CacheOperationBuilder.Factory builderFactory;
     *
     *     public User getUser(String userId) {
     *         return builderFactory.create()
     *             .cacheName("users")
     *             .key(userId)
     *             .loader(() -> userRepository.findById(userId))
     *             .cacheable();
     *     }
     * }
     * }</pre>
     *
     * @author ajuar
     */
    public static class Factory {

        /** Servicio de caché. */
        private final RedisCacheService cacheService;

        public Factory(RedisCacheService cacheService) {
            this.cacheService = cacheService;
        }

        /**
         * Crea una nueva instancia de {@link CacheOperationBuilder}.
         *
         * <p>Cada invocación devuelve una instancia nueva e independiente que
         * debe usarse para una sola operación de caché y luego descartarse.</p>
         *
         * @param <T> tipo del valor en caché
         * @return nuevo constructor de operaciones de caché
         */
        public <T> CacheOperationBuilder<T> create() {
            return new CacheOperationBuilder<>(cacheService);
        }
    }
}
