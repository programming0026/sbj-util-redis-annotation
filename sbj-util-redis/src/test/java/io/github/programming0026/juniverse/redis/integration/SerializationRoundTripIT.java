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
 * Checks that what a method with {@code @CoalesceCacheable} returns is stored in Redis
 * and read back with the same type and the same value.
 * Each test calls the method twice: the second call must come from the cache.
 *
 * @author Java Universe
 */
class SerializationRoundTripIT extends AbstractRedisIT {

    /** Intercepted service. */
    private TypesService service;

    /** Real service, to count the executions. */
    private TypesService target;

    /** Plain, non-final data object. */
    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    private static class Item {
        /** Identifier. */
        private Long id;
        /** Name. */
        private String name;
        /** Price. */
        private BigDecimal price;
        /** Registration date. */
        private LocalDateTime createdAt;
        /** Status. */
        private Status status;
    }

    /**
     * Service with one method for each return type to be checked.
     */
     public static class TypesService {

        /** Number of times some real method ran. */
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
        public Person recordPerson() {
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
     * Creates the intercepted service.
     */
    @BeforeEach
     void setUp() {
        target = new TypesService();
        service = newInstance().proxy(target);
    }

    /**
     * Calls twice and checks that the second call returns the same without running the method.
     *
     * @param call call to the intercepted method
     */
    private void assertServedFromCache(Supplier<Object> call) {
        Object first = call.get();
        Object second = call.get();

        assertEquals(first, second);
        assertEquals(1, target.calls.get(), "the second call must be served from the cache");
    }

    @Test
     void pojo_ShouldRoundTrip() {
        assertServedFromCache(service::pojo);
    }

    @Test
     void string_ShouldRoundTrip() {
        assertServedFromCache(service::string);
    }

    @Test
     void arrayList_ShouldRoundTrip() {
        assertServedFromCache(service::arrayList);
    }

    @Test
     void map_ShouldRoundTrip() {
        assertServedFromCache(service::map);
    }

    @Test
     void record_ShouldRoundTrip() {
        assertServedFromCache(service::recordPerson);
    }

    @Test
     void long_ShouldRoundTrip() {
        assertServedFromCache(service::longValue);
    }

    @Test
     void bigDecimal_ShouldRoundTrip() {
        assertServedFromCache(service::bigDecimal);
    }

    @Test
     void dateTime_ShouldRoundTrip() {
        assertServedFromCache(service::dateTime);
    }

    @Test
     void enum_ShouldRoundTrip() {
        assertServedFromCache(service::enumValue);
    }

    @Test
     void listOf_ShouldRoundTrip() {
        assertServedFromCache(service::listOf);
    }

    @Test
     void streamToList_ShouldRoundTrip() {
        assertServedFromCache(service::streamToList);
    }

    /**
     * With {@code cacheNull = true}, a null result must also come from the cache.
     */
    @Test
     void nullValue_ShouldBeServedFromCache_WhenCacheNullIsTrue() {
        assertNull(service.nullValue());
        assertNull(service.nullValue());

        assertEquals(1, target.calls.get(), "the stored null must avoid the second execution");
    }
}
