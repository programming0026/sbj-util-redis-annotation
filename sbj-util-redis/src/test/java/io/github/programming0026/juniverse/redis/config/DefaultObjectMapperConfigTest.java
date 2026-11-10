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
 * Pruebas unitarias de DefaultObjectMapperConfig.
 *
 * @author Java Universe
 */
class DefaultObjectMapperConfigTest {

    /** Configuración. */
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
     * Jackson 3 admite java.time sin registrar ningún módulo.
     */
    @Test
    void configure_ShouldSupportJavaTimeWithoutModules() {
        ObjectMapper mapper = config.configure();

        assertEquals("\"2026-01-02\"", mapper.writeValueAsString(java.time.LocalDate.of(2026, 1, 2)));
    }

    /**
     * Jackson 3 admite Optional sin registrar ningún módulo.
     */
    @Test
    void configure_ShouldSupportOptionalWithoutModules() {
        ObjectMapper mapper = config.configure();

        assertEquals("\"x\"", mapper.writeValueAsString(java.util.Optional.of("x")));
    }

    /**
     * La configuración debe deshabilitar escribir fechas como marcas de tiempo.
     */
    @Test
    void configure_ShouldDisableWriteDatesAsTimestamps() {
        ObjectMapper mapper = config.configure();

        assertFalse(mapper.isEnabled(DateTimeFeature.WRITE_DATES_AS_TIMESTAMPS));
    }

    /**
     * La configuración debe configurar la visibilidad.
     */
    @Test
    void configure_ShouldConfigureVisibility() {
        ObjectMapper mapper = config.configure();

        assertNotNull(mapper);
        
        assertNotNull(mapper.serializationConfig().getDefaultVisibilityChecker());
    }

    /**
     * La configuración no debe fallar con propiedades desconocidas.
     */
    @Test
    void configure_ShouldNotFailOnUnknownProperties() {
        ObjectMapper mapper = config.configure();

        assertFalse(mapper.isEnabled(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES));
    }

    /**
     * La configuración no debe fallar con beans vacíos.
     */
    @Test
    void configure_ShouldNotFailOnEmptyBeans() {
        ObjectMapper mapper = config.configure();

        assertFalse(mapper.isEnabled(SerializationFeature.FAIL_ON_EMPTY_BEANS));
    }

    /**
     * La configuración no debe fallar con nulos en primitivos.
     */
    @Test
    void configure_ShouldNotFailOnNullForPrimitives() {
        ObjectMapper mapper = config.configure();

        assertFalse(mapper.isEnabled(DeserializationFeature.FAIL_ON_NULL_FOR_PRIMITIVES));
    }

    /**
     * La configuración debe aceptar un valor único como arreglo.
     */
    @Test
    void configure_ShouldAcceptSingleValueAsArray() {
        ObjectMapper mapper = config.configure();

        assertTrue(mapper.isEnabled(DeserializationFeature.ACCEPT_SINGLE_VALUE_AS_ARRAY));
    }

    /**
     * La configuración no debe aceptar una cadena vacía como objeto nulo.
     */
    @Test
    void configure_ShouldNotAcceptEmptyStringAsNullObject() {
        ObjectMapper mapper = config.configure();

        assertFalse(mapper.isEnabled(DeserializationFeature.ACCEPT_EMPTY_STRING_AS_NULL_OBJECT));
    }

    /**
     * La configuración no debe aceptar un arreglo vacío como objeto nulo.
     */
    @Test
    void configure_ShouldNotAcceptEmptyArrayAsNullObject() {
        ObjectMapper mapper = config.configure();

        assertFalse(mapper.isEnabled(DeserializationFeature.ACCEPT_EMPTY_ARRAY_AS_NULL_OBJECT));
    }

    /**
     * La configuración no debe leer enumeraciones con toString.
     */
    @Test
    void configure_ShouldNotReadEnumsUsingToString() {
        ObjectMapper mapper = config.configure();

        assertFalse(mapper.isEnabled(EnumFeature.READ_ENUMS_USING_TO_STRING));
    }

    /**
     * La configuración no debe escribir enumeraciones con toString.
     */
    @Test
    void configure_ShouldNotWriteEnumsUsingToString() {
        ObjectMapper mapper = config.configure();

        assertFalse(mapper.isEnabled(EnumFeature.WRITE_ENUMS_USING_TO_STRING));
    }

    /**
     * La configuración debe poder usarse para serializar.
     *
     * @throws Exception si ocurre un error
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
     * La configuración debe poder usarse para deserializar.
     *
     * @throws Exception si ocurre un error
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
     * La configuración debe manejar los tipos de java.time.
     *
     * @throws Exception si ocurre un error
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
     * La configuración debe crear una instancia nueva cada vez.
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
        
        /** Nombre. */
        private String name;
        
        /** Valor. */
        private int value;

        /** Constructor. */
        public TestObject () {
        }

        public TestObject(String name, int value) {
            this.name = name;
            this.value = value;
        }
    }

    private static class TimeObject {
        
        /** Marca de tiempo. */
        private LocalDateTime timestamp;

        /** Constructor. */
       public TimeObject() {

       }

        public TimeObject(LocalDateTime timestamp) {
            this.timestamp = timestamp;
        }
    }
}
