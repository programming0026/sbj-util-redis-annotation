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
 * Unit tests for CustomJacksonJsonRedisSerializer.
 *
 * @author ajuar
 */
@ExtendWith(MockitoExtension.class)
class CustomJacksonJsonRedisSerializerTest {

    /** The object mapper. */
    @Mock
    private ObjectMapper objectMapper;

    /** The serializer. */
    private CustomJacksonJsonRedisSerializer<Object> serializer;
    

    /**
     * Sets the up.
     */
    @BeforeEach
    void setUp() {
        serializer = new CustomJacksonJsonRedisSerializer<>(objectMapper, Object.class);
    }

    
    /**
     * Serialize with null value should return empty byte array.
     */
    @Test
    void serialize_WithNullValue_ShouldReturnEmptyByteArray() {
        byte[] result = serializer.serialize(null);

        assertNotNull(result);
        assertEquals(0, result.length);
        verifyNoInteractions(objectMapper);
    }

    /**
     * Serialize with valid object should return serialized bytes.
     *
     * @throws JacksonException the json processing exception
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
     * Serialize when json processing fails should throw serialization exception.
     *
     * @throws JacksonException the json processing exception
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
     * Serialize with complex object should serialize correctly.
     *
     * @throws JacksonException the json processing exception
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
     * Deserialize with null byte array should return null.
     */
    @Test
    void deserialize_WithNullByteArray_ShouldReturnNull() {
        Object result = serializer.deserialize(null);

        assertNull(result);
        verifyNoInteractions(objectMapper);
    }

    /**
     * Deserialize with empty byte array should return null.
     */
    @Test
    void deserialize_WithEmptyByteArray_ShouldReturnNull() {
        byte[] emptyBytes = new byte[0];

        Object result = serializer.deserialize(emptyBytes);

        assertNull(result);
        verifyNoInteractions(objectMapper);
    }

    /**
     * Deserialize when Jackson fails should throw serialization exception.
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
     * Test object for serialization tests.
     */
    public static class TestObject {
        
        /** The name. */
        private String name;
        
        /** The age. */
        private int age;

        /**
         * Instantiates a new test object.
         */
        public TestObject() {
        }

        /**
         * Instantiates a new test object.
         *
         * @param name the name
         * @param age the age
         */
        public TestObject(String name, int age) {
            this.name = name;
            this.age = age;
        }

        /**
         * Gets the name.
         *
         * @return the name
         */
        public String getName() {
            return name;
        }

        /**
         * Sets the name.
         *
         * @param name the new name
         */
        public void setName(String name) {
            this.name = name;
        }

        /**
         * Gets the age.
         *
         * @return the age
         */
        public int getAge() {
            return age;
        }

        /**
         * Sets the age.
         *
         * @param age the new age
         */
        public void setAge(int age) {
            this.age = age;
        }
    }
}
