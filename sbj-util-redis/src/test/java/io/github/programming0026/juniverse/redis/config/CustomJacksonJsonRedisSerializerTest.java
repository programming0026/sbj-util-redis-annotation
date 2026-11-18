package io.github.programming0026.juniverse.redis.config;

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
 * Unit tests for CustomJacksonJsonRedisSerializer.
 *
 * @author Java Universe
 */
@ExtendWith(MockitoExtension.class)
class CustomJacksonJsonRedisSerializerTest {

    /** Object mapper. */
    @Mock
    private ObjectMapper objectMapper;

    /** Serializer. */
    private CustomJacksonJsonRedisSerializer<Object> serializer;
    

    /**
     * Sets up the test.
     */
    @BeforeEach
     void setUp() {
        serializer = new CustomJacksonJsonRedisSerializer<>(objectMapper, Object.class);
    }

    
    /**
     * Serializing a null value must return an empty byte array.
     */
    @Test
     void serialize_WithNullValue_ShouldReturnEmptyByteArray() {
        byte[] result = serializer.serialize(null);

        assertNotNull(result);
        assertEquals(0, result.length);
        verifyNoInteractions(objectMapper);
    }

    /**
     * Serializing a valid object must return the serialized bytes.
     *
     * @throws JacksonException if JSON processing fails
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
     * Serializing when the JSON fails must throw SerializationException.
     *
     * @throws JacksonException if JSON processing fails
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
     * Serializing a complex object must work correctly.
     *
     * @throws JacksonException if JSON processing fails
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
     * Deserializing null bytes must return null.
     */
    @Test
     void deserialize_WithNullByteArray_ShouldReturnNull() {
        Object result = serializer.deserialize(null);

        assertNull(result);
        verifyNoInteractions(objectMapper);
    }

    /**
     * Deserializing an empty array must return null.
     */
    @Test
     void deserialize_WithEmptyByteArray_ShouldReturnNull() {
        byte[] emptyBytes = new byte[0];

        Object result = serializer.deserialize(emptyBytes);

        assertNull(result);
        verifyNoInteractions(objectMapper);
    }

    /**
     * Deserializing when Jackson fails must throw SerializationException.
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
     * Test object for the serialization tests.
     */
     static class TestObject {
        
        /** Name. */
        private String name;
        
        /** Age. */
        private int age;

         TestObject() {
        }

         TestObject(String name, int age) {
            this.name = name;
            this.age = age;
        }

         String getName() {
            return name;
        }

         void setName(String name) {
            this.name = name;
        }

         int getAge() {
            return age;
        }

        /**
         * Sets the age.
         *
         * @param age new age
         */
         void setAge(int age) {
            this.age = age;
        }
    }
}
