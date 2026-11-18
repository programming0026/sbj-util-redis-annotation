package io.github.programming0026.juniverse.redis.util;

import com.fasterxml.jackson.annotation.JsonAutoDetect;
import com.fasterxml.jackson.annotation.PropertyAccessor;
import java.lang.reflect.Method;
import java.lang.reflect.Type;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.StringJoiner;
import java.util.UUID;

import org.aspectj.lang.JoinPoint;
import org.aspectj.lang.reflect.MethodSignature;
import org.springframework.core.GenericTypeResolver;
import org.springframework.util.ClassUtils;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.MapperFeature;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.SerializationFeature;
import tools.jackson.databind.cfg.DateTimeFeature;
import tools.jackson.databind.json.JsonMapper;

/**
 * Genera las claves de caché de los métodos que no indican {@code value} o {@code key}.
 *
 * <p>La clave se construye con el contenido de los argumentos, no con su {@code hashCode()}:
 * dos llamadas con argumentos distintos nunca comparten clave, y la misma llamada produce la
 * misma clave en todas las instancias.</p>
 *
 * @author Java Universe
 */
public final class CacheKeyGenerator {

    /** Longitud a partir de la cual los argumentos se resumen con SHA-256. */
    private static final int MAX_ARGUMENTS_LENGTH = 200;

    /**
     * Conversor que da siempre el mismo JSON para el mismo contenido: propiedades y entradas
     * de mapas ordenadas. Lee los campos directamente, así no depende de que haya getters.
     */
    private static final ObjectMapper CANONICAL_MAPPER = JsonMapper.builder()
            .enable(MapperFeature.SORT_PROPERTIES_ALPHABETICALLY)
            .enable(SerializationFeature.ORDER_MAP_ENTRIES_BY_KEYS)
            // Un objeto sin campos daría "{}" para cualquier instancia: mejor fallar.
            .enable(SerializationFeature.FAIL_ON_EMPTY_BEANS)
            .disable(DateTimeFeature.WRITE_DATES_AS_TIMESTAMPS)
            .changeDefaultVisibility(visibility -> visibility
                    .withVisibility(PropertyAccessor.ALL, JsonAutoDetect.Visibility.NONE)
                    .withVisibility(PropertyAccessor.FIELD, JsonAutoDetect.Visibility.ANY))
            .build();

    private CacheKeyGenerator() {
    }

    /**
     * Devuelve el método interceptado.
     *
     * @param joinPoint punto de unión del aspecto
     * @return método, o {@code null} si la firma no es de un método
     */
    public static Method methodOf(JoinPoint joinPoint) {
        return joinPoint.getSignature() instanceof MethodSignature signature ? signature.getMethod() : null;
    }

    /**
     * Devuelve el tipo de retorno del método interceptado, con los genéricos resueltos
     * contra la clase del objeto real.
     *
     * @param joinPoint punto de unión del aspecto
     * @return tipo de retorno; {@code Object} si no se puede determinar
     */
    public static Type returnTypeOf(JoinPoint joinPoint) {
        Method method = methodOf(joinPoint);
        if (method == null) {
            return Object.class;
        }
        Object target = joinPoint.getTarget();
        Class<?> contextClass = target != null ? ClassUtils.getUserClass(target) : method.getDeclaringClass();
        return GenericTypeResolver.resolveType(method.getGenericReturnType(), contextClass);
    }

    /**
     * Nombre de caché de un método que no indica {@code value}: la clase, el método y los
     * tipos de sus parámetros, de modo que dos sobrecargas no compartan entradas.
     *
     * @param joinPoint punto de unión del aspecto
     * @return nombre de caché, por ejemplo {@code com.acme.UserService.find(Long,String)}
     */
    public static String defaultCacheName(JoinPoint joinPoint) {
        Method method = methodOf(joinPoint);
        if (method == null) {
            return joinPoint.getSignature().toLongString();
        }
        StringJoiner parameters = new StringJoiner(",", "(", ")");
        for (Class<?> parameterType : method.getParameterTypes()) {
            parameters.add(parameterType.getSimpleName());
        }
        return method.getDeclaringClass().getName() + "." + method.getName() + parameters;
    }

    /**
     * Clave de un método que no indica {@code key}, construida con el contenido de sus argumentos.
     *
     * <p>Con un solo argumento numérico, booleano, enum o UUID la clave es su texto. En los demás
     * casos es la lista de argumentos en JSON; si pasa de {@value #MAX_ARGUMENTS_LENGTH} caracteres
     * se sustituye por su SHA-256.</p>
     *
     * @param joinPoint punto de unión del aspecto
     * @return clave de los argumentos
     * @throws IllegalStateException si algún argumento no se puede convertir a JSON; en ese caso
     *                               el método debe indicar {@code key}
     */
    public static String argumentsKey(JoinPoint joinPoint) {
        Object[] args = joinPoint.getArgs();
        if (args == null) {
            args = new Object[0];
        }
        if (args.length == 1 && isPlainScalar(args[0])) {
            return args[0] instanceof Enum<?> constant ? constant.name() : args[0].toString();
        }

        String json;
        try {
            json = CANONICAL_MAPPER.writeValueAsString(args);
        } catch (JacksonException e) {
            throw new IllegalStateException(
                    "Cannot build a cache key from the arguments of " + joinPoint.getSignature().toShortString()
                    + ". Set the 'key' attribute of the annotation with a SpEL expression. Cause: "
                    + e.getOriginalMessage(), e);
        }
        return json.length() <= MAX_ARGUMENTS_LENGTH ? json : sha256(json);
    }

    /**
     * Indica si el texto del valor lo identifica sin ambigüedad.
     * Las cadenas no entran: {@code null} y {@code "null"} darían el mismo texto.
     *
     * @param value argumento
     * @return {@code true} para números, booleanos, enums y UUID
     */
    private static boolean isPlainScalar(Object value) {
        return value instanceof Number || value instanceof Boolean || value instanceof Enum<?>
                || value instanceof UUID;
    }

    private static String sha256(String text) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return "sha256-" + HexFormat.of().formatHex(digest.digest(text.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 is not available", e);
        }
    }
}
