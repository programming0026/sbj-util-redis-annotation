package io.github.programming0026.juniverse.redis.integration;

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
 * Aplicación de prueba que arranca un contexto real de Spring con {@code @EnableRedisLibrary}.
 * A diferencia de {@link RedisInstance}, aquí la conexión la crea la propia librería a partir de
 * la anotación, así que se prueba la configuración tal como la usará una aplicación.
 *
 * <p>Los datos que cambian en cada ejecución (direcciones y puertos de los contenedores) llegan
 * como propiedades y se usan en la anotación con placeholders.</p>
 *
 * @author Java Universe
 */
final class LibraryApplication implements AutoCloseable {

    /** Contexto de Spring. */
    private final AnnotationConfigApplicationContext context;

    private LibraryApplication(AnnotationConfigApplicationContext context) {
        this.context = context;
    }

    /**
     * Arranca la aplicación.
     *
     * @param configuration clase anotada con {@code @EnableRedisLibrary}
     * @param properties propiedades para los placeholders de la anotación
     * @return aplicación arrancada
     */
    static LibraryApplication start(Class<?> configuration, Map<String, Object> properties) {
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

    CatalogService catalog() {
        return context.getBean(CatalogService.class);
    }

    CoalesceCacheManager coalesceCache() {
        return context.getBean(CoalesceCacheManager.class);
    }

    RedisCacheService cacheService() {
        return context.getBean(RedisCacheService.class);
    }

    RedisHealthChecker health() {
        return context.getBean(RedisHealthChecker.class);
    }

    /**
     * Indica si la caché funciona de verdad: lo que se guarda se puede leer.
     *
     * @return {@code true} si una escritura seguida de una lectura devuelve el valor
     */
    boolean cacheWorks() {
        String key = "probe:" + System.nanoTime();
        coalesceCache().put(key, "ok", 30, String.class);
        return "ok".equals(coalesceCache().lookup(key, String.class).getValue());
    }

    /**
     * Espera a que se cumpla una condición.
     *
     * @param condition condición que se consulta periódicamente
     * @param timeout espera máxima
     * @return {@code true} si se cumplió a tiempo
     */
    static boolean await(BooleanSupplier condition, Duration timeout) {
        long deadline = System.nanoTime() + timeout.toNanos();
        while (System.nanoTime() < deadline) {
            try {
                if (condition.getAsBoolean()) {
                    return true;
                }
            } catch (RuntimeException e) {
                // Se vuelve a intentar hasta agotar la espera.
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

    /** Artículo del catálogo. */
    public record Item(Long id, String name, List<String> tags) {
    }

    /**
     * Servicio de negocio con las tres formas de caché.
     */
    public static class CatalogService {

        /** Número de ejecuciones reales. */
        private final AtomicInteger calls = new AtomicInteger();

        /**
         * Devuelve cuántas veces se ejecutó algún método real. Es un método porque el bean
         * que entrega Spring es un proxy, cuyos campos están vacíos.
         *
         * @return número de ejecuciones
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
            // Solo se intercepta.
        }

        @Cacheable("springItems")
        public Item findWithSpringCache(Long id) {
            calls.incrementAndGet();
            return new Item(id, "spring-" + id, List.of("c"));
        }

        @CacheEvict("springItems")
        public void forgetWithSpringCache(Long id) {
            // Solo se intercepta.
        }
    }

    /**
     * Beans propios de la aplicación de prueba.
     */
    @Configuration
    static class Beans {

        @Bean
        CatalogService catalogService() {
            return new CatalogService();
        }
    }
}
