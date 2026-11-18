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
 * Arranca un contexto real de Spring con {@code @EnableRedisLibrary} contra Redis.
 * Comprueba que los beans de la librería se registran, que se retiran cuando la aplicación
 * define los suyos y que las anotaciones funcionan de extremo a extremo.
 *
 * <p>La aplicación de prueba define su propia fábrica de conexiones, que apunta al contenedor:
 * el puerto de {@code @HostEntry} es una constante y el del contenedor cambia en cada ejecución.
 * Eso mismo prueba que la librería no crea una segunda fábrica.</p>
 *
 * @author Java Universe
 */
@Testcontainers(disabledWithoutDocker = true)
class SpringContextIT {

    /** Contexto de la prueba en curso. */
    private AnnotationConfigApplicationContext context;

    /**
     * Servicio de negocio de la aplicación de prueba.
     */
    public static class UserService {

        /** Número de ejecuciones reales. */
        private final AtomicInteger calls = new AtomicInteger();

        /**
         * Devuelve cuántas veces se ejecutó algún método real. Es un método y no un acceso al
         * campo porque el bean que entrega Spring es un proxy, cuyos campos están vacíos.
         *
         * @return número de ejecuciones
         */
        public int callCount() {
            return calls.get();
        }

        @CoalesceCacheable(value = "${app.users.cache}", key = "#id")
        public User find(Long id) {
            calls.incrementAndGet();
            return new User(id, "user-" + id, List.of("admin"));
        }

        @CoalesceEvict(value = "${app.users.cache}", key = "#id")
        public void forget(Long id) {
            // Solo se intercepta.
        }

        @Cacheable("springUsers")
        public String findWithSpringCache(Long id) {
            calls.incrementAndGet();
            return "spring-" + id;
        }
    }

    /**
     * Aplicación que usa la librería con su propia fábrica de conexiones.
     */
    @Configuration
    @EnableRedisLibrary(
            hostEntries = @HostEntry(host = "unused.invalid"),
            keyPrefix = "context",
            ttlEntries = @TTLEntry(name = "users", ttl = 5))
    @Import(ApplicationBeans.class)
    public static class Application {
    }

    /**
     * Beans propios de la aplicación de prueba.
     */
    @Configuration
    public static class ApplicationBeans {

        @Bean
        public RedisConnectionFactory applicationConnectionFactory() {
            return RedisContainers.standaloneFactory();
        }

        @Bean
        public UserService userService() {
            return new UserService();
        }
    }

    /**
     * La misma aplicación, que además define su propio administrador de caché de Spring.
     */
    @Configuration
    @EnableRedisLibrary(hostEntries = @HostEntry(host = "unused.invalid"), keyPrefix = "context")
    @Import(ApplicationBeans.class)
    public static class ApplicationWithOwnCacheManager {

        @Bean
        public CacheManager applicationCacheManager() {
            return new ConcurrentMapCacheManager();
        }
    }

    /**
     * La misma aplicación con un registro de métricas de Micrometer.
     */
    @Configuration
    @EnableRedisLibrary(hostEntries = @HostEntry(host = "unused.invalid"), keyPrefix = "context")
    @Import(ApplicationBeans.class)
    public static class ApplicationWithMetrics {

        @Bean
        public MeterRegistry meterRegistry() {
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
     * Vacía Redis y cierra el contexto.
     */
    @AfterEach
    public void closeContext() {
        if (context != null) {
            try (RedisConnection connection = context.getBean(RedisConnectionFactory.class).getConnection()) {
                connection.serverCommands().flushAll();
            }
            context.close();
        }
        System.clearProperty("app.users.cache");
    }

    /**
     * La librería registra sus beans y usa la fábrica de conexiones de la aplicación
     * en lugar de crear otra.
     */
    @Test
    public void context_ShouldRegisterLibraryBeansAndReuseApplicationConnectionFactory() {
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
     * Las anotaciones funcionan a través del proxy que crea Spring: placeholders resueltos,
     * record reconstruido desde Redis, invalidación y prefijo de la aplicación en las claves.
     */
    @Test
    public void annotations_ShouldWorkThroughSpringProxies() {
        start(Application.class);
        UserService service = context.getBean(UserService.class);

        User first = service.find(1L);
        User second = service.find(1L);

        assertEquals(new User(1L, "user-1", List.of("admin")), second);
        assertEquals(first, second);
        assertEquals(1, service.callCount());
        assertTrue(context.getBean(CoalesceCacheManager.class).exists("users:1"));
        // TTL de @TTLEntry (5 minutos), porque la anotación no indica ttl.
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
     * El {@code @Cacheable} de Spring usa el administrador de caché de la librería.
     */
    @Test
    public void springCacheable_ShouldUseLibraryCacheManager() {
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
     * Con un registro de Micrometer en el contexto, la librería publica sus contadores.
     */
    @Test
    public void metrics_ShouldBePublishedWhenApplicationHasMeterRegistry() {
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
     * Si la aplicación define su administrador de caché de Spring, la librería no registra el suyo,
     * y sus propias anotaciones siguen funcionando.
     */
    @Test
    public void context_ShouldBackOffWhenApplicationDefinesItsOwnCacheManager() {
        start(ApplicationWithOwnCacheManager.class);
        UserService service = context.getBean(UserService.class);

        assertEquals(1, context.getBeansOfType(CacheManager.class).size());
        assertInstanceOf(ConcurrentMapCacheManager.class, context.getBean(CacheManager.class));

        service.find(2L);
        service.find(2L);
        assertEquals(1, service.callCount());
    }
}
