package io.github.programming0026.juniverse.redis.integration;

import io.github.programming0026.juniverse.redis.integration.dto.Item;
import io.github.programming0026.juniverse.redis.annotation.CoalesceCacheable;
import io.github.programming0026.juniverse.redis.annotation.CoalesceEvict;
import io.github.programming0026.juniverse.redis.service.CoalesceCacheManager;
import io.github.programming0026.juniverse.redis.service.RedisCacheService;
import io.github.programming0026.juniverse.redis.service.RedisHealthChecker;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.BooleanSupplier;

import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.MapPropertySource;

/**
 * Test application that starts a real Spring context with {@code @EnableRedisLibrary}.
 * Unlike {@link RedisInstance}, here the connection is created by the library itself from
 * the annotation, so the configuration is tested the way an application will use it.
 *
 * <p>Data that changes on every run (container addresses and ports) arrives
 * as properties and is used in the annotation with placeholders.</p>
 *
 * @author Java Universe
 */
public final class LibraryApplication implements AutoCloseable {

    /** Spring context. */
    private final AnnotationConfigApplicationContext context;

    private LibraryApplication(AnnotationConfigApplicationContext context) {
        this.context = context;
    }

    /**
     * Starts the application.
     *
     * @param configuration class annotated with {@code @EnableRedisLibrary}
     * @param properties properties for the annotation's placeholders
     * @return started application
     */
    public static LibraryApplication start(Class<?> configuration, Map<String, Object> properties) {
        AnnotationConfigApplicationContext context = new AnnotationConfigApplicationContext();
        context.getEnvironment().getPropertySources().addFirst(new MapPropertySource("integration", properties));
        context.register(configuration, Beans.class);
        try {
            context.refresh();
        } catch (RuntimeException e) {
            context.close();
            throw e;
        }
        return new LibraryApplication(context);
    }

    public CatalogService catalog() {
        return context.getBean(CatalogService.class);
    }

    public CoalesceCacheManager coalesceCache() {
        return context.getBean(CoalesceCacheManager.class);
    }

    public RedisCacheService cacheService() {
        return context.getBean(RedisCacheService.class);
    }

    public RedisHealthChecker health() {
        return context.getBean(RedisHealthChecker.class);
    }

    /**
     * Tells whether the cache really works: what is stored can be read.
     *
     * @return {@code true} if a write followed by a read returns the value
     */
    public boolean cacheWorks() {
        String key = "probe:" + System.nanoTime();
        coalesceCache().put(key, "ok", 30, String.class);
        return "ok".equals(coalesceCache().lookup(key, String.class).getValue());
    }

    /**
     * Waits for a condition to be met.
     *
     * @param condition condition that is queried periodically
     * @param timeout maximum wait
     * @return {@code true} if it was met in time
     */
    public static boolean await(BooleanSupplier condition, Duration timeout) {
        long deadline = System.nanoTime() + timeout.toNanos();
        while (System.nanoTime() < deadline) {
            try {
                if (condition.getAsBoolean()) {
                    return true;
                }
            } catch (RuntimeException e) {
                // It is retried until the wait runs out.
            }
            try {
                Thread.sleep(250);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return false;
            }
        }
        return false;
    }

    @Override
    public void close() {
        context.close();
    }

    /**
     * Business service with the three forms of cache.
     */
    public static class CatalogService {

        /** Number of real executions. */
        private final AtomicInteger calls = new AtomicInteger();

        /**
         * Returns how many times some real method ran. It is a method because the bean
         * that Spring delivers is a proxy, whose fields are empty.
         *
         * @return number of executions
         */
        public int callCount() {
            return calls.get();
        }

        @CoalesceCacheable(value = "items", key = "#id", ttl = 120)
        public Item find(Long id) {
            calls.incrementAndGet();
            return new Item(id, "item-" + id, List.of("a", "b"));
        }

        @CoalesceEvict(value = "items", key = "#id")
        public void forget(Long id) {
            // Only intercepted.
        }

        @Cacheable("springItems")
        public Item findWithSpringCache(Long id) {
            calls.incrementAndGet();
            return new Item(id, "spring-" + id, List.of("c"));
        }

        @CacheEvict("springItems")
        public void forgetWithSpringCache(Long id) {
            // Only intercepted.
        }
    }

    /**
     * Beans of the test application itself.
     */
    @Configuration
    public static class Beans {

        @Bean
        public CatalogService catalogService() {
            return new CatalogService();
        }
    }
}
