package io.github.programming0026.juniverse.redis.integration;

import io.github.programming0026.juniverse.redis.annotation.EnableRedisLibrary;
import io.github.programming0026.juniverse.redis.annotation.HostEntry;
import io.github.programming0026.juniverse.redis.service.RedisCacheService;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Supplier;
import java.util.stream.Stream;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.MapPropertySource;
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory;
import org.springframework.data.redis.core.RedisTemplate;
import org.testcontainers.junit.jupiter.Testcontainers;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Comprueba contra Redis las formas de caché que no conocen el tipo del valor:
 * {@code RedisCacheService}, {@code RedisTemplate} y el {@code @Cacheable} de Spring.
 * Cada valor se guarda, se lee de vuelta y debe ser igual al original, con el mismo tipo.
 *
 * <p>La aplicación de prueba no indica {@code trustedPackages}: los records de esta clase se
 * admiten porque están en el paquete de la clase que lleva {@code @EnableRedisLibrary}.</p>
 *
 * @author Java Universe
 */
@Testcontainers(disabledWithoutDocker = true)
class UntypedRoundTripIT {

    /** Contexto de Spring. */
    private AnnotationConfigApplicationContext context;

    /** Estado de un pedido. */
    public enum Status { OPEN, CLOSED }

    /** Pedido inmutable. */
    public record Order(Long id, BigDecimal total, Status status, LocalDateTime createdAt, List<String> tags) {
    }

    /**
     * Servicio con métodos {@code @Cacheable} de Spring que devuelven tipos que antes se perdían.
     */
    public static class SpringCachedService {

        /** Número de ejecuciones reales. */
        private final AtomicInteger calls = new AtomicInteger();

        public int callCount() {
            return calls.get();
        }

        @Cacheable("untypedOrders")
        public Order order(Long id) {
            calls.incrementAndGet();
            return sampleOrder(id);
        }

        @Cacheable("untypedCounts")
        public Long count(String name) {
            calls.incrementAndGet();
            return 42L;
        }

        @Cacheable("untypedLists")
        public List<Order> orders(String customer) {
            calls.incrementAndGet();
            return Stream.of(sampleOrder(1L), sampleOrder(2L)).toList();
        }

        @Cacheable("untypedDates")
        public LocalDate date(String name) {
            calls.incrementAndGet();
            return LocalDate.of(2026, 1, 15);
        }

        @Cacheable("untypedStatus")
        public Status status(String name) {
            calls.incrementAndGet();
            return Status.CLOSED;
        }
    }

    static Order sampleOrder(Long id) {
        return new Order(id, new BigDecimal("19.90"), Status.OPEN, LocalDateTime.of(2026, 1, 15, 10, 30),
                List.of("a", "b"));
    }

    /**
     * Aplicación con la configuración predeterminada de la librería.
     */
    @Configuration
    @EnableRedisLibrary(hostEntries = @HostEntry(host = "${it.redis.address}"), keyPrefix = "sintipo")
    static class Application {

        @Bean
        SpringCachedService springCachedService() {
            return new SpringCachedService();
        }
    }

    /**
     * Arranca la aplicación contra el Redis compartido de las pruebas.
     */
    @BeforeEach
    void start() {
        LettuceConnectionFactory shared = RedisContainers.standaloneFactory();
        String address = shared.getStandaloneConfiguration().getHostName() + ":"
                + shared.getStandaloneConfiguration().getPort();
        context = new AnnotationConfigApplicationContext();
        context.getEnvironment().getPropertySources()
                .addFirst(new MapPropertySource("it", Map.of("it.redis.address", address)));
        context.register(Application.class);
        context.refresh();
    }

    /**
     * Cierra la aplicación.
     */
    @AfterEach
    void close() {
        context.close();
    }

    /**
     * El {@code @Cacheable} de Spring devuelve, desde Redis, el mismo valor y tipo que el método.
     */
    @Test
    void springCacheable_ShouldReturnTheOriginalTypes() {
        SpringCachedService service = context.getBean(SpringCachedService.class);

        assertServedFromCache(service, () -> service.order(7L), sampleOrder(7L));
        assertServedFromCache(service, () -> service.count("x"), 42L);
        assertServedFromCache(service, () -> service.orders("ana"), List.of(sampleOrder(1L), sampleOrder(2L)));
        assertServedFromCache(service, () -> service.date("x"), LocalDate.of(2026, 1, 15));
        assertServedFromCache(service, () -> service.status("x"), Status.CLOSED);
    }

    private static void assertServedFromCache(SpringCachedService service, Supplier<Object> call, Object expected) {
        int before = service.callCount();
        Object first = call.get();
        Object second = call.get();

        assertEquals(expected, first);
        assertEquals(expected, second);
        assertEquals(expected.getClass().isRecord() || expected instanceof Long || expected instanceof Enum
                || expected instanceof LocalDate ? expected.getClass() : second.getClass(), second.getClass());
        assertEquals(before + 1, service.callCount(), "la segunda llamada debe salir de Redis");
    }

    /**
     * {@code RedisCacheService} devuelve, desde Redis, el mismo valor y tipo que el cargador.
     */
    @Test
    void redisCacheService_ShouldReturnTheOriginalTypes() {
        RedisCacheService service = context.getBean(RedisCacheService.class);
        Object[] values = {
            sampleOrder(3L), 5L, new BigDecimal("1.50"), Status.OPEN, LocalDate.of(2026, 2, 1),
            List.of(sampleOrder(4L)), Stream.of("a", "b").toList(), Map.of("k", 1L), Set.of("z")
        };

        for (int i = 0; i < values.length; i++) {
            Object value = values[i];
            String key = "value" + i;
            service.cacheable("roundtrip", key, () -> value);

            Object fromRedis = service.cacheable("roundtrip", key, () -> "no-debe-ejecutarse");

            assertEquals(value, fromRedis, "valor de tipo " + value.getClass().getName());
            if (!(value instanceof java.util.Collection) && !(value instanceof Map)) {
                assertEquals(value.getClass(), fromRedis.getClass());
            }
        }
    }

    /**
     * La plantilla {@code redisTemplate} de la librería conserva igualmente los tipos.
     */
    @Test
    @SuppressWarnings("unchecked")
    void redisTemplate_ShouldReturnTheOriginalTypes() {
        RedisTemplate<String, Object> template = context.getBean("redisTemplate", RedisTemplate.class);

        template.opsForValue().set("sintipo:plantilla:pedido", sampleOrder(8L));
        template.opsForValue().set("sintipo:plantilla:numero", 9L);

        assertEquals(sampleOrder(8L), template.opsForValue().get("sintipo:plantilla:pedido"));
        assertEquals(9L, template.opsForValue().get("sintipo:plantilla:numero"));
        template.delete(List.of("sintipo:plantilla:pedido", "sintipo:plantilla:numero"));
    }
}
