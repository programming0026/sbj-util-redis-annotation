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
 * Simulates an application instance that uses the library: it has its own connection,
 * its template, its message listener and its cache manager.
 * Creating two instances against the same Redis is equivalent to having two replicas of the service.
 *
 * <p>The components are assembled the same way as in {@code CacheConfig}, with the library's
 * default serializer and {@code ObjectMapper}.</p>
 *
 * @author Java Universe
 */
public final class RedisInstance implements AutoCloseable {

    /** Connection factory of this instance. */
    private final LettuceConnectionFactory connectionFactory;

    /** Redis template with the library's serializers. */
    private final RedisTemplate<String, Object> template;

    /** Pub/sub message listener. */
    private final RedisMessageListenerContainer listenerContainer;

    /** Coalescing cache manager. */
    private final CoalesceCacheManager cacheManager;

    /** Programmatic cache service. */
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
     * Wraps a service with the library's three aspects, as Spring would.
     *
     * @param <T> service type
     * @param target real service
     * @return proxy intercepted by the aspects
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
     * Reads a key as it is in Redis, without deserializing.
     *
     * @param key full key
     * @return content as text, or {@code null} if the key does not exist
     */
    public String rawValue(String key) {
        try (RedisConnection connection = connectionFactory.getConnection()) {
            byte[] bytes = connection.stringCommands().get(key.getBytes(StandardCharsets.UTF_8));
            return bytes == null ? null : new String(bytes, StandardCharsets.UTF_8);
        }
    }

    /**
     * Deletes all the keys in Redis.
     */
    public void flushAll() {
        try (RedisConnection connection = connectionFactory.getConnection()) {
            connection.serverCommands().flushAll();
        }
    }

    /**
     * Stops the listener and closes the connections.
     * Errors are ignored because Redis may already be stopped.
     */
    @Override
    public void close() {
        try {
            listenerContainer.stop();
            listenerContainer.destroy();
        } catch (Exception e) {
            // Redis may have been stopped on purpose by the test.
        }
        try {
            connectionFactory.destroy();
        } catch (RuntimeException e) {
            // Same as above.
        }
    }
}
