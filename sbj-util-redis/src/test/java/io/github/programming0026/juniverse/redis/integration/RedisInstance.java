package io.github.programming0026.juniverse.redis.integration;

import io.github.programming0026.juniverse.redis.aspect.CoalesceCachingAspect;
import io.github.programming0026.juniverse.redis.aspect.CoalesceEvictAspect;
import io.github.programming0026.juniverse.redis.aspect.CoalescePutAspect;
import io.github.programming0026.juniverse.redis.dto.CoalesceSettings;
import io.github.programming0026.juniverse.redis.config.CustomJacksonJsonRedisSerializer;
import io.github.programming0026.juniverse.redis.config.DefaultObjectMapperConfig;
import io.github.programming0026.juniverse.redis.service.CacheValueCodec;
import io.github.programming0026.juniverse.redis.service.CoalesceCacheManager;
import io.github.programming0026.juniverse.redis.service.RedisCacheService;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import org.springframework.aop.aspectj.annotation.AspectJProxyFactory;
import org.springframework.data.redis.connection.RedisConnection;
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.listener.RedisMessageListenerContainer;
import org.springframework.data.redis.serializer.RedisSerializer;
import org.springframework.data.redis.serializer.StringRedisSerializer;

/**
 * Simula una instancia de aplicación que usa la librería: tiene su propia conexión,
 * su plantilla, su escucha de mensajes y su administrador de caché.
 * Crear dos instancias contra el mismo Redis equivale a tener dos réplicas del servicio.
 *
 * <p>Los componentes se montan igual que en {@code CacheConfig}, con el serializador
 * y el {@code ObjectMapper} predeterminados de la librería.</p>
 *
 * @author Java Universe
 */
public final class RedisInstance implements AutoCloseable {

    /** Fábrica de conexiones de esta instancia. */
    private final LettuceConnectionFactory connectionFactory;

    /** Plantilla de Redis con los serializadores de la librería. */
    private final RedisTemplate<String, Object> template;

    /** Escucha de mensajes pub/sub. */
    private final RedisMessageListenerContainer listenerContainer;

    /** Administrador de caché con coalescencia. */
    private final CoalesceCacheManager cacheManager;

    /** Servicio de caché programático. */
    private final RedisCacheService cacheService;

    public RedisInstance(LettuceConnectionFactory connectionFactory) {
        this(connectionFactory, CoalesceSettings.defaults());
    }

    public RedisInstance(LettuceConnectionFactory connectionFactory, CoalesceSettings settings) {
        this(connectionFactory, settings, "");
    }

    public RedisInstance(LettuceConnectionFactory connectionFactory, CoalesceSettings settings, String keyPrefix) {
        this.connectionFactory = connectionFactory;
        connectionFactory.afterPropertiesSet();

        template = new RedisTemplate<>();
        template.setConnectionFactory(connectionFactory);
        StringRedisSerializer stringSerializer = new StringRedisSerializer();
        template.setKeySerializer(stringSerializer);
        template.setHashKeySerializer(stringSerializer);
        CustomJacksonJsonRedisSerializer<Object> jsonSerializer = new CustomJacksonJsonRedisSerializer<>(
                new DefaultObjectMapperConfig().configure(), Object.class);
        template.setValueSerializer(jsonSerializer);
        template.setHashValueSerializer(jsonSerializer);
        template.afterPropertiesSet();

        listenerContainer = new RedisMessageListenerContainer();
        listenerContainer.setConnectionFactory(connectionFactory);
        RedisTemplate<String, byte[]> rawTemplate = new RedisTemplate<>();
        rawTemplate.setConnectionFactory(connectionFactory);
        rawTemplate.setKeySerializer(stringSerializer);
        rawTemplate.setHashKeySerializer(stringSerializer);
        rawTemplate.setValueSerializer(RedisSerializer.byteArray());
        rawTemplate.setHashValueSerializer(RedisSerializer.byteArray());
        rawTemplate.afterPropertiesSet();
        cacheManager = new CoalesceCacheManager(rawTemplate, listenerContainer,
                new CacheValueCodec(new DefaultObjectMapperConfig().configure()), settings, keyPrefix);
        listenerContainer.afterPropertiesSet();
        listenerContainer.start();

        cacheService = new RedisCacheService(template, keyPrefix, Map.of());
    }

    public RedisTemplate<String, Object> template() {
        return template;
    }

    public CoalesceCacheManager cacheManager() {
        return cacheManager;
    }

    public RedisCacheService cacheService() {
        return cacheService;
    }

    /**
     * Envuelve un servicio con los tres aspectos de la librería, como haría Spring.
     *
     * @param <T> tipo del servicio
     * @param target servicio real
     * @return proxy interceptado por los aspectos
     */
    public <T> T proxy(T target) {
        AspectJProxyFactory factory = new AspectJProxyFactory(target);
        factory.setProxyTargetClass(true);
        factory.addAspect(new CoalesceCachingAspect(cacheManager));
        factory.addAspect(new CoalescePutAspect(cacheManager));
        factory.addAspect(new CoalesceEvictAspect(cacheManager));
        return factory.getProxy();
    }

    /**
     * Lee una clave tal como está en Redis, sin deserializar.
     *
     * @param key clave completa
     * @return contenido como texto, o {@code null} si la clave no existe
     */
    public String rawValue(String key) {
        try (RedisConnection connection = connectionFactory.getConnection()) {
            byte[] bytes = connection.stringCommands().get(key.getBytes(StandardCharsets.UTF_8));
            return bytes == null ? null : new String(bytes, StandardCharsets.UTF_8);
        }
    }

    /**
     * Borra todas las claves de Redis.
     */
    public void flushAll() {
        try (RedisConnection connection = connectionFactory.getConnection()) {
            connection.serverCommands().flushAll();
        }
    }

    /**
     * Detiene la escucha y cierra las conexiones.
     * Los errores se ignoran porque Redis puede estar ya detenido.
     */
    @Override
    public void close() {
        try {
            listenerContainer.stop();
            listenerContainer.destroy();
        } catch (Exception e) {
            // Redis puede estar detenido a propósito por la prueba.
        }
        try {
            connectionFactory.destroy();
        } catch (RuntimeException e) {
            // Igual que arriba.
        }
    }
}
