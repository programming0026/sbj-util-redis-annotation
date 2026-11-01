package com.juniverse.redis.config;

import tools.jackson.core.JacksonException;
import tools.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.serializer.SerializationException;


import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Pruebas unitarias de CustomJacksonJsonRedisSerializer.
 *
 * @author ajuar
 */
@ExtendWith(MockitoExtension.class)
class CustomJacksonJsonRedisSerializerTest {

    /** Conversor de objeto. */
    @Mock
    private ObjectMapper objectMapper;

    /** Serializador. */
    private CustomJacksonJsonRedisSerializer<Object> serializer;
    

    /**
     * Prepara la prueba.
     */
    @BeforeEach
    void setUp() {
        serializer = new CustomJacksonJsonRedisSerializer<>(objectMapper, Object.class);
    }

    
    /**
     * Serializar un valor nulo debe devolver un arreglo de bytes vacío.
     */
    @Test
    void serialize_WithNullValue_ShouldReturnEmptyByteArray() {
        byte[] result = serializer.serialize(null);

        assertNotNull(result);
        assertEquals(0, result.length);
        verifyNoInteractions(objectMapper);
    }

    /**
     * Serializar un objeto válido debe devolver los bytes serializados.
     *
     * @throws JacksonException si falla el procesamiento del JSON
     */
    @Test
    void serialize_WithValidObject_ShouldReturnSerializedBytes() throws JacksonException {
        String value = "test value";
        byte[] expectedBytes = new byte[]{1, 2, 3, 4};

        when(objectMapper.writeValueAsBytes(any())).thenReturn(expectedBytes);

        byte[] result = serializer.serialize(value);

        assertNotNull(result);
        assertEquals(expectedBytes, result);
        verify(objectMapper).writeValueAsBytes(any());
    }

    /**
     * Serializar cuando falla el JSON debe lanzar SerializationException.
     *
     * @throws JacksonException si falla el procesamiento del JSON
     */
    @Test
    void serialize_WhenJsonProcessingFails_ShouldThrowSerializationException() throws JacksonException {
        String value = "test value";
        JacksonException exception = mock(JacksonException.class);

        when(objectMapper.writeValueAsBytes(any())).thenThrow(exception);

        SerializationException thrown = assertThrows(SerializationException.class,
                () -> serializer.serialize(value));
        assertEquals("Error serializing", thrown.getMessage());
        assertSame(exception, thrown.getCause());
    }

    /**
     * Serializar un objeto complejo debe hacerse correctamente.
     *
     * @throws JacksonException si falla el procesamiento del JSON
     */
    @Test
    void serialize_WithComplexObject_ShouldSerializeCorrectly() throws JacksonException {
        TestObject testObject = new TestObject("John", 30);
        byte[] expectedBytes = new byte[]{5, 6, 7, 8};

        when(objectMapper.writeValueAsBytes(any())).thenReturn(expectedBytes);

        byte[] result = serializer.serialize(testObject);

        assertNotNull(result);
        assertEquals(expectedBytes, result);
        verify(objectMapper).writeValueAsBytes(any());
    }

    

    /**
     * Deserializar bytes nulos debe devolver nulo.
     */
    @Test
    void deserialize_WithNullByteArray_ShouldReturnNull() {
        Object result = serializer.deserialize(null);

        assertNull(result);
        verifyNoInteractions(objectMapper);
    }

    /**
     * Deserializar un arreglo vacío debe devolver nulo.
     */
    @Test
    void deserialize_WithEmptyByteArray_ShouldReturnNull() {
        byte[] emptyBytes = new byte[0];

        Object result = serializer.deserialize(emptyBytes);

        assertNull(result);
        verifyNoInteractions(objectMapper);
    }

    /**
     * Deserializar cuando Jackson falla debe lanzar SerializationException.
     */
    @SuppressWarnings("unchecked")
	@Test
    void deserialize_WhenJacksonFails_ShouldThrowSerializationException() {
        byte[] bytes = new byte[]{1, 2, 3, 4};
        JacksonException exception = mock(JacksonException.class);

        when(objectMapper.readValue(eq(bytes), any(Class.class))).thenThrow(exception);

        SerializationException  thrown = assertThrows(SerializationException.class,
                () -> serializer.deserialize(bytes));
        assertEquals("Error deserializing", thrown.getMessage());
    }

    
    /**
     * Objeto de prueba para las pruebas de serialización.
     */
    public static class TestObject {
        
        /** Nombre. */
        private String name;
        
        /** Edad. */
        private int age;

        public TestObject() {
        }

        public TestObject(String name, int age) {
            this.name = name;
            this.age = age;
        }

        public String getName() {
            return name;
        }

        public void setName(String name) {
            this.name = name;
        }

        public int getAge() {
            return age;
        }

        /**
         * Establece la edad.
         *
         * @param age nueva edad
         */
        public void setAge(int age) {
            this.age = age;
        }
    }
}
