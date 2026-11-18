package io.github.programming0026.juniverse.redis.service;

import io.github.programming0026.juniverse.redis.dto.CoalesceSettings;
import io.github.programming0026.juniverse.redis.dto.CacheResult;
import io.github.programming0026.juniverse.redis.dto.CoalescedResponseDto;
import io.github.programming0026.juniverse.redis.exception.CoalesceException;
import io.github.programming0026.juniverse.redis.util.CacheDefaults;
import io.github.programming0026.juniverse.redis.util.RedisKeyScanner;
import java.lang.reflect.Type;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Supplier;

import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.connection.Message;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.data.redis.core.script.RedisScript;
import org.springframework.data.redis.listener.ChannelTopic;
import org.springframework.data.redis.listener.RedisMessageListenerContainer;
import org.springframework.data.redis.serializer.SerializationException;

/**
 * Caché sobre Redis con coalescencia de peticiones: cuando varias peticiones piden a la vez
 * una clave que no está en caché, solo una ejecuta la carga y las demás reciben su resultado.
 *
 * <p><b>Cómo se reparte el trabajo.</b> Dentro de una misma JVM, la primera petición de una clave
 * se encarga de ella y las demás esperan su resultado en memoria. Entre instancias, la encargada
 * de cada JVM compite por un bloqueo en Redis: la que lo consigue carga el valor, lo guarda y
 * avisa por pub/sub; las demás esperan ese aviso y, por si se pierde, vuelven a mirar la caché
 * y el bloqueo cada {@value #POLL_INTERVAL_MILLIS} ms.</p>
 *
 * <p><b>Cargas largas.</b> Quien tiene el bloqueo lo renueva mientras carga, así que una carga
 * más larga que {@link CoalesceSettings#lockTimeout()} no se repite en otra instancia. Esa
 * duración es, en la práctica, lo que se tarda en detectar que la instancia que cargaba ha muerto.
 * Quien espera lo hace como mucho {@link CoalesceSettings#waitTimeout()}.</p>
 *
 * <p><b>Si Redis falla.</b> Con {@link CoalesceSettings#failOpen()} activo (valor por defecto), un
 * fallo de Redis se trata como un fallo de caché: la carga se ejecuta y el error solo se registra.
 * Tras un fallo de conexión se deja de usar Redis durante
 * {@link CoalesceSettings#failurePause()}. Mientras dura la avería las invalidaciones no llegan
 * a Redis, así que al recuperarse puede haber datos antiguos hasta que caduquen.</p>
 *
 * <p><b>Formato.</b> Los valores se guardan en JSON mediante {@link CacheValueCodec}. Los métodos
 * que reciben el tipo del valor lo reconstruyen con exactitud; los que no lo reciben usan el
 * formato con nombres de clase y sus límites. Todas las claves y el canal de avisos llevan
 * delante el prefijo de la aplicación, si se configuró.</p>
 *
 * @author Java Universe
 */
@Slf4j
public class CoalesceCacheManager implements AutoCloseable {

    /** Parte fija de las claves de los valores en caché. */
    private static final String CACHE_SEGMENT = "coalesce:cache:";

    /** Separador entre el nombre de la caché y la clave. */
    private static final String KEY_SEPARATOR = ":";

    /** Caracteres con significado especial en los patrones de Redis. */
    private static final String GLOB_CHARACTERS = "\\*?[]";

    /** Parte fija de las claves del bloqueo que evita calcular el mismo valor dos veces. */
    private static final String LOCK_SEGMENT = "coalesce:lock:";

    /** Parte fija del canal pub/sub por el que se avisa de que terminó la carga de una clave. */
    private static final String SIGNAL_SEGMENT = "coalesce:signal";

    /** Cada cuánto vuelve a mirar la caché y el bloqueo quien espera a otra instancia. */
    private static final long POLL_INTERVAL_MILLIS = 100;

    /** Intervalo mínimo entre dos avisos de fallo de Redis en el log; el resto va a depuración. */
    private static final long WARN_INTERVAL_NANOS = TimeUnit.SECONDS.toNanos(10);

    /** Valor que devuelve Redis para el tiempo de vida de una clave que no existe. */
    private static final long TTL_KEY_NOT_FOUND = -2;

    /** Borra el bloqueo solo si sigue siendo de quien lo pide. */
    private static final RedisScript<Long> RELEASE_LOCK = new DefaultRedisScript<>(
            "if redis.call('get', KEYS[1]) == ARGV[1] then return redis.call('del', KEYS[1]) else return 0 end",
            Long.class);

    /** Alarga la vida del bloqueo solo si sigue siendo de quien lo pide. */
    private static final RedisScript<Long> RENEW_LOCK = new DefaultRedisScript<>(
            "if redis.call('get', KEYS[1]) == ARGV[1] then return redis.call('pexpire', KEYS[1], ARGV[2]) "
            + "else return 0 end",
            Long.class);

    /** El bloqueo se renueva cada tercio de su duración: tolera dos renovaciones fallidas seguidas. */
    private static final int RENEWALS_PER_LOCK_TIMEOUT = 3;

    /** Resultado de una lectura cuando la clave no existe. */
    private static final CacheResult<Object> ABSENT = CacheResult.miss(null);

    /** Resultado de una lectura cuando no se pudo consultar Redis. */
    private static final CacheResult<Object> UNAVAILABLE = CacheResult.miss(null);

    /** Plantilla de Redis que lee y escribe los valores ya serializados. */
    private final RedisTemplate<String, byte[]> redisTemplate;

    /** Conversor de los valores a JSON. */
    private final CacheValueCodec codec;

    /** Ajustes de coalescencia. */
    private final CoalesceSettings settings;

    /** Contadores de lo que ocurre en la caché. */
    private final CacheMetrics metrics;

    /** Prefijo completo de las claves de los valores en caché. */
    private final String cachePrefix;

    /** Prefijo completo de las claves de bloqueo. */
    private final String lockPrefix;

    /** Canal pub/sub de los avisos de esta aplicación. */
    private final String signalChannel;

    /** Cargas en curso en esta JVM, por clave. */
    private final ConcurrentHashMap<String, CompletableFuture<Object>> localLoads = new ConcurrentHashMap<>();

    /** Avisos recibidos de otras instancias para las claves que esta JVM está esperando. */
    private final ConcurrentHashMap<String, BlockingQueue<CoalescedResponseDto>> signalQueues =
            new ConcurrentHashMap<>();

    /** Renueva los bloqueos de las cargas en curso de esta JVM. */
    private final ScheduledThreadPoolExecutor lockRenewer = newLockRenewer();

    /** Deja de usar Redis un tiempo tras un fallo de conexión; compartido con las demás formas de caché. */
    private final RedisCircuitBreaker breaker;

    /** Instante del último aviso de fallo escrito en el log; 0 si no hubo ninguno. */
    private final AtomicLong lastWarnNanos = new AtomicLong();

    /**
     * Crea el administrador con los ajustes predeterminados y sin prefijo de aplicación.
     *
     * @param redisTemplate plantilla de Redis con claves de texto y valores en bytes sin transformar
     * @param listenerContainer escucha de mensajes pub/sub
     * @param codec conversor de los valores a JSON
     */
    public CoalesceCacheManager(
            RedisTemplate<String, byte[]> redisTemplate,
            RedisMessageListenerContainer listenerContainer,
            CacheValueCodec codec) {
        this(redisTemplate, listenerContainer, codec, CoalesceSettings.defaults(), "");
    }

    /**
     * Crea el administrador.
     *
     * @param redisTemplate plantilla de Redis con claves de texto y valores en bytes sin transformar
     * @param listenerContainer escucha de mensajes pub/sub
     * @param codec conversor de los valores a JSON
     * @param settings ajustes de coalescencia
     * @param keyPrefix prefijo de la aplicación para claves y canales; vacío si no se usa
     */
    public CoalesceCacheManager(
            RedisTemplate<String, byte[]> redisTemplate,
            RedisMessageListenerContainer listenerContainer,
            CacheValueCodec codec,
            CoalesceSettings settings,
            String keyPrefix) {
        this(redisTemplate, listenerContainer, codec, settings, keyPrefix, CacheMetrics.NOOP);
    }

    /**
     * Crea el administrador con contadores de métricas.
     *
     * @param redisTemplate plantilla de Redis con claves de texto y valores en bytes sin transformar
     * @param listenerContainer escucha de mensajes pub/sub
     * @param codec conversor de los valores a JSON
     * @param settings ajustes de coalescencia
     * @param keyPrefix prefijo de la aplicación para claves y canales; vacío si no se usa
     * @param metrics contadores de lo que ocurre en la caché
     */
    public CoalesceCacheManager(
            RedisTemplate<String, byte[]> redisTemplate,
            RedisMessageListenerContainer listenerContainer,
            CacheValueCodec codec,
            CoalesceSettings settings,
            String keyPrefix,
            CacheMetrics metrics) {
        this(redisTemplate, listenerContainer, codec, settings, keyPrefix, metrics,
                new RedisCircuitBreaker(settings.failurePause()));
    }

    /**
     * Crea el administrador con un cortacircuitos, normalmente compartido con las demás formas de caché.
     *
     * @param redisTemplate plantilla de Redis con claves de texto y valores en bytes sin transformar
     * @param listenerContainer escucha de mensajes pub/sub
     * @param codec conversor de los valores a JSON
     * @param settings ajustes de coalescencia
     * @param keyPrefix prefijo de la aplicación para claves y canales; vacío si no se usa
     * @param metrics contadores de lo que ocurre en la caché
     * @param breaker cortacircuitos que evita esperar a Redis mientras está caído
     */
    public CoalesceCacheManager(
            RedisTemplate<String, byte[]> redisTemplate,
            RedisMessageListenerContainer listenerContainer,
            CacheValueCodec codec,
            CoalesceSettings settings,
            String keyPrefix,
            CacheMetrics metrics,
            RedisCircuitBreaker breaker) {
        this.redisTemplate = redisTemplate;
        this.codec = codec;
        this.settings = settings;
        this.metrics = metrics;
        this.breaker = breaker;

        String namespace = CacheDefaults.namespace(keyPrefix);
        this.cachePrefix = namespace + CACHE_SEGMENT;
        this.lockPrefix = namespace + LOCK_SEGMENT;
        this.signalChannel = namespace + SIGNAL_SEGMENT;

        listenerContainer.addMessageListener(
                this::handleSignal,
                new ChannelTopic(signalChannel)
        );
    }

    /**
     * Guarda un valor cuyo tipo no se conoce, en el formato con nombres de clase.
     * Es preferible {@link #put(String, Object, long, Type)}.
     *
     * @param key clave
     * @param value valor; un nulo queda guardado como tal
     * @param ttlSeconds tiempo de vida en segundos; si no es positivo, el valor no caduca
     */
    public void put(String key, Object value, long ttlSeconds) {
        put(key, value, ttlSeconds, Object.class);
    }

    /**
     * Guarda un valor.
     *
     * @param key clave
     * @param value valor; un nulo queda guardado como tal
     * @param ttlSeconds tiempo de vida en segundos; si no es positivo, el valor no caduca
     * @param type tipo del valor; debe ser el mismo con el que luego se lea
     */
    public void put(String key, Object value, long ttlSeconds, Type type) {
        store(key, value, ttlSeconds, type, false);
    }

    /**
     * Obtiene un valor guardado sin tipo, en el formato con nombres de clase.
     * No distingue un nulo guardado de una clave ausente; para eso está
     * {@link #lookup(String, Type)}.
     *
     * @param key clave
     * @return el valor, o vacío si no está en caché, si es nulo o si no se pudo consultar Redis
     */
    public Optional<Object> get(String key) {
        return Optional.ofNullable(lookup(key, Object.class).getValue());
    }

    /**
     * Busca un valor en la caché.
     *
     * @param key clave
     * @param type tipo con el que se reconstruye el valor
     * @return acierto con el valor (que puede ser nulo si se guardó un nulo), o fallo si la clave
     *         no existe o no se pudo consultar Redis
     */
    public CacheResult<Object> lookup(String key, Type type) {
        String fullKey = cachePrefix + key;
        CacheResult<Object> result = read(fullKey, type);
        if (result.isCacheHit()) {
            metrics.hit();
            log.debug("Cache hit for key: {}", fullKey);
        } else {
            metrics.miss();
            log.debug("Cache miss for key: {}", fullKey);
        }
        return result;
    }

    /**
     * Invalida una clave.
     *
     * @param key clave
     */
    public void evict(String key) {
        String fullKey = cachePrefix + key;
        run("evict", fullKey, () -> {
            redisTemplate.delete(fullKey);
            log.debug("Evicted cache key: {}", fullKey);
        });
    }

    /**
     * Invalida todas las entradas de una caché.
     * Solo afecta a las claves {@code prefix:...}; otras cachés cuyo nombre empieza igual
     * (por ejemplo {@code users} frente a {@code user}) no se tocan.
     *
     * @param prefix nombre de la caché, sin el separador final
     */
    public void evictAll(String prefix) {
        deleteMatching(escapeGlob(cachePrefix + prefix + KEY_SEPARATOR) + "*");
    }

    /**
     * Invalida las claves que coinciden con un patrón.
     *
     * @param pattern patrón de claves
     */
    public void evictPattern(String pattern) {
        deleteMatching(escapeGlob(cachePrefix) + pattern);
    }

    /**
     * Invalida varias claves.
     *
     * @param keys claves
     */
    public void evictMultiple(Collection<String> keys) {
        List<String> fullKeys = keys.stream()
                .map(key -> cachePrefix + key)
                .toList();

        if (!fullKeys.isEmpty()) {
            run("evict", fullKeys.size() + " keys", () -> {
                redisTemplate.delete(fullKeys);
                log.debug("Evicted {} cache keys", fullKeys.size());
            });
        }
    }

    /**
     * Comprueba si una clave está en caché.
     *
     * @param key clave
     * @return {@code true} si existe; {@code false} si no existe o no se pudo consultar Redis
     */
    public boolean exists(String key) {
        String fullKey = cachePrefix + key;
        return Boolean.TRUE.equals(call("exists", fullKey, () -> redisTemplate.hasKey(fullKey), Boolean.FALSE));
    }

    /**
     * Borra todas las entradas de todas las cachés.
     */
    public void clear() {
        deleteMatching(escapeGlob(cachePrefix) + "*");
    }

    /**
     * Obtiene el tiempo de vida restante.
     *
     * @param key clave
     * @return segundos restantes; -1 si la clave no caduca; -2 si no existe o no se pudo consultar Redis
     */
    public long getTimeToLive(String key) {
        String fullKey = cachePrefix + key;
        Long ttl = call("ttl", fullKey,
                () -> redisTemplate.getExpire(fullKey, TimeUnit.SECONDS), TTL_KEY_NOT_FOUND);
        return ttl != null ? ttl : -1;
    }

    /**
     * Establece la expiración.
     *
     * @param key clave
     * @param ttlSeconds tiempo de vida en segundos
     */
    public void expire(String key, long ttlSeconds) {
        String fullKey = cachePrefix + key;
        run("expire", fullKey, () -> {
            redisTemplate.expire(fullKey, Duration.ofSeconds(ttlSeconds));
            log.debug("Set expiration for key: {} to {} seconds", fullKey, ttlSeconds);
        });
    }

    /**
     * Obtiene un valor de la caché o lo carga, con coalescencia de peticiones.
     *
     * <p>El cargador se ejecuta fuera de cualquier captura: si lanza una excepción, llega tal cual
     * a quien llama y a las peticiones de esta JVM que esperaban el mismo valor. Las instancias
     * que esperaban en otras JVM ejecutan entonces su propia carga.</p>
     *
     * <p>Lo mismo ocurre cuando el resultado es nulo y no se guarda ({@code cacheNull} falso):
     * no hay valor en caché que repartir, así que cada instancia que esperaba ejecuta su
     * propia carga.</p>
     *
     * @param key clave de caché
     * @param loader proveedor que carga el valor si no está en caché
     * @param ttlSeconds tiempo de vida en segundos; si no es positivo, el valor no caduca
     * @param cacheNull indica si se guardan los resultados nulos
     * @param type tipo del valor, con el que se reconstruye al leerlo de la caché
     * @return valor de la caché o cargado
     * @throws CoalesceException si se agota {@link CoalesceSettings#waitTimeout()} esperando
     *                           el resultado de otra petición, o si se interrumpe la espera
     */
    public Object getOrLoad(String key, Supplier<Object> loader, long ttlSeconds, boolean cacheNull, Type type) {
        CompletableFuture<Object> mine = new CompletableFuture<>();
        CompletableFuture<Object> inProgress = localLoads.putIfAbsent(key, mine);
        if (inProgress != null) {
            return awaitLocalLoad(key, inProgress);
        }

        try {
            Object result = loadAcrossInstances(key, loader, ttlSeconds, cacheNull, type);
            mine.complete(result);
            return result;
        } catch (RuntimeException | Error e) {
            mine.completeExceptionally(e);
            throw e;
        } finally {
            localLoads.remove(key, mine);
        }
    }

    /**
     * Obtiene o carga un valor cuyo tipo no se conoce, en el formato con nombres de clase.
     * Es preferible {@link #getOrLoad(String, Supplier, long, boolean, Type)}.
     *
     * @param key clave de caché
     * @param loader proveedor que carga el valor si no está en caché
     * @param ttlSeconds tiempo de vida en segundos; si no es positivo, el valor no caduca
     * @param cacheNull indica si se guardan los resultados nulos
     * @return valor de la caché o cargado
     */
    public Object getOrLoad(String key, Supplier<Object> loader, long ttlSeconds, boolean cacheNull) {
        return getOrLoad(key, loader, ttlSeconds, cacheNull, Object.class);
    }

    /**
     * Espera el resultado de la petición de esta JVM que ya está cargando la clave.
     *
     * @param key clave de caché
     * @param inProgress carga en curso
     * @return valor cargado
     */
    private Object awaitLocalLoad(String key, CompletableFuture<Object> inProgress) {
        try {
            Object result = inProgress.get(settings.waitTimeout().toNanos(), TimeUnit.NANOSECONDS);
            metrics.coalesced();
            return result;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new CoalesceException("Interrupted while waiting for coalesced result for key: " + key, e);
        } catch (ExecutionException e) {
            Throwable cause = e.getCause();
            if (cause instanceof RuntimeException runtime) {
                throw runtime;
            }
            if (cause instanceof Error error) {
                throw error;
            }
            throw new CoalesceException("Failed to get coalesced result for key: " + key, cause);
        } catch (TimeoutException e) {
            metrics.waitTimeout();
            throw new CoalesceException("Timed out waiting for coalesced result for key: " + key, e);
        }
    }

    /**
     * Carga el valor coordinándose con las demás instancias mediante el bloqueo de Redis.
     * Solo la ejecuta una petición por clave en cada JVM.
     *
     * @param key clave de caché
     * @param loader proveedor que carga el valor
     * @param ttlSeconds tiempo de vida en segundos
     * @param cacheNull indica si se guardan los resultados nulos
     * @param type tipo del valor
     * @return valor de la caché o cargado
     */
    private Object loadAcrossInstances(
            String key, Supplier<Object> loader, long ttlSeconds, boolean cacheNull, Type type) {
        String fullKey = cachePrefix + key;
        String lockKey = lockPrefix + key;
        byte[] token = UUID.randomUUID().toString().getBytes(StandardCharsets.UTF_8);
        long deadline = System.nanoTime() + settings.waitTimeout().toNanos();
        BlockingQueue<CoalescedResponseDto> signals = null;

        try {
            while (true) {
                CacheResult<Object> cached = read(fullKey, type);
                if (cached == UNAVAILABLE) {
                    return load(loader);
                }
                if (cached.isCacheHit()) {
                    log.debug("Cache hit for coalesced key: {}", fullKey);
                    if (signals == null) {
                        metrics.hit();
                    } else {
                        // Se esperó a otra instancia y el valor ya está: carga ahorrada.
                        metrics.coalesced();
                    }
                    return cached.getValue();
                }
                if (signals == null) {
                    metrics.miss();
                }

                Boolean acquired = call("lock", lockKey,
                        () -> redisTemplate.opsForValue().setIfAbsent(lockKey, token, settings.lockTimeout()),
                        null);
                if (acquired == null) {
                    return load(loader);
                }
                if (acquired) {
                    log.debug("Acquired lock for key: {}, executing loader", fullKey);
                    return loadAsLockHolder(key, lockKey, token, loader, ttlSeconds, cacheNull, type);
                }

                if (signals == null) {
                    // Se registra la cola y se vuelve a mirar antes de esperar, por si el aviso
                    // se publicó entre la lectura anterior y el registro.
                    signals = new LinkedBlockingQueue<>();
                    signalQueues.put(key, signals);
                    continue;
                }

                long remainingNanos = deadline - System.nanoTime();
                if (remainingNanos <= 0) {
                    metrics.waitTimeout();
                    throw new CoalesceException("Timed out waiting for coalesced result for key: " + key);
                }

                log.debug("Lock not acquired for key: {}, waiting for result", fullKey);
                CoalescedResponseDto signal = signals.poll(
                        Math.min(TimeUnit.MILLISECONDS.toNanos(POLL_INTERVAL_MILLIS), remainingNanos),
                        TimeUnit.NANOSECONDS);
                if (signal != null && !signal.isCached()) {
                    // Quien tenía el bloqueo terminó sin dejar valor (falló, o devolvió un nulo
                    // que no se guarda): no hay nada que esperar, se carga aquí.
                    return loadWithoutLock(key, loader, ttlSeconds, cacheNull, type);
                }
                // Con aviso de valor guardado, sin aviso (pudo perderse) o al caducar el bloqueo
                // ajeno, la siguiente vuelta lo resuelve leyendo la caché o tomando el bloqueo.
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new CoalesceException("Interrupted while waiting for coalesced result for key: " + key, e);
        } finally {
            if (signals != null) {
                signalQueues.remove(key, signals);
            }
        }
    }

    /**
     * Ejecuta la carga teniendo el bloqueo, guarda el resultado, suelta el bloqueo y avisa.
     * Nada de lo que ocurre después de la carga puede hacer fallar la petición.
     *
     * @param key clave de caché
     * @param lockKey clave del bloqueo
     * @param token valor del bloqueo, que identifica a su dueño
     * @param loader proveedor que carga el valor
     * @param ttlSeconds tiempo de vida en segundos
     * @param cacheNull indica si se guardan los resultados nulos
     * @param type tipo del valor
     * @return valor cargado
     */
    private Object loadAsLockHolder(
            String key, String lockKey, byte[] token,
            Supplier<Object> loader, long ttlSeconds, boolean cacheNull, Type type) {

        boolean stored = false;
        ScheduledFuture<?> renewal = scheduleLockRenewal(lockKey, token);
        try {
            Object result = load(loader);
            stored = (result != null || cacheNull) && store(key, result, ttlSeconds, type, true);
            return result;
        } finally {
            renewal.cancel(false);
            releaseLock(lockKey, token);
            publishSignal(key, stored);
        }
    }

    /**
     * Programa la renovación periódica del bloqueo mientras dura la carga.
     * Así una carga más larga que {@link CoalesceSettings#lockTimeout()} conserva su bloqueo y
     * no se ejecuta a la vez en otra instancia. Si esta JVM muere, las renovaciones cesan y el
     * bloqueo caduca solo.
     *
     * @param lockKey clave del bloqueo
     * @param token valor con el que se tomó el bloqueo
     * @return tarea programada, que hay que cancelar al terminar la carga
     */
    private ScheduledFuture<?> scheduleLockRenewal(String lockKey, byte[] token) {
        long lockMillis = settings.lockTimeout().toMillis();
        long periodMillis = Math.max(1, lockMillis / RENEWALS_PER_LOCK_TIMEOUT);
        byte[] ttl = String.valueOf(lockMillis).getBytes(StandardCharsets.UTF_8);
        return lockRenewer.scheduleAtFixedRate(
                () -> quietly("renew", lockKey,
                        () -> redisTemplate.execute(RENEW_LOCK, List.of(lockKey), token, ttl), null),
                periodMillis, periodMillis, TimeUnit.MILLISECONDS);
    }

    private static ScheduledThreadPoolExecutor newLockRenewer() {
        ScheduledThreadPoolExecutor executor = new ScheduledThreadPoolExecutor(1, runnable -> {
            Thread thread = new Thread(runnable, "redis-coalesce-lock-renewal");
            thread.setDaemon(true);
            return thread;
        });
        executor.setRemoveOnCancelPolicy(true);
        return executor;
    }

    /**
     * Detiene la renovación de bloqueos. Spring lo llama al cerrar el contexto.
     */
    @Override
    public void close() {
        lockRenewer.shutdownNow();
    }

    /**
     * Ejecuta la carga sin bloqueo y guarda el resultado.
     *
     * @param key clave de caché
     * @param loader proveedor que carga el valor
     * @param ttlSeconds tiempo de vida en segundos
     * @param cacheNull indica si se guardan los resultados nulos
     * @param type tipo del valor
     * @return valor cargado
     */
    private Object loadWithoutLock(
            String key, Supplier<Object> loader, long ttlSeconds, boolean cacheNull, Type type) {
        Object result = load(loader);
        if (result != null || cacheNull) {
            store(key, result, ttlSeconds, type, true);
        }
        return result;
    }

    /**
     * Ejecuta el cargador y lo cuenta.
     *
     * @param loader proveedor que carga el valor
     * @return valor cargado
     */
    private Object load(Supplier<Object> loader) {
        metrics.load();
        return loader.get();
    }

    /**
     * Lee un valor de la caché.
     * Una entrada que no corresponde al tipo pedido (por ejemplo, escrita por una versión anterior
     * de la clase o de la librería) se borra y cuenta como ausente.
     *
     * @param fullKey clave completa
     * @param type tipo con el que se reconstruye el valor
     * @return acierto con el valor, {@link #ABSENT} si la clave no existe,
     *         o {@link #UNAVAILABLE} si no se pudo consultar Redis
     */
    private CacheResult<Object> read(String fullKey, Type type) {
        return call("read", fullKey, () -> {
            byte[] bytes = redisTemplate.opsForValue().get(fullKey);
            if (bytes == null) {
                return ABSENT;
            }
            try {
                return CacheResult.hit(codec.decode(bytes, type));
            } catch (SerializationException e) {
                log.warn("Discarding unreadable cache entry {}: {}", fullKey, e.getMessage());
                redisTemplate.delete(fullKey);
                return ABSENT;
            }
        }, UNAVAILABLE);
    }

    /**
     * Guarda un valor.
     *
     * @param key clave
     * @param value valor
     * @param ttlSeconds tiempo de vida en segundos; si no es positivo, el valor no caduca
     * @param type tipo del valor
     * @param quiet si es {@code true}, un fallo de Redis o de serialización nunca se propaga
     * @return {@code true} si el valor quedó guardado
     */
    private boolean store(String key, Object value, long ttlSeconds, Type type, boolean quiet) {
        String fullKey = cachePrefix + key;
        Supplier<Boolean> action = () -> {
            byte[] bytes = codec.encode(value, type);
            if (ttlSeconds > 0) {
                redisTemplate.opsForValue().set(fullKey, bytes, Duration.ofSeconds(ttlSeconds));
            } else {
                redisTemplate.opsForValue().set(fullKey, bytes);
            }
            log.debug("Cached value for key: {}", fullKey);
            return Boolean.TRUE;
        };
        return quiet
                ? quietly("write", fullKey, action, Boolean.FALSE)
                : call("write", fullKey, action, Boolean.FALSE);
    }

    /**
     * Borra las claves que coinciden con un patrón, recorriéndolas con SCAN.
     *
     * @param pattern patrón completo
     */
    private void deleteMatching(String pattern) {
        run("evict", pattern, () -> {
            long deleted = RedisKeyScanner.deleteMatching(redisTemplate, pattern);
            log.debug("Evicted {} keys matching pattern: {}", deleted, pattern);
        });
    }

    /**
     * Suelta el bloqueo solo si sigue siendo de esta petición.
     * Si la carga tardó más que el bloqueo, este pudo caducar y pasar a otra instancia.
     *
     * @param lockKey clave del bloqueo
     * @param token valor con el que se tomó el bloqueo
     */
    private void releaseLock(String lockKey, byte[] token) {
        quietly("unlock", lockKey, () -> redisTemplate.execute(RELEASE_LOCK, List.of(lockKey), token), null);
    }

    /**
     * Avisa a las demás instancias de que terminó la carga de una clave.
     *
     * @param key clave de caché
     * @param cached indica si el valor quedó en la caché
     */
    private void publishSignal(String key, boolean cached) {
        CoalescedResponseDto signal = new CoalescedResponseDto(key, cached, LocalDateTime.now());
        quietly("publish", key, () -> redisTemplate.convertAndSend(
                signalChannel, codec.encode(signal, CoalescedResponseDto.class)), null);
    }

    /**
     * Recibe el aviso de que otra instancia (o esta misma) terminó de cargar una clave.
     *
     * @param message mensaje
     * @param pattern patrón del canal; no se usa
     */
    private void handleSignal(Message message, byte[] pattern) {
        try {
            Object payload = codec.decode(message.getBody(), CoalescedResponseDto.class);
            if (payload instanceof CoalescedResponseDto signal && signal.getCoalescingKey() != null) {
                BlockingQueue<CoalescedResponseDto> queue = signalQueues.get(signal.getCoalescingKey());
                if (queue != null) {
                    queue.offer(signal);
                    log.debug("Received coalesce signal for key: {}", signal.getCoalescingKey());
                }
            }
        } catch (RuntimeException e) {
            // Sin aviso, quien espera lo resuelve en su siguiente consulta periódica.
            log.warn("Ignoring unreadable coalesce signal: {}", e.getMessage());
        }
    }

    /**
     * Ejecuta una operación de Redis sin resultado, con el tratamiento de fallos de {@link #call}.
     *
     * @param operation nombre de la operación, para el log
     * @param target clave o patrón afectado, para el log
     * @param action operación
     */
    private void run(String operation, String target, Runnable action) {
        call(operation, target, () -> {
            action.run();
            return null;
        }, null);
    }

    /**
     * Ejecuta una operación de Redis. Si falla y la degradación está activa, se registra el fallo
     * y se devuelve el valor alternativo; si no está activa, el fallo se propaga.
     *
     * @param <T> tipo del resultado
     * @param operation nombre de la operación, para el log
     * @param target clave o patrón afectado, para el log
     * @param action operación
     * @param fallback valor que se devuelve si Redis no está disponible
     * @return resultado de la operación o el valor alternativo
     */
    private <T> T call(String operation, String target, Supplier<T> action, T fallback) {
        return execute(operation, target, action, fallback, settings.failOpen());
    }

    /**
     * Ejecuta una operación de Redis cuyo fallo nunca debe llegar a quien llama, porque ocurre
     * después de que el método de negocio ya se ejecutó.
     *
     * @param <T> tipo del resultado
     * @param operation nombre de la operación, para el log
     * @param target clave o patrón afectado, para el log
     * @param action operación
     * @param fallback valor que se devuelve si la operación falla
     * @return resultado de la operación o el valor alternativo
     */
    private <T> T quietly(String operation, String target, Supplier<T> action, T fallback) {
        return execute(operation, target, action, fallback, true);
    }

    private <T> T execute(String operation, String target, Supplier<T> action, T fallback, boolean swallow) {
        if (breaker.isOpen()) {
            return fallback;
        }
        try {
            T result = action.get();
            breaker.recordSuccess();
            return result;
        } catch (RuntimeException e) {
            if (!swallow) {
                throw e;
            }
            if (settings.failOpen()) {
                breaker.recordFailure(e);
            }
            metrics.redisError(operation);
            logFailure(operation, target, e);
            return fallback;
        }
    }

    /**
     * Registra un fallo de Redis. Para no llenar el log durante una avería, solo uno de cada
     * {@link #WARN_INTERVAL_NANOS} va como aviso; los demás van a depuración.
     *
     * @param operation nombre de la operación
     * @param target clave o patrón afectado
     * @param e excepción lanzada
     */
    private void logFailure(String operation, String target, RuntimeException e) {
        long now = System.nanoTime();
        long last = lastWarnNanos.get();
        boolean due = last == 0 || now - last >= WARN_INTERVAL_NANOS;
        if (due && lastWarnNanos.compareAndSet(last, now == 0 ? 1 : now)) {
            log.warn("Redis {} failed for {}; continuing without cache: {}", operation, target, e.toString());
        } else {
            log.debug("Redis {} failed for {}: {}", operation, target, e.toString());
        }
    }

    /**
     * Escapa los caracteres que Redis interpreta en un patrón, para que un nombre
     * de caché se compare siempre de forma literal.
     *
     * @param text texto literal
     * @return texto con los caracteres de patrón escapados
     */
    private static String escapeGlob(String text) {
        StringBuilder escaped = new StringBuilder(text.length());
        for (char c : text.toCharArray()) {
            if (GLOB_CHARACTERS.indexOf(c) >= 0) {
                escaped.append('\\');
            }
            escaped.append(c);
        }
        return escaped.toString();
    }
}
