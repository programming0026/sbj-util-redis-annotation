package io.github.programming0026.juniverse.redis.integration;

import io.github.programming0026.juniverse.redis.integration.dto.User;
import io.github.programming0026.juniverse.redis.annotation.CoalesceCacheable;
import io.github.programming0026.juniverse.redis.annotation.CoalesceEvict;
import io.github.programming0026.juniverse.redis.annotation.EnableRedisLibrary;
import io.github.programming0026.juniverse.redis.annotation.HostEntry;
import io.github.programming0026.juniverse.redis.annotation.TTLEntry;
import io.github.programming0026.juniverse.redis.config.FailOpenCacheManager;
import io.github.programming0026.juniverse.redis.service.CacheOperationBuilder;
import io.github.programming0026.juniverse.redis.service.CoalesceCacheManager;
import io.github.programming0026.juniverse.redis.service.RedisCacheService;
import io.github.programming0026.juniverse.redis.service.RedisHealthChecker;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.cache.CacheManager;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.cache.concurrent.ConcurrentMapCacheManager;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.data.redis.cache.RedisCacheManager;
import org.springframework.data.redis.connection.RedisConnection;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.core.RedisTemplate;
import org.testcontainers.junit.jupiter.Testcontainers;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Starts a real Spring context with {@code @EnableRedisLibrary} against Redis.
 * It checks that the library's beans are registered, that they back off when the application
 * defines its own, and that the annotations work end to end.
 *
 * <p>The test application defines its own connection factory, which points to the container:
 * the port in {@code @HostEntry} is a constant and the container's changes on every run.
 * That itself proves that the library does not create a second factory.</p>
 *
 * @author Java Universe
 */
@Testcontainers(disabledWithoutDocker = true)
class SpringContextIT {

    /** Context of the current test. */
    private AnnotationConfigApplicationContext context;

    /**
     * Business service of the test application.
     */
     public static class UserService {

        /** Number of real executions. */
        private final AtomicInteger calls = new AtomicInteger();

        /**
         * Returns how many times some real method ran. It is a method and not a field access
         * because the bean that Spring delivers is a proxy, whose fields are empty.
         *
         * @return number of executions
         */
         int callCount() {
            return calls.get();
        }

        @CoalesceCacheable(value = "${app.users.cache}", key = "#id")
         User find(Long id) {
            calls.incrementAndGet();
            return new User(id, "user-" + id, List.of("admin"));
        }

        @CoalesceEvict(value = "${app.users.cache}", key = "#id")
         void forget(Long id) {
            // Only intercepted.
        }

        @Cacheable("springUsers")
         String findWithSpringCache(Long id) {
            calls.incrementAndGet();
            return "spring-" + id;
        }
    }

    /**
     * Application that uses the library with its own connection factory.
     */
    @Configuration
    @EnableRedisLibrary(
            hostEntries = @HostEntry(host = "unused.invalid"),
            keyPrefix = "context",
            ttlEntries = @TTLEntry(name = "users", ttl = 5))
    @Import(ApplicationBeans.class)
     static class Application {
    }

    /**
     * Beans of the test application itself.
     */
    @Configuration
     static class ApplicationBeans {

        @Bean
         RedisConnectionFactory applicationConnectionFactory() {
            return RedisContainers.standaloneFactory();
        }

        @Bean
         UserService userService() {
            return new UserService();
        }
    }

    /**
     * The same application, which also defines its own Spring cache manager.
     */
    @Configuration
    @EnableRedisLibrary(hostEntries = @HostEntry(host = "unused.invalid"), keyPrefix = "context")
    @Import(ApplicationBeans.class)
     static class ApplicationWithOwnCacheManager {

        @Bean
         CacheManager applicationCacheManager() {
            return new ConcurrentMapCacheManager();
        }
    }

    /**
     * The same application with a Micrometer metrics registry.
     */
    @Configuration
    @EnableRedisLibrary(hostEntries = @HostEntry(host = "unused.invalid"), keyPrefix = "context")
    @Import(ApplicationBeans.class)
     static class ApplicationWithMetrics {

        @Bean
         MeterRegistry meterRegistry() {
            return new SimpleMeterRegistry();
        }
    }

    private AnnotationConfigApplicationContext start(Class<?> application) {
        context = new AnnotationConfigApplicationContext();
        context.getEnvironment().getSystemProperties().put("app.users.cache", "users");
        context.register(application);
        context.refresh();
        return context;
    }

    /**
     * Flushes Redis and closes the context.
     */
    @AfterEach
     void closeContext() {
        if (context != null) {
            try (RedisConnection connection = context.getBean(RedisConnectionFactory.class).getConnection()) {
                connection.serverCommands().flushAll();
            }
            context.close();
        }
        System.clearProperty("app.users.cache");
    }

    /**
     * The library registers its beans and uses the application's connection factory
     * instead of creating another one.
     */
    @Test
     void context_ShouldRegisterLibraryBeansAndReuseApplicationConnectionFactory() {
        start(Application.class);

        assertEquals(1, context.getBeansOfType(RedisConnectionFactory.class).size());
        assertSame(context.getBean("applicationConnectionFactory"), context.getBean(RedisConnectionFactory.class));
        assertNotNull(context.getBean("redisTemplate", RedisTemplate.class));
        assertNotNull(context.getBean(CoalesceCacheManager.class));
        assertNotNull(context.getBean(RedisCacheService.class));
        assertNotNull(context.getBean(CacheOperationBuilder.Factory.class));
        FailOpenCacheManager cacheManager =
                assertInstanceOf(FailOpenCacheManager.class, context.getBean(CacheManager.class));
        assertInstanceOf(RedisCacheManager.class, cacheManager.getDelegate());
        assertTrue(context.getBean(RedisHealthChecker.class).isRedisActive().isConnected());
    }

    /**
     * The annotations work through the proxy Spring creates: placeholders resolved,
     * record rebuilt from Redis, eviction and application prefix in the keys.
     */
    @Test
     void annotations_ShouldWorkThroughSpringProxies() {
        start(Application.class);
        UserService service = context.getBean(UserService.class);

        User first = service.find(1L);
        User second = service.find(1L);

        assertEquals(new User(1L, "user-1", List.of("admin")), second);
        assertEquals(first, second);
        assertEquals(1, service.callCount());
        assertTrue(context.getBean(CoalesceCacheManager.class).exists("users:1"));
        // TTL from @TTLEntry (5 minutes), because the annotation does not specify ttl.
        long ttl = context.getBean(CoalesceCacheManager.class).getTimeToLive("users:1");
        assertTrue(ttl > 240 && ttl <= 300, "unexpected TTL: " + ttl);
        try (RedisConnection connection = context.getBean(RedisConnectionFactory.class).getConnection()) {
            assertEquals(Boolean.TRUE, connection.keyCommands().exists("context:coalesce:cache:users:1".getBytes()));
        }

        service.forget(1L);
        service.find(1L);
        assertEquals(2, service.callCount());
    }

    /**
     * Spring's {@code @Cacheable} uses the library's cache manager.
     */
    @Test
     void springCacheable_ShouldUseLibraryCacheManager() {
        start(Application.class);
        UserService service = context.getBean(UserService.class);

        assertEquals("spring-7", service.findWithSpringCache(7L));
        assertEquals("spring-7", service.findWithSpringCache(7L));

        assertEquals(1, service.callCount());
        try (RedisConnection connection = context.getBean(RedisConnectionFactory.class).getConnection()) {
            assertEquals(Boolean.TRUE, connection.keyCommands().exists("context:springUsers::7".getBytes()));
        }
    }

    /**
     * With a Micrometer registry in the context, the library publishes its counters.
     */
    @Test
     void metrics_ShouldBePublishedWhenApplicationHasMeterRegistry() {
        start(ApplicationWithMetrics.class);
        UserService service = context.getBean(UserService.class);
        MeterRegistry registry = context.getBean(MeterRegistry.class);

        service.find(3L);
        service.find(3L);
        service.find(3L);

        assertEquals(1, registry.get("sbj.redis.cache.loads").counter().count());
        assertEquals(1, registry.get("sbj.redis.cache.requests").tag("result", "miss").counter().count());
        assertEquals(2, registry.get("sbj.redis.cache.requests").tag("result", "hit").counter().count());
    }

    /**
     * If the application defines its Spring cache manager, the library does not register its own,
     * and its own annotations keep working.
     */
    @Test
     void context_ShouldBackOffWhenApplicationDefinesItsOwnCacheManager() {
        start(ApplicationWithOwnCacheManager.class);
        UserService service = context.getBean(UserService.class);

        assertEquals(1, context.getBeansOfType(CacheManager.class).size());
        assertInstanceOf(ConcurrentMapCacheManager.class, context.getBean(CacheManager.class));

        service.find(2L);
        service.find(2L);
        assertEquals(1, service.callCount());
    }
}
