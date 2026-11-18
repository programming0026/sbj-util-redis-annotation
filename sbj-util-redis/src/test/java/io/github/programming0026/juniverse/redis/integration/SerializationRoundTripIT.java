package io.github.programming0026.juniverse.redis.integration;

import io.github.programming0026.juniverse.redis.integration.dto.Person;
import io.github.programming0026.juniverse.redis.integration.enums.Status;
import io.github.programming0026.juniverse.redis.annotation.CoalesceCacheable;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Supplier;
import java.util.stream.Stream;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

/**
 * Comprueba que lo que devuelve un método con {@code @CoalesceCacheable} se guarda en Redis
 * y se lee de vuelta con el mismo tipo y el mismo valor.
 * Cada prueba llama dos veces al método: la segunda debe salir de la caché.
 *
 * @author Java Universe
 */
class SerializationRoundTripIT extends AbstractRedisIT {

    /** Servicio interceptado. */
    private TypesService service;

    /** Servicio real, para contar las ejecuciones. */
    private TypesService target;

    /** Objeto de datos corriente, no final. */
    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class Item {
        /** Identificador. */
        private Long id;
        /** Nombre. */
        private String name;
        /** Precio. */
        private BigDecimal price;
        /** Fecha de alta. */
        private LocalDateTime createdAt;
        /** Estado. */
        private Status status;
    }

    /**
     * Servicio con un método por cada tipo de retorno que se quiere comprobar.
     */
    public static class TypesService {

        /** Número de veces que se ejecutó algún método real. */
        private final AtomicInteger calls = new AtomicInteger();

        private static Item item() {
            return new Item(7L, "keyboard", new BigDecimal("19.90"),
                    LocalDateTime.of(2026, 1, 15, 10, 30), Status.OPEN);
        }

        @CoalesceCacheable(value = "types", key = "'pojo'")
        public Item pojo() {
            calls.incrementAndGet();
            return item();
        }

        @CoalesceCacheable(value = "types", key = "'string'")
        public String string() {
            calls.incrementAndGet();
            return "hello";
        }

        @CoalesceCacheable(value = "types", key = "'arrayList'")
        public List<Item> arrayList() {
            calls.incrementAndGet();
            return new ArrayList<>(List.of(item()));
        }

        @CoalesceCacheable(value = "types", key = "'map'")
        public Map<String, Long> map() {
            calls.incrementAndGet();
            return new HashMap<>(Map.of("a", 1L));
        }

        @CoalesceCacheable(value = "types", key = "'record'")
        public Person record() {
            calls.incrementAndGet();
            return new Person(1L, "Ana");
        }

        @CoalesceCacheable(value = "types", key = "'long'")
        public Long longValue() {
            calls.incrementAndGet();
            return 5L;
        }

        @CoalesceCacheable(value = "types", key = "'bigDecimal'")
        public BigDecimal bigDecimal() {
            calls.incrementAndGet();
            return new BigDecimal("10.50");
        }

        @CoalesceCacheable(value = "types", key = "'dateTime'")
        public LocalDateTime dateTime() {
            calls.incrementAndGet();
            return LocalDateTime.of(2026, 1, 15, 10, 30);
        }

        @CoalesceCacheable(value = "types", key = "'enum'")
        public Status enumValue() {
            calls.incrementAndGet();
            return Status.CLOSED;
        }

        @CoalesceCacheable(value = "types", key = "'listOf'")
        public List<Item> listOf() {
            calls.incrementAndGet();
            return List.of(item());
        }

        @CoalesceCacheable(value = "types", key = "'streamToList'")
        public List<Item> streamToList() {
            calls.incrementAndGet();
            return Stream.of(item()).toList();
        }

        @CoalesceCacheable(value = "types", key = "'null'", cacheNull = true)
        public Item nullValue() {
            calls.incrementAndGet();
            return null;
        }
    }

    /**
     * Crea el servicio interceptado.
     */
    @BeforeEach
    public void setUp() {
        target = new TypesService();
        service = newInstance().proxy(target);
    }

    /**
     * Llama dos veces y comprueba que la segunda devuelve lo mismo sin ejecutar el método.
     *
     * @param call llamada al método interceptado
     */
    private void assertServedFromCache(Supplier<Object> call) {
        Object first = call.get();
        Object second = call.get();

        assertEquals(first, second);
        assertEquals(1, target.calls.get(), "the second call must be served from the cache");
    }

    @Test
    public void pojo_ShouldRoundTrip() {
        assertServedFromCache(service::pojo);
    }

    @Test
    public void string_ShouldRoundTrip() {
        assertServedFromCache(service::string);
    }

    @Test
    public void arrayList_ShouldRoundTrip() {
        assertServedFromCache(service::arrayList);
    }

    @Test
    public void map_ShouldRoundTrip() {
        assertServedFromCache(service::map);
    }

    @Test
    public void record_ShouldRoundTrip() {
        assertServedFromCache(service::record);
    }

    @Test
    public void long_ShouldRoundTrip() {
        assertServedFromCache(service::longValue);
    }

    @Test
    public void bigDecimal_ShouldRoundTrip() {
        assertServedFromCache(service::bigDecimal);
    }

    @Test
    public void dateTime_ShouldRoundTrip() {
        assertServedFromCache(service::dateTime);
    }

    @Test
    public void enum_ShouldRoundTrip() {
        assertServedFromCache(service::enumValue);
    }

    @Test
    public void listOf_ShouldRoundTrip() {
        assertServedFromCache(service::listOf);
    }

    @Test
    public void streamToList_ShouldRoundTrip() {
        assertServedFromCache(service::streamToList);
    }

    /**
     * Con {@code cacheNull = true}, un resultado nulo también debe salir de la caché.
     */
    @Test
    public void nullValue_ShouldBeServedFromCache_WhenCacheNullIsTrue() {
        assertNull(service.nullValue());
        assertNull(service.nullValue());

        assertEquals(1, target.calls.get(), "the stored null must avoid the second execution");
    }
}
