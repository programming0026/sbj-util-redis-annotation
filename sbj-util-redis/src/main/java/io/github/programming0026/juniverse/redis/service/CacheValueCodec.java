package io.github.programming0026.juniverse.redis.service;

import java.lang.reflect.Type;
import java.lang.reflect.TypeVariable;
import java.lang.reflect.WildcardType;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;

import org.springframework.data.redis.serializer.SerializationException;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.ObjectMapper;

/**
 * Converts cache values to JSON and back.
 *
 * <p>When the type of the value is known (the return type of the cached method), the JSON does not
 * carry class names: when reading, exactly that type is built. This preserves
 * records, immutable lists, {@code Long}, dates and enums, and whatever is in Redis
 * cannot choose which classes the application instantiates.</p>
 *
 * <p>When the type says nothing ({@code Object}, {@code void} or an unresolved generic), the
 * configured {@code ObjectMapper} is used as is, which by default includes the class name
 * in the JSON. That format has known limits: it does not preserve records, immutable collections
 * or {@code Long} as a root value.</p>
 *
 * <p>A null value is stored as the JSON literal {@code null}; this way a key with a stored
 * null is distinguished from a key that does not exist.</p>
 *
 * @author Java Universe
 */
public class CacheValueCodec {

    /** Representation of a stored null value. */
    private static final byte[] NULL_BYTES = "null".getBytes(StandardCharsets.UTF_8);

    /** Mapper without class names, for values of known type. */
    private final ObjectMapper typedMapper;

    /** Configured mapper, for values of unknown type. */
    private final ObjectMapper untypedMapper;

    /**
     * Creates the codec from the configured {@code ObjectMapper}. For values of known
     * type, a copy with the same configuration and without default typing is used.
     *
     * @param configuredMapper mapper from {@code ObjectMapperConfig}
     */
    public CacheValueCodec(ObjectMapper configuredMapper) {
        this.untypedMapper = configuredMapper;
        this.typedMapper = configuredMapper.rebuild().deactivateDefaultTyping().build();
    }

    /**
     * Serializes a value.
     *
     * @param value value; may be null
     * @param type declared type of the value
     * @return UTF-8 JSON
     * @throws SerializationException if the value cannot be serialized
     */
    public byte[] encode(Object value, Type type) {
        if (value == null) {
            return NULL_BYTES.clone();
        }
        try {
            return isKnown(type)
                    ? typedMapper.writeValueAsBytes(value)
                    : untypedMapper.writeValueAsBytes(value);
        } catch (JacksonException e) {
            throw new SerializationException("Error serializing cache value of type " + describe(type), e);
        }
    }

    /**
     * Deserializes a value.
     *
     * @param bytes UTF-8 JSON
     * @param type type used to build the value
     * @return value; null if what was stored was a null
     * @throws SerializationException if the bytes do not match the type
     */
    public Object decode(byte[] bytes, Type type) {
        if (Arrays.equals(bytes, NULL_BYTES)) {
            return null;
        }
        try {
            return isKnown(type)
                    ? typedMapper.readValue(bytes, typedMapper.constructType(type))
                    : untypedMapper.readValue(bytes, Object.class);
        } catch (JacksonException e) {
            throw new SerializationException("Error deserializing cache value as " + describe(type), e);
        }
    }

    /**
     * Tells whether the type is enough to rebuild the value without class names in the JSON.
     *
     * @param type declared type
     * @return {@code false} for {@code Object}, {@code void} and unresolved generics
     */
    private static boolean isKnown(Type type) {
        return type != null
                && type != Object.class
                && type != void.class
                && type != Void.class
                && !(type instanceof TypeVariable)
                && !(type instanceof WildcardType);
    }

    private static String describe(Type type) {
        return type == null ? "unknown type" : type.getTypeName();
    }
}
