package io.github.programming0026.juniverse.redis.config;

import tools.jackson.databind.DeserializationFeature;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.SerializationFeature;
import tools.jackson.databind.cfg.DateTimeFeature;
import tools.jackson.databind.cfg.EnumFeature;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

import java.time.LocalDateTime;

/**
 * Unit tests for DefaultObjectMapperConfig.
 *
 * @author Java Universe
 */
class DefaultObjectMapperConfigTest {

    /** Configuration. */
    private final DefaultObjectMapperConfig config = new DefaultObjectMapperConfig();

    /**
     * Configure should return configured object mapper.
     */
    @Test
     void configure_ShouldReturnConfiguredObjectMapper() {
        ObjectMapper mapper = config.configure();

        assertNotNull(mapper);
        assertInstanceOf(ObjectMapper.class, mapper);
    }

    /**
     * Jackson 3 supports java.time without registering any module.
     */
    @Test
     void configure_ShouldSupportJavaTimeWithoutModules() {
        ObjectMapper mapper = config.configure();

        assertEquals("[\"java.time.LocalDate\",\"2026-01-02\"]",
                mapper.writeValueAsString(java.time.LocalDate.of(2026, 1, 2)));
        assertEquals(java.time.LocalDate.of(2026, 1, 2),
                mapper.readValue("[\"java.time.LocalDate\",\"2026-01-02\"]", Object.class));
    }

    /**
     * Jackson 3 supports Optional without registering any module.
     */
    @Test
     void configure_ShouldSupportOptionalWithoutModules() {
        ObjectMapper mapper = config.configure();

        assertEquals("\"x\"", mapper.writeValueAsString(java.util.Optional.of("x")));
    }

    /**
     * The configuration must disable writing dates as timestamps.
     */
    @Test
     void configure_ShouldDisableWriteDatesAsTimestamps() {
        ObjectMapper mapper = config.configure();

        assertFalse(mapper.isEnabled(DateTimeFeature.WRITE_DATES_AS_TIMESTAMPS));
    }

    /**
     * The configuration must set up the visibility.
     */
    @Test
     void configure_ShouldConfigureVisibility() {
        ObjectMapper mapper = config.configure();

        assertNotNull(mapper);
        
        assertNotNull(mapper.serializationConfig().getDefaultVisibilityChecker());
    }

    /**
     * The configuration must not fail on unknown properties.
     */
    @Test
     void configure_ShouldNotFailOnUnknownProperties() {
        ObjectMapper mapper = config.configure();

        assertFalse(mapper.isEnabled(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES));
    }

    /**
     * The configuration must not fail on empty beans.
     */
    @Test
     void configure_ShouldNotFailOnEmptyBeans() {
        ObjectMapper mapper = config.configure();

        assertFalse(mapper.isEnabled(SerializationFeature.FAIL_ON_EMPTY_BEANS));
    }

    /**
     * The configuration must not fail on nulls for primitives.
     */
    @Test
     void configure_ShouldNotFailOnNullForPrimitives() {
        ObjectMapper mapper = config.configure();

        assertFalse(mapper.isEnabled(DeserializationFeature.FAIL_ON_NULL_FOR_PRIMITIVES));
    }

    /**
     * The configuration must accept a single value as an array.
     */
    @Test
     void configure_ShouldAcceptSingleValueAsArray() {
        ObjectMapper mapper = config.configure();

        assertTrue(mapper.isEnabled(DeserializationFeature.ACCEPT_SINGLE_VALUE_AS_ARRAY));
    }

    /**
     * The configuration must not accept an empty string as a null object.
     */
    @Test
     void configure_ShouldNotAcceptEmptyStringAsNullObject() {
        ObjectMapper mapper = config.configure();

        assertFalse(mapper.isEnabled(DeserializationFeature.ACCEPT_EMPTY_STRING_AS_NULL_OBJECT));
    }

    /**
     * The configuration must not accept an empty array as a null object.
     */
    @Test
     void configure_ShouldNotAcceptEmptyArrayAsNullObject() {
        ObjectMapper mapper = config.configure();

        assertFalse(mapper.isEnabled(DeserializationFeature.ACCEPT_EMPTY_ARRAY_AS_NULL_OBJECT));
    }

    /**
     * The configuration must not read enums using toString.
     */
    @Test
     void configure_ShouldNotReadEnumsUsingToString() {
        ObjectMapper mapper = config.configure();

        assertFalse(mapper.isEnabled(EnumFeature.READ_ENUMS_USING_TO_STRING));
    }

    /**
     * The configuration must not write enums using toString.
     */
    @Test
     void configure_ShouldNotWriteEnumsUsingToString() {
        ObjectMapper mapper = config.configure();

        assertFalse(mapper.isEnabled(EnumFeature.WRITE_ENUMS_USING_TO_STRING));
    }

    /**
     * The configuration must be usable for serializing.
     *
     * @throws Exception if an error occurs
     */
    @Test
     void configure_ShouldBeUsableForSerialization() throws Exception {
        ObjectMapper mapper = config.configure();
        TestObject testObj = new TestObject("test", 123);

        String json = mapper.writeValueAsString(testObj);

        assertNotNull(json);
        assertTrue(json.contains("test"));
        assertTrue(json.contains("123"));
    }

    /**
     * The configuration must be usable for deserializing.
     *
     * @throws Exception if an error occurs
     */
    @Test
     void configure_ShouldBeUsableForDeserialization() throws Exception {
        ObjectMapper mapper = config.configure();

        String json =
                """
                 {"@class":"io.github.programming0026.juniverse.redis.config.DefaultObjectMapperConfigTest$TestObject","name":"test","value":123,"unknownField":"should be ignored"}
                """;

        TestObject result = mapper.readValue(json, TestObject.class);

        assertNotNull(result);
        assertEquals("test", result.name);
        assertEquals(123, result.value);
    }

    /**
     * The configuration must handle the java.time types.
     *
     * @throws Exception if an error occurs
     */
    @Test
     void configure_ShouldHandleJavaTimeTypes() throws Exception {
        ObjectMapper mapper = config.configure();
        LocalDateTime now = LocalDateTime.now();
        TimeObject timeObj = new TimeObject(now);

        String json = mapper.writeValueAsString(timeObj);
        TimeObject result = mapper.readValue(json, TimeObject.class);

        assertNotNull(result);
        assertNotNull(result.timestamp);
    }

    /**
     * The configuration must create a new instance each time.
     */
    @Test
     void configure_ShouldCreateNewInstanceEachTime() {
        
    	ObjectMapper mapper1 = config.configure();
        ObjectMapper mapper2 = config.configure();

        assertNotNull(mapper1);
        assertNotNull(mapper2);
        assertNotSame(mapper1, mapper2);
    }

    
    private static class TestObject {
        
        /** Name. */
        private String name;
        
        /** Value. */
        private int value;

        /** Constructor. */
         TestObject () {
        }

         TestObject(String name, int value) {
            this.name = name;
            this.value = value;
        }
    }

    private static class TimeObject {
        
        /** Timestamp. */
        private LocalDateTime timestamp;

        /** Constructor. */
        TimeObject() {

       }

         TimeObject(LocalDateTime timestamp) {
            this.timestamp = timestamp;
        }
    }
}
