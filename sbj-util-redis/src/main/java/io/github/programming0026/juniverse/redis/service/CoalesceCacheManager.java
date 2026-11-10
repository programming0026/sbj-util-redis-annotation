package io.github.programming0026.juniverse.redis.service;

import io.github.programming0026.juniverse.redis.dto.CoalescedResponseDto;
import io.github.programming0026.juniverse.redis.dto.EvictionEventDto;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.function.Supplier;

import io.github.programming0026.juniverse.redis.exception.CoalesceException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.connection.Message;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.listener.ChannelTopic;
import org.springframework.data.redis.listener.RedisMessageListenerContainer;
import org.springframework.data.redis.serializer.SerializationException;

/**
 * @author Java Universe
 */
@Slf4j
public class CoalesceCacheManager {

    /** Plantilla de Redis. */
    private final RedisTemplate<String, Object> redisTemplate;

    /** Prefijo de las claves de los valores en caché. */
    private static final String CACHE_PREFIX = "coalesce:cache:";

    /** Prefijo de las claves del bloqueo que evita calcular el mismo valor dos veces. */
    private static final String LOCK_PREFIX = "coalesce:lock:";

    /** Duración del bloqueo y tiempo máximo que una petición espera el resultado coalescido. */
    private static final Duration LOCK_TIMEOUT = Duration.ofSeconds(30);

    /** Canal pub/sub por el que se avisa a las demás instancias de que borren una entrada. */
    private static final String EVICT_CHANNEL = "coalesce:evict";

    /** Canal pub/sub por el que se avisa de que un valor ya está calculado. */
    private static final String COALESCE_CHANNEL = "coalesce:ready";

    /** Mapa de peticiones pendientes para la espera local. */
    private final ConcurrentHashMap<String, CompletableFuture<Object>> pendingRequests =
            new ConcurrentHashMap<>();

    public CoalesceCacheManager(
            RedisTemplate<String, Object> redisTemplate,
            RedisMessageListenerContainer listenerContainer) {
        this.redisTemplate = redisTemplate;

        listenerContainer.addMessageListener(
                this::handleEvictionEvent,
                new ChannelTopic(EVICT_CHANNEL)
        );

        listenerContainer.addMessageListener(
                this::handleCoalesceEvent,
                new ChannelTopic(COALESCE_CHANNEL)
        );
    }

    /**
     * Guarda un valor.
     *
     * @param key clave
     * @param value valor
     * @param ttlSeconds tiempo de vida en segundos
     */
    public void put(String key, Object value, long ttlSeconds) {
        String fullKey = CACHE_PREFIX + key;
        if (ttlSeconds > 0) {
            redisTemplate.opsForValue().set(
                    fullKey,
                    value,
                    Duration.ofSeconds(ttlSeconds)
            );
        } else {
            redisTemplate.opsForValue().set(fullKey, value);
        }
        log.debug("Cached value for key: {}", fullKey);
    }

    /**
     * Obtiene.
     *
     * @param key clave
     * @return opcional
     */
    public Optional<Object> get(String key) {
        String fullKey = CACHE_PREFIX + key;
        Object value = redisTemplate.opsForValue().get(fullKey);
        if (value != null) {
            log.debug("Cache hit for key: {}", fullKey);
        } else {
            log.debug("Cache miss for key: {}", fullKey);
        }
        return Optional.ofNullable(value);
    }

    /**
     * Invalida una clave.
     *
     * @param key clave
     */
    public void evict(String key) {
        String fullKey = CACHE_PREFIX + key;
        redisTemplate.delete(fullKey);
        log.info("Evicted cache key: {}", fullKey);

        publishEviction(fullKey);
    }

    /**
     * Invalida todas las entradas de una caché.
     *
     * @param prefix prefijo
     */
    public void evictAll(String prefix) {
        String pattern = CACHE_PREFIX + prefix + "*";
        Set<String> keys = redisTemplate.keys(pattern);

        if (keys != null && !keys.isEmpty()) {
            redisTemplate.delete(keys);
            log.info("Evicted {} keys matching pattern: {}", keys.size(), pattern);

            keys.forEach(this::publishEviction);
        }
    }

    /**
     * Invalida las claves que coinciden con un patrón.
     *
     * @param pattern patrón de claves
     */
    public void evictPattern(String pattern) {
        String fullPattern = CACHE_PREFIX + pattern;
        Set<String> keys = redisTemplate.keys(fullPattern);

        if (keys != null && !keys.isEmpty()) {
            redisTemplate.delete(keys);
            log.info("Evicted {} keys matching pattern: {}", keys.size(), fullPattern);

            keys.forEach(this::publishEviction);
        }
    }

    /**
     * Invalida varias claves.
     *
     * @param keys claves
     */
    public void evictMultiple(Collection<String> keys) {
        List<String> fullKeys = keys.stream()
                .map(key -> CACHE_PREFIX + key)
                .toList();

        if (!fullKeys.isEmpty()) {
            redisTemplate.delete(fullKeys);
            log.info("Evicted {} cache keys", fullKeys.size());

            fullKeys.forEach(this::publishEviction);
        }
    }

    public boolean exists(String key) {
        String fullKey = CACHE_PREFIX + key;
        return Boolean.TRUE.equals(redisTemplate.hasKey(fullKey));
    }

    /**
     * Limpia.
     */
    public void clear() {
        String pattern = CACHE_PREFIX + "*";
        Set<String> keys = redisTemplate.keys(pattern);

        if (keys != null && !keys.isEmpty()) {
            redisTemplate.delete(keys);
            log.warn("Cleared all cache entries: {} keys", keys.size());
        }
    }

    /**
     * Obtiene el tiempo de vida restante.
     *
     * @param key clave
     * @return tiempo de vida restante
     */
    public long getTimeToLive(String key) {
        String fullKey = CACHE_PREFIX + key;
        Long ttl = redisTemplate.getExpire(fullKey, TimeUnit.SECONDS);
        return ttl != null ? ttl : -1;
    }

    /**
     * Establece la expiración.
     *
     * @param key clave
     * @param ttlSeconds tiempo de vida en segundos
     */
    public void expire(String key, long ttlSeconds) {
        String fullKey = CACHE_PREFIX + key;
        redisTemplate.expire(fullKey, Duration.ofSeconds(ttlSeconds));
        log.debug("Set expiration for key: {} to {} seconds", fullKey, ttlSeconds);
    }

    /**
     * Obtiene o carga un valor con coalescencia de peticiones mediante pub/sub de Redis.
     * Solo una instancia ejecuta el cargador; las demás esperan el resultado.
     *
     * @param key clave de caché
     * @param loader proveedor que carga el valor si no está en caché
     * @param ttlSeconds TTL en segundos
     * @param cacheNull indica si se guardan los resultados nulos
     * @return valor de la caché o cargado
     */
    public Object getOrLoad(String key, Supplier<Object> loader, long ttlSeconds, boolean cacheNull) {
        String fullKey = CACHE_PREFIX + key;
        String lockKey = LOCK_PREFIX + key;
        String requestId = UUID.randomUUID().toString();

        Object cached = redisTemplate.opsForValue().get(fullKey);
        if (cached != null) {
            log.debug("Cache hit for coalesced key: {}", fullKey);
            return cached;
        }

        Boolean acquired = redisTemplate.opsForValue().setIfAbsent(
                lockKey,
                requestId,
                LOCK_TIMEOUT
        );

        if (Boolean.TRUE.equals(acquired)) {

            log.debug("Acquired lock for key: {}, executing loader", fullKey);
            try {
                Object result = loader.get();


                if (result != null || cacheNull) {
                    put(key, result, ttlSeconds);
                }

                publishCoalesceResult(key, result, null);

                return result;
            } catch (Exception e) {
                publishCoalesceResult(key, null, e);
                throw e;
            } finally {
                redisTemplate.delete(lockKey);
            }
        } else {
            log.debug("Lock not acquired for key: {}, waiting for result", fullKey);
            return waitForResult(key, ttlSeconds);
        }
    }

    /**
     * Espera un resultado coalescido por pub/sub.
     * Todas las peticiones que esperan la misma clave comparten un único future, de modo que
     * el aviso del resultado las despierta a todas. La espera nunca supera la duración del bloqueo.
     *
     * @param key clave de caché
     * @param ttlSeconds TTL en segundos; acota la espera cuando es menor que el bloqueo
     * @return valor cargado
     */
    private Object waitForResult(String key, long ttlSeconds) {
        long lockSeconds = LOCK_TIMEOUT.toSeconds();
        long timeout = ttlSeconds > 0 ? Math.min(ttlSeconds, lockSeconds) : lockSeconds;
        CompletableFuture<Object> future = pendingRequests.computeIfAbsent(key, k -> new CompletableFuture<>());
        String fullKey = CACHE_PREFIX + key;

        try {
            // El resultado pudo publicarse antes de registrar el future: se revisa la caché otra vez.
            Object cached = redisTemplate.opsForValue().get(fullKey);
            if (cached != null) {
                return cached;
            }
            return future.get(timeout, TimeUnit.SECONDS);
        } catch(InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new CoalesceException("Failed to get coalesced result for key: " + key, e);
        } catch (ExecutionException | TimeoutException e) {
            log.warn("Timeout or error waiting for coalesced result for key {} {}", key, e.getMessage());
            Object cached = redisTemplate.opsForValue().get(fullKey);

            if (cached != null) {
                return cached;
            }
            throw new CoalesceException("Failed to get coalesced result for key: " + key, e);
        } finally {
            // Solo se quita si sigue siendo el mismo future; los demás que esperan ya tienen su referencia.
            pendingRequests.remove(key, future);
        }
    }

    /**
     * Publica el resultado coalescido a todos los suscriptores en espera.
     *
     * @param key clave de caché
     * @param result resultado
     * @param error error ocurrido, si lo hubo
     */
    private void publishCoalesceResult(String key, Object result, Throwable error) {
        CoalescedResponseDto response = new CoalescedResponseDto(
                UUID.randomUUID().toString(),
                key,
                result,
                error,
                LocalDateTime.now()
        );
        redisTemplate.convertAndSend(COALESCE_CHANNEL, response);
        log.debug("Published coalesce result for key: {}", key);
    }

    /**
     * Procesa los eventos de coalescencia de pub/sub.
     *
     * @param message mensaje
     * @param pattern patrón de claves
     */
    private void handleCoalesceEvent(Message message, byte[] pattern) {
        try {
            CoalescedResponseDto response = (CoalescedResponseDto) redisTemplate
                    .getValueSerializer().deserialize(message.getBody());

            if (response != null) {
                String key = response.getCoalescingKey();
                CompletableFuture<Object> future = pendingRequests.get(key);

                if (future != null) {
                    if (response.getError() != null) {
                        future.completeExceptionally(response.getError());
                    } else {
                        future.complete(response.getResult());
                    }
                    log.debug("Completed pending request for key: {}", key);
                }
            }
        } catch (SerializationException e) {
            log.error("Error handling coalesce event {}", e.getMessage());
        }
    }

    /**
     * Publica la invalidación.
     *
     * @param key clave
     */
    private void publishEviction(String key) {
        EvictionEventDto event = new EvictionEventDto(key, LocalDateTime.now());
        redisTemplate.convertAndSend(EVICT_CHANNEL, event);
    }

    /**
     * Procesa el evento de invalidación.
     *
     * @param message mensaje
     * @param pattern patrón de claves
     */
    private void handleEvictionEvent(Message message, byte[] pattern) {
        try {
            EvictionEventDto event = (EvictionEventDto) redisTemplate
                    .getValueSerializer().deserialize(message.getBody());

            if (event != null) {
                log.debug("Received eviction event for key: {}", event.getKey());
            }
        } catch (SerializationException e) {
            log.error("Error handling eviction event {}", e.getMessage());
        }
    }
}
