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
 * Convierte los valores de la caché a JSON y de vuelta.
 *
 * <p>Cuando se conoce el tipo del valor (el tipo de retorno del método en caché), el JSON no
 * lleva nombres de clase: al leer se construye exactamente ese tipo. Así se conservan los
 * records, las listas inmutables, {@code Long}, las fechas y los enums, y lo que haya en Redis
 * no puede elegir qué clases instancia la aplicación.</p>
 *
 * <p>Cuando el tipo no dice nada ({@code Object}, {@code void} o un genérico sin resolver) se usa
 * el {@code ObjectMapper} configurado tal cual, que por defecto incluye el nombre de la clase
 * en el JSON. Ese formato tiene límites conocidos: no conserva records, colecciones inmutables
 * ni {@code Long} como valor raíz.</p>
 *
 * <p>Un valor nulo se guarda como el literal JSON {@code null}; así una clave con un nulo
 * guardado se distingue de una clave que no existe.</p>
 *
 * @author Java Universe
 */
public class CacheValueCodec {

    /** Representación de un valor nulo guardado. */
    private static final byte[] NULL_BYTES = "null".getBytes(StandardCharsets.UTF_8);

    /** Conversor sin nombres de clase, para los valores de tipo conocido. */
    private final ObjectMapper typedMapper;

    /** Conversor configurado, para los valores de tipo desconocido. */
    private final ObjectMapper untypedMapper;

    /**
     * Crea el conversor a partir del {@code ObjectMapper} configurado. Para los valores de tipo
     * conocido se usa una copia con la misma configuración y sin tipado por defecto.
     *
     * @param configuredMapper conversor de {@code ObjectMapperConfig}
     */
    public CacheValueCodec(ObjectMapper configuredMapper) {
        this.untypedMapper = configuredMapper;
        this.typedMapper = configuredMapper.rebuild().deactivateDefaultTyping().build();
    }

    /**
     * Serializa un valor.
     *
     * @param value valor; puede ser nulo
     * @param type tipo declarado del valor
     * @return JSON en UTF-8
     * @throws SerializationException si el valor no se puede serializar
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
     * Deserializa un valor.
     *
     * @param bytes JSON en UTF-8
     * @param type tipo con el que se construye el valor
     * @return valor; nulo si lo guardado era un nulo
     * @throws SerializationException si los bytes no corresponden al tipo
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
     * Indica si el tipo basta para reconstruir el valor sin nombres de clase en el JSON.
     *
     * @param type tipo declarado
     * @return {@code false} para {@code Object}, {@code void} y los genéricos sin resolver
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
