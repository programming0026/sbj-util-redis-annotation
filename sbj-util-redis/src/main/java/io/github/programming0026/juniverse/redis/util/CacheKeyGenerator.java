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
 * Generates the cache keys of methods that do not specify {@code value} or {@code key}.
 *
 * <p>The key is built from the content of the arguments, not from their {@code hashCode()}:
 * two calls with different arguments never share a key, and the same call produces the
 * same key on all instances.</p>
 *
 * @author Java Universe
 */
public final class CacheKeyGenerator {

    /** Length above which the arguments are summarized with SHA-256. */
    private static final int MAX_ARGUMENTS_LENGTH = 200;

    /**
     * Mapper that always produces the same JSON for the same content: properties and map
     * entries are sorted. It reads the fields directly, so it does not depend on getters being present.
     */
    private static final ObjectMapper CANONICAL_MAPPER = JsonMapper.builder()
            .enable(MapperFeature.SORT_PROPERTIES_ALPHABETICALLY)
            .enable(SerializationFeature.ORDER_MAP_ENTRIES_BY_KEYS)
            .enable(SerializationFeature.FAIL_ON_EMPTY_BEANS)
            .disable(DateTimeFeature.WRITE_DATES_AS_TIMESTAMPS)
            .changeDefaultVisibility(visibility -> visibility
                    .withVisibility(PropertyAccessor.ALL, JsonAutoDetect.Visibility.NONE)
                    .withVisibility(PropertyAccessor.FIELD, JsonAutoDetect.Visibility.ANY))
            .build();

    private CacheKeyGenerator() {
    }

    /**
     * Returns the intercepted method.
     *
     * @param joinPoint aspect join point
     * @return method, or {@code null} if the signature is not a method's
     */
    public static Method methodOf(JoinPoint joinPoint) {
        return joinPoint.getSignature() instanceof MethodSignature signature ? signature.getMethod() : null;
    }

    /**
     * Returns the return type of the intercepted method, with generics resolved
     * against the class of the real object.
     *
     * @param joinPoint aspect join point
     * @return return type; {@code Object} if it cannot be determined
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
     * Cache name of a method that does not specify {@code value}: the class, the method and the
     * types of its parameters, so that two overloads do not share entries.
     *
     * @param joinPoint aspect join point
     * @return cache name, for example {@code com.acme.UserService.find(Long,String)}
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
     * Key of a method that does not specify {@code key}, built from the content of its arguments.
     *
     * <p>With a single numeric, boolean, enum or UUID argument, the key is its text. In the other
     * cases it is the list of arguments as JSON; if it exceeds {@value #MAX_ARGUMENTS_LENGTH} characters
     * it is replaced by its SHA-256.</p>
     *
     * @param joinPoint aspect join point
     * @return key built from the arguments
     * @throws IllegalStateException if any argument cannot be converted to JSON; in that case
     *                               the method must specify {@code key}
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
     * Tells whether the text of the value identifies it unambiguously.
     * Strings are excluded: {@code null} and {@code "null"} would give the same text.
     *
     * @param value argument
     * @return {@code true} for numbers, booleans, enums and UUIDs
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
