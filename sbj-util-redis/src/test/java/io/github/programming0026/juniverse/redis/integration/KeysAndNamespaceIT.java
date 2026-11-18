package io.github.programming0026.juniverse.redis.integration;

import io.github.programming0026.juniverse.redis.annotation.CoalesceCacheable;
import io.github.programming0026.juniverse.redis.annotation.CoalesceEvict;
import io.github.programming0026.juniverse.redis.config.CoalesceSettings;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicInteger;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Comprueba contra Redis las claves que se generan sin {@code key} ni {@code value}
 * y el aislamiento entre aplicaciones que usan prefijos distintos.
 *
 * @author Java Universe
 */
class KeysAndNamespaceIT extends AbstractRedisIT {

    /** Filtro de búsqueda. */
    public record Filter(String name, List<String> tags) {
    }

    /**
     * Servicio cuyas anotaciones no indican clave, y en algunos casos tampoco nombre de caché.
     */
    public static class LookupService {

        /** Número de ejecuciones reales. */
        private final AtomicInteger calls = new AtomicInteger();

        @CoalesceCacheable("names")
        public String upper(String name) {
            calls.incrementAndGet();
            return name.toUpperCase();
        }

        @CoalesceCacheable("searches")
        public String search(Filter filter, int page) {
            calls.incrementAndGet();
            return filter.name() + "#" + page;
        }

        @CoalesceCacheable
        public Long twice(Long value) {
            calls.incrementAndGet();
            return value * 2;
        }

        @CoalesceCacheable
        public String twice(String value) {
            calls.incrementAndGet();
            return value + value;
        }

        @CoalesceEvict("names")
        public void forget(String name) {
            // Solo se intercepta.
        }
    }

    /**
     * "Aa" y "BB" tienen el mismo hashCode: cada uno debe tener su propia entrada.
     */
    @Test
    void argumentsWithSameHashCode_ShouldNotShareEntries() {
        LookupService target = new LookupService();
        LookupService service = newInstance().proxy(target);

        assertEquals("AA", service.upper("Aa"));
        assertEquals("BB", service.upper("BB"));
        assertEquals("AA", service.upper("Aa"));
        assertEquals("BB", service.upper("BB"));

        assertEquals(2, target.calls.get());
    }

    /**
     * Los objetos se identifican por su contenido: dos filtros iguales comparten entrada
     * y dos distintos no.
     */
    @Test
    void objectArguments_ShouldBeKeyedByContent() {
        LookupService target = new LookupService();
        LookupService service = newInstance().proxy(target);

        assertEquals("ana#1", service.search(new Filter("ana", List.of("x")), 1));
        assertEquals("ana#1", service.search(new Filter("ana", List.of("x")), 1));
        assertEquals("ana#2", service.search(new Filter("ana", List.of("x")), 2));
        assertEquals("ana#1", service.search(new Filter("ana", List.of("y")), 1));

        assertEquals(3, target.calls.get());
    }

    /**
     * Dos sobrecargas sin nombre de caché no deben compartir entradas, aunque el argumento
     * se escriba igual.
     */
    @Test
    void overloadsWithoutCacheName_ShouldNotShareEntries() {
        LookupService target = new LookupService();
        LookupService service = newInstance().proxy(target);

        assertEquals(10L, service.twice(5L));
        assertEquals("55", service.twice("5"));
        assertEquals(10L, service.twice(5L));
        assertEquals("55", service.twice("5"));

        assertEquals(2, target.calls.get());
    }

    /**
     * La invalidación sin clave explícita encuentra la entrada que guardó la lectura sin clave.
     */
    @Test
    void evictWithoutKey_ShouldMatchCacheableWithoutKey() {
        LookupService target = new LookupService();
        LookupService service = newInstance().proxy(target);

        service.upper("ana");
        service.forget("ana");
        service.upper("ana");

        assertEquals(2, target.calls.get());
    }

    /**
     * Dos aplicaciones con prefijos distintos en el mismo Redis no comparten entradas,
     * ni con las anotaciones ni con el servicio programático, y borrar todo en una no toca la otra.
     */
    @Test
    void applicationsWithDifferentPrefixes_ShouldBeIsolated() {
        RedisInstance shop = register(new RedisInstance(connectionFactory(), CoalesceSettings.defaults(), "tienda"));
        RedisInstance billing = register(
                new RedisInstance(connectionFactory(), CoalesceSettings.defaults(), "facturas"));

        shop.cacheManager().put("users:1", "de-tienda", 60, String.class);
        billing.cacheManager().put("users:1", "de-facturas", 60, String.class);
        shop.cacheService().cacheable("users", "1", () -> "servicio-tienda");
        billing.cacheService().cacheable("users", "1", () -> "servicio-facturas");

        assertEquals("de-tienda", shop.cacheManager().lookup("users:1", String.class).getValue());
        assertEquals("de-facturas", billing.cacheManager().lookup("users:1", String.class).getValue());
        assertEquals("servicio-tienda", shop.cacheService().cacheable("users", "1", () -> "otro"));
        assertEquals("servicio-facturas", billing.cacheService().cacheable("users", "1", () -> "otro"));

        shop.cacheManager().evictAll("users");
        shop.cacheManager().clear();
        shop.cacheService().cacheEvictAll("users");
        shop.cacheService().cacheEvictByPattern("*");

        assertFalse(shop.cacheManager().exists("users:1"));
        assertFalse(shop.cacheService().exists("users", "1"));
        assertTrue(billing.cacheManager().exists("users:1"), "se borró una clave de otra aplicación");
        assertTrue(billing.cacheService().exists("users", "1"), "se borró una clave de otra aplicación");
    }

    /**
     * Una carga coalescida en una aplicación no despierta ni bloquea a otra con distinto prefijo.
     */
    @Test
    void applicationsWithDifferentPrefixes_ShouldLoadIndependently() {
        RedisInstance shop = register(new RedisInstance(connectionFactory(), CoalesceSettings.defaults(), "tienda"));
        RedisInstance billing = register(
                new RedisInstance(connectionFactory(), CoalesceSettings.defaults(), "facturas"));
        Map<String, Long> totals = Map.of("a", 1L);

        Object fromShop = shop.cacheManager().getOrLoad("totals:1", () -> Optional.of(totals), 60, false,
                Optional.class);
        Object fromBilling = billing.cacheManager().getOrLoad("totals:1", () -> "otra-cosa", 60, false,
                String.class);

        assertEquals(Optional.of(totals), fromShop);
        assertEquals("otra-cosa", fromBilling);
    }
}
