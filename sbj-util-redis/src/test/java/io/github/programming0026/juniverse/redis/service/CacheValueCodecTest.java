package io.github.programming0026.juniverse.redis.service;

import io.github.programming0026.juniverse.redis.dto.CoalesceSettings;
import io.github.programming0026.juniverse.redis.config.DefaultObjectMapperConfig;
import io.github.programming0026.juniverse.redis.util.CacheDefaults;
import java.lang.reflect.Type;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Stream;

import org.junit.jupiter.api.Test;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.data.redis.serializer.SerializationException;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Tests of the cache value converter and the related settings.
 *
 * @author Java Universe
 */
class CacheValueCodecTest {

    /** Converter with the default configuration without class restrictions. */
    private final CacheValueCodec codec = new CacheValueCodec(new DefaultObjectMapperConfig().configure());

    /** Sample state. */
     enum Status { OPEN, CLOSED }

    /** Sample immutable value. */
     record Person(Long id, String name, List<String> tags) {
    }

    /** Sample non-final data object. */
     static class Item {
        /** Identifier. */
        private Long id = 7L;
        /** Tags. */
        private List<String> tags = Stream.of("a", "b").toList();

         Long id() {
            return id;
        }

         List<String> tags() {
            return tags;
        }
    }

    private Object roundTrip(Object value, Type type) {
        return codec.decode(codec.encode(value, type), type);
    }

    private static String text(byte[] bytes) {
        return new String(bytes, StandardCharsets.UTF_8);
    }

    @Test
     void knownTypes_ShouldRoundTripExactly() {
        Type listOfPersons = new ParameterizedTypeReference<List<Person>>() { }.getType();
        Type mapOfLongs = new ParameterizedTypeReference<Map<String, Long>>() { }.getType();
        Type optional = new ParameterizedTypeReference<Optional<Person>>() { }.getType();
        Person ana = new Person(1L, "Ana", List.of("x"));

        assertEquals(ana, roundTrip(ana, Person.class));
        assertEquals(5L, roundTrip(5L, Long.class));
        assertEquals(5L, roundTrip(5L, long.class));
        assertEquals(new BigDecimal("10.50"), roundTrip(new BigDecimal("10.50"), BigDecimal.class));
        assertEquals(Status.CLOSED, roundTrip(Status.CLOSED, Status.class));
        assertEquals(LocalDateTime.of(2026, 1, 15, 10, 30),
                roundTrip(LocalDateTime.of(2026, 1, 15, 10, 30), LocalDateTime.class));
        assertEquals(List.of(ana), roundTrip(List.of(ana), listOfPersons));
        assertEquals(List.of(ana), roundTrip(Stream.of(ana).toList(), listOfPersons));
        assertEquals(Map.of("a", 1L), roundTrip(Map.of("a", 1L), mapOfLongs));
        assertEquals(Optional.of(ana), roundTrip(Optional.of(ana), optional));
    }

    /**
     * With a known type the JSON carries no class names, not even in the fields.
     */
    @Test
     void knownTypes_ShouldNotWriteClassNames() {
        String json = text(codec.encode(new Item(), Item.class));

        assertFalse(json.contains("@class"), json);
        assertFalse(json.contains("java.util"), json);

        Item back = (Item) codec.decode(json.getBytes(StandardCharsets.UTF_8), Item.class);
        assertEquals(7L, back.id());
        assertEquals(List.of("a", "b"), back.tags());
    }

    /**
     * A class name inside the JSON does not decide which class is instantiated when there is a type.
     */
    @Test
     void knownTypes_ShouldIgnoreClassNamesInStoredJson() {
        byte[] stored = "{\"@class\":\"javax.swing.JEditorPane\",\"id\":3,\"name\":\"Ana\"}"
                .getBytes(StandardCharsets.UTF_8);

        Object value = codec.decode(stored, Person.class);

        assertEquals(new Person(3L, "Ana", null), value);
    }

    @Test
     void nullValue_ShouldBeStoredAsJsonNull() {
        byte[] bytes = codec.encode(null, Person.class);

        assertArrayEquals("null".getBytes(StandardCharsets.UTF_8), bytes);
        assertNull(codec.decode(bytes, Person.class));
        assertNull(codec.decode(codec.encode(null, Object.class), Object.class));
    }

    /**
     * Without a known type the format with class names is used.
     */
    @Test
     void unknownTypes_ShouldUseClassNames() {
        Type typeVariable = List.class.getTypeParameters()[0];

        for (Type unknown : new Type[] {Object.class, null, void.class, Void.class, typeVariable}) {
            String json = text(codec.encode(new ArrayList<>(List.of("a")), unknown));
            assertTrue(json.contains("java.util.ArrayList"), json);
            assertEquals(List.of("a"), codec.decode(json.getBytes(StandardCharsets.UTF_8), unknown));
        }
    }

    @Test
     void decode_WithBytesThatDoNotMatchTheType_ShouldThrowSerializationException() {
        byte[] notJson = "{this is not JSON".getBytes(StandardCharsets.UTF_8);
        byte[] wrongShape = "[1,2]".getBytes(StandardCharsets.UTF_8);

        assertThrows(SerializationException.class, () -> codec.decode(notJson, Person.class));
        assertThrows(SerializationException.class, () -> codec.decode(wrongShape, Person.class));
        assertThrows(SerializationException.class, () -> codec.decode(notJson, Object.class));
    }

    @Test
     void encode_WithValueThatCannotBeSerialized_ShouldThrowSerializationException() {
        Object selfReferencing = new Object() {
            @SuppressWarnings("unused")
            private final Object self = this;
        };

        assertThrows(SerializationException.class, () -> codec.encode(selfReferencing, Person.class));
    }

    /**
     * With trusted packages, the format with class names only instantiates classes from those
     * packages and the JDK basic types.
     */
    @Test
     void trustedPackages_ShouldRestrictClassNamesInUntypedFormat() {
        byte[] stored = codec.encode(new Item(), Object.class);
        byte[] list = codec.encode(new ArrayList<>(List.of("a", 1L)), Object.class);

        CacheValueCodec trusting = new CacheValueCodec(
                new DefaultObjectMapperConfig(List.of(getClass().getPackageName())).configure());
        CacheValueCodec trustingWithDot = new CacheValueCodec(
                new DefaultObjectMapperConfig(List.of(" " + getClass().getPackageName() + ". ", "")).configure());
        CacheValueCodec distrusting = new CacheValueCodec(
                new DefaultObjectMapperConfig(List.of("com.otra.aplicacion")).configure());
        CacheValueCodec anyPackage = new CacheValueCodec(
                new DefaultObjectMapperConfig(List.of(DefaultObjectMapperConfig.ANY_PACKAGE)).configure());

        assertInstanceOf(Item.class, trusting.decode(stored, Object.class));
        assertInstanceOf(Item.class, trustingWithDot.decode(stored, Object.class));
        assertInstanceOf(Item.class, anyPackage.decode(stored, Object.class));
        assertThrows(SerializationException.class, () -> distrusting.decode(stored, Object.class));
        assertEquals(List.of("a", 1L), distrusting.decode(list, Object.class));
        assertInstanceOf(Item.class, distrusting.decode(distrusting.encode(new Item(), Item.class), Item.class));
    }

    // ------------------------------------------------------------------ settings

    @Test
     void cacheDefaults_ShouldResolveTtlAndNamespace() {
        Map<String, Long> configured = Map.of("users", 1800L);

        assertEquals(60, CacheDefaults.resolveTtl(60, "users", configured));
        assertEquals(0, CacheDefaults.resolveTtl(0, "users", configured));
        assertEquals(1800, CacheDefaults.resolveTtl(CacheDefaults.CONFIGURED_TTL, "users", configured));
        assertEquals(CacheDefaults.FALLBACK_TTL_SECONDS,
                CacheDefaults.resolveTtl(CacheDefaults.CONFIGURED_TTL, "orders", configured));

        assertEquals("", CacheDefaults.namespace(null));
        assertEquals("", CacheDefaults.namespace("  "));
        assertEquals("shop:", CacheDefaults.namespace(" shop "));
    }

    @Test
     void coalesceSettings_ShouldRejectInvalidValues() {
        Duration ok = Duration.ofSeconds(1);

        assertThrows(IllegalArgumentException.class, () -> new CoalesceSettings(true, Duration.ZERO, ok));
        assertThrows(IllegalArgumentException.class, () -> new CoalesceSettings(true, ok, Duration.ofSeconds(-1)));
        assertThrows(NullPointerException.class, () -> new CoalesceSettings(true, null, ok));
        assertThrows(NullPointerException.class, () -> new CoalesceSettings(true, ok, ok, null));
        assertThrows(IllegalArgumentException.class,
                () -> new CoalesceSettings(true, ok, ok, Duration.ofSeconds(-1)));
        assertEquals(Duration.ZERO, new CoalesceSettings(true, ok, ok, Duration.ZERO).failurePause());
        assertTrue(CoalesceSettings.defaults().failOpen());
    }

    /**
     * Without a known type (RedisTemplate, RedisCacheService, Spring's @Cacheable) the value comes back
     * with its original type, including those that Jackson's default typing did not preserve.
     */
    @Test
     void unknownTypes_ShouldRoundTripWithTheirOriginalType() {
        Person ana = new Person(1L, "Ana", List.of("x"));
        Object[] values = {
            5L, 5, 2.5d, 2.5f, (short) 3, "texto", true, new BigDecimal("10.50"), Status.CLOSED,
            LocalDateTime.of(2026, 1, 15, 10, 30), java.util.UUID.fromString("00000000-0000-0000-0000-000000000001"),
            ana, List.of(ana), Stream.of(ana, ana).toList(), new ArrayList<>(List.of(1L, 2L)),
            Map.of("a", 1L), java.util.Set.of("z"), java.util.Arrays.asList("p", "q"),
            java.util.Collections.emptyList()
        };

        for (Object value : values) {
            Object back = roundTrip(value, Object.class);
            assertEquals(value, back, "value of type " + value.getClass().getName());
            if (!(value instanceof java.util.Collection) && !(value instanceof Map)) {
                assertEquals(value.getClass(), back.getClass());
            }
        }
    }

    @Test
     void unknownTypes_ShouldRoundTripArrays() {
        assertArrayEquals(new long[] {1, 2}, (long[]) roundTrip(new long[] {1, 2}, Object.class));
        assertArrayEquals(new byte[] {1, 2}, (byte[]) roundTrip(new byte[] {1, 2}, Object.class));
        assertArrayEquals(new String[] {"a"}, (String[]) roundTrip(new String[] {"a"}, Object.class));
        assertArrayEquals(new Person[] {new Person(1L, "Ana", null)},
                (Person[]) roundTrip(new Person[] {new Person(1L, "Ana", null)}, Object.class));
    }

    /**
     * An object with fields of every kind, including immutable lists and {@code Object} fields.
     */
    @Test
     void unknownTypes_ShouldRoundTripNestedContent() {
        Holder holder = new Holder();

        Holder back = (Holder) roundTrip(holder, Object.class);

        assertEquals(holder.id, back.id);
        assertEquals(holder.people, back.people);
        assertEquals(holder.totals, back.totals);
        assertEquals(holder.status, back.status);
        assertEquals(holder.createdAt, back.createdAt);
        assertEquals(holder.anything, back.anything);
        assertInstanceOf(Person.class, back.anything);
    }

    /** Object with fields of several types. */
     static class Holder {
        /** Identifier. */
        private Long id = 9L;
        /** Immutable list of records. */
        private List<Person> people = Stream.of(new Person(1L, "Ana", List.of("x"))).toList();
        /** Immutable map. */
        private Map<String, Long> totals = Map.of("a", 1L);
        /** Enum. */
        private Status status = Status.OPEN;
        /** Date. */
        private LocalDateTime createdAt = LocalDateTime.of(2026, 1, 15, 10, 30);
        /** Field without a concrete type. */
        private Object anything = new Person(2L, "Luis", null);
    }
}
