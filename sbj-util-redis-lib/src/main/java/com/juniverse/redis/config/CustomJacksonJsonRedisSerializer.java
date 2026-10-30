package com.juniverse.redis.config;

import org.springframework.data.redis.serializer.RedisSerializer;
import org.springframework.data.redis.serializer.SerializationException;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.ObjectMapper;


/**
 * The Class CustomJacksonJsonRedisSerializer.
 *
 * <p>Serializes values to JSON with a Jackson 3 {@link ObjectMapper}. In Jackson 3 the exceptions are unchecked
 * ({@link JacksonException}); they are wrapped in a {@link SerializationException}.
 *
 * @author ajuar
 * @param <T> The object
 */
public class CustomJacksonJsonRedisSerializer<T> implements RedisSerializer<T> {

    /** The object mapper. */
    private final ObjectMapper objectMapper;

    /**
     * The class type.
     */
    private final Class<T> type;

    /**
     * Instantiates a new custom jackson json redis serializer.
     *
     * @param objectMapper the object mapper
     * @param type the type
     */
    public CustomJacksonJsonRedisSerializer(ObjectMapper objectMapper, Class<T> type) {
        this.objectMapper = objectMapper;
        this.type = type;
    }

    /**
     * Serialize.
     *
     * @param value the value
     * @return the byte[]
     * @throws SerializationException the serialization exception
     */
    @Override
    public byte[] serialize(T value) throws SerializationException {
        if (value == null) {
            return new byte[0];
        }
        try {
            return objectMapper.writeValueAsBytes(value);
        } catch (JacksonException e) {
            throw new SerializationException("Error serializing", e);
        }
    }

    /**
     * Deserialize.
     *
     * @param bytes the bytes
     * @return the t
     * @throws SerializationException the serialization exception
     */
    @Override
    public T deserialize(byte[] bytes) throws SerializationException {
        if (bytes == null || bytes.length == 0) {
            return null;
        }
        try {
            return objectMapper.readValue(bytes, type);
        } catch (JacksonException e) {
            throw new SerializationException("Error deserializing", e);
        }
    }
}
