package io.github.programming0026.juniverse.redis.config;

import io.github.programming0026.juniverse.redis.service.RedisCircuitBreaker;
import java.time.Duration;
import java.util.Collection;
import java.util.concurrent.Callable;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Supplier;

import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.Cache;
import org.springframework.cache.CacheManager;

/**
 * Administrador de caché de Spring que no deja que un fallo de Redis haga fallar el método
 * anotado con {@code @Cacheable}, {@code @CachePut} o {@code @CacheEvict}.
 *
 * <p>Envuelve a otro administrador. Si una operación de caché falla, registra el fallo y se
 * comporta como si la entrada no existiera: las lecturas devuelven «no está», y los guardados
 * y las invalidaciones no hacen nada. El método de negocio se ejecuta igualmente y sus propias
 * excepciones llegan intactas a quien llama.</p>
 *
 * <p>Tras un fallo de conexión, durante la pausa de {@link RedisCircuitBreaker} las operaciones
 * ni siquiera intentan llegar a Redis, para no esperar su tiempo de respuesta.</p>
 *
 * <p>Mientras Redis no responde, las invalidaciones no se aplican; al recuperarse puede haber
 * datos antiguos hasta que caduquen.</p>
 *
 * @author Java Universe
 */
@Slf4j
public class FailOpenCacheManager implements CacheManager {

    /** Intervalo mínimo entre dos avisos de fallo en el log; el resto va a depuración. */
    private static final long WARN_INTERVAL_NANOS = TimeUnit.SECONDS.toNanos(10);

    /** Administrador real. */
    private final CacheManager delegate;

    /** Deja de usar Redis un tiempo tras un fallo de conexión; compartido con las demás formas de caché. */
    private final RedisCircuitBreaker breaker;

    /** Cachés ya envueltas, por nombre. */
    private final ConcurrentMap<String, Cache> caches = new ConcurrentHashMap<>();

    /** Instante del último aviso de fallo escrito en el log; 0 si no hubo ninguno. */
    private final AtomicLong lastWarnNanos = new AtomicLong();

    /**
     * Envuelve un administrador de caché.
     *
     * @param delegate administrador real
     */
    public FailOpenCacheManager(CacheManager delegate) {
        this(delegate, new RedisCircuitBreaker(Duration.ZERO));
    }

    /**
     * Envuelve un administrador de caché con un cortacircuitos, normalmente compartido con las
     * demás formas de caché: mientras está abierto, las operaciones no llegan a Redis.
     *
     * @param delegate administrador real
     * @param breaker cortacircuitos que evita esperar a Redis mientras está caído
     */
    public FailOpenCacheManager(CacheManager delegate, RedisCircuitBreaker breaker) {
        this.delegate = delegate;
        this.breaker = breaker;
    }

    /**
     * Devuelve el administrador real, por si hace falta su API específica.
     *
     * @return administrador envuelto
     */
    public CacheManager getDelegate() {
        return delegate;
    }

    /**
     * Devuelve la caché con ese nombre, protegida frente a fallos.
     *
     * @param name nombre de la caché
     * @return caché, o {@code null} si el administrador real no la tiene
     */
    @Override
    public Cache getCache(String name) {
        Cache cache = delegate.getCache(name);
        return cache == null ? null : caches.computeIfAbsent(name, key -> new FailOpenCache(cache));
    }

    /**
     * Devuelve los nombres de las cachés conocidas.
     *
     * @return nombres de caché
     */
    @Override
    public Collection<String> getCacheNames() {
        return delegate.getCacheNames();
    }

    private void logFailure(String operation, String cacheName, RuntimeException e) {
        breaker.recordFailure(e);
        long now = System.nanoTime();
        long last = lastWarnNanos.get();
        boolean due = last == 0 || now - last >= WARN_INTERVAL_NANOS;
        if (due && lastWarnNanos.compareAndSet(last, now == 0 ? 1 : now)) {
            log.warn("Cache {} failed on '{}'; continuing without cache: {}", operation, cacheName, e.toString());
        } else {
            log.debug("Cache {} failed on '{}': {}", operation, cacheName, e.toString());
        }
    }

    /**
     * Caché que trata los fallos como ausencia de valor.
     */
    private final class FailOpenCache implements Cache {

        /** Caché real. */
        private final Cache cache;

        FailOpenCache(Cache cache) {
            this.cache = cache;
        }

        @Override
        public String getName() {
            return cache.getName();
        }

        @Override
        public Object getNativeCache() {
            return cache.getNativeCache();
        }

        @Override
        public ValueWrapper get(Object key) {
            return guarded("get", () -> cache.get(key), null);
        }

        @Override
        public <T> T get(Object key, Class<T> type) {
            return guarded("get", () -> cache.get(key, type), null);
        }

        /**
         * Obtiene el valor o lo carga. Si la caché falla antes de cargar, el cargador se ejecuta
         * aquí; si falla después (al guardar), se devuelve lo que ya cargó. El cargador nunca se
         * ejecuta dos veces y sus excepciones se propagan como hace Spring.
         */
        @Override
        public <T> T get(Object key, Callable<T> valueLoader) {
            if (breaker.isOpen()) {
                return loadDirectly(key, valueLoader);
            }
            Object[] loaded = new Object[1];
            boolean[] wasLoaded = new boolean[1];
            Callable<T> tracking = () -> {
                T value = valueLoader.call();
                loaded[0] = value;
                wasLoaded[0] = true;
                return value;
            };
            try {
                T value = cache.get(key, tracking);
                breaker.recordSuccess();
                return value;
            } catch (ValueRetrievalException e) {
                // El cargador falló, no Redis.
                breaker.recordSuccess();
                throw e;
            } catch (RuntimeException e) {
                logFailure("get", cache.getName(), e);
                if (wasLoaded[0]) {
                    @SuppressWarnings("unchecked")
                    T value = (T) loaded[0];
                    return value;
                }
                return loadDirectly(key, valueLoader);
            }
        }

        private <T> T loadDirectly(Object key, Callable<T> valueLoader) {
            try {
                return valueLoader.call();
            } catch (Exception loaderFailure) {
                throw new ValueRetrievalException(key, valueLoader, loaderFailure);
            }
        }

        @Override
        public CompletableFuture<?> retrieve(Object key) {
            return cache.retrieve(key);
        }

        @Override
        public <T> CompletableFuture<T> retrieve(Object key, Supplier<CompletableFuture<T>> valueLoader) {
            return cache.retrieve(key, valueLoader);
        }

        @Override
        public void put(Object key, Object value) {
            guarded("put", () -> {
                cache.put(key, value);
                return null;
            }, null);
        }

        @Override
        public ValueWrapper putIfAbsent(Object key, Object value) {
            return guarded("put", () -> cache.putIfAbsent(key, value), null);
        }

        @Override
        public void evict(Object key) {
            guarded("evict", () -> {
                cache.evict(key);
                return null;
            }, null);
        }

        @Override
        public boolean evictIfPresent(Object key) {
            return guarded("evict", () -> cache.evictIfPresent(key), Boolean.FALSE);
        }

        @Override
        public void clear() {
            guarded("clear", () -> {
                cache.clear();
                return null;
            }, null);
        }

        @Override
        public boolean invalidate() {
            return guarded("clear", cache::invalidate, Boolean.FALSE);
        }

        private <T> T guarded(String operation, Supplier<T> action, T fallback) {
            if (breaker.isOpen()) {
                return fallback;
            }
            try {
                T result = action.get();
                breaker.recordSuccess();
                return result;
            } catch (RuntimeException e) {
                logFailure(operation, cache.getName(), e);
                return fallback;
            }
        }
    }
}
