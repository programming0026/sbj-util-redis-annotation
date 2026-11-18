package io.github.programming0026.juniverse.redis.config;

import org.springframework.data.redis.serializer.RedisSerializer;
import org.springframework.data.redis.serializer.SerializationException;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.ObjectMapper;


/**
 *
 * <p>Serializes values to JSON with a Jackson 3 {@link ObjectMapper}. In Jackson 3 exceptions are unchecked
 * ({@link JacksonException}); they are wrapped in a {@link SerializationException}.
 *
 * @author Java Universe
 * @param <T> object type
 */
public class CustomJacksonJsonRedisSerializer<T> implements RedisSerializer<T> {

    /** Object mapper. */
    private final ObjectMapper objectMapper;

    /**
     * Class type.
     */
    private final Class<T> type;

    public CustomJacksonJsonRedisSerializer(ObjectMapper objectMapper, Class<T> type) {
        this.objectMapper = objectMapper;
        this.type = type;
    }

    /**
     * Serializes the object.
     *
     * @param value value
     * @return serialized bytes
     * @throws SerializationException if serialization fails
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
     * Deserializes the bytes.
     *
     * @param bytes bytes
     * @return deserialized object
     * @throws SerializationException if deserialization fails
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
