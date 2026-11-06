package com.juniverse.redis.config;

import org.springframework.data.redis.serializer.RedisSerializer;
import org.springframework.data.redis.serializer.SerializationException;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.ObjectMapper;


/**
 *
 * <p>Serializa valores a JSON con un {@link ObjectMapper} de Jackson 3. En Jackson 3 las excepciones no son comprobadas
 * ({@link JacksonException}); se envuelven en una {@link SerializationException}.
 *
 * @author Java Universe
 * @param <T> objeto
 */
public class CustomJacksonJsonRedisSerializer<T> implements RedisSerializer<T> {

    /** Conversor de objeto. */
    private final ObjectMapper objectMapper;

    /**
     * Tipo de clase.
     */
    private final Class<T> type;

    public CustomJacksonJsonRedisSerializer(ObjectMapper objectMapper, Class<T> type) {
        this.objectMapper = objectMapper;
        this.type = type;
    }

    /**
     * Serializa el objeto.
     *
     * @param value valor
     * @return bytes serializados
     * @throws SerializationException si falla la serialización
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
     * Deserializa los bytes.
     *
     * @param bytes bytes
     * @return resultado de la evaluación
     * @throws SerializationException si falla la serialización
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
