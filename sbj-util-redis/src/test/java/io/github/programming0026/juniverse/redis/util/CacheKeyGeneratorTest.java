package io.github.programming0026.juniverse.redis.util;

import java.lang.reflect.Method;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.aspectj.lang.JoinPoint;
import org.aspectj.lang.Signature;
import org.aspectj.lang.reflect.MethodSignature;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Tests for the cache keys generated when the annotation does not specify
 * {@code value} or {@code key}.
 *
 * @author Java Universe
 */
class CacheKeyGeneratorTest {

    /** Sample state. */
     private enum Status { OPEN }

    /** Sample search filter. */
    private record Filter(String name, int page) {
    }

    /** Object without fields: it cannot be used to build a key. */
    private static class Empty {
    }

    /**
     * Sample generic service.
     *
     * @param <T> entity type
     */
     public abstract static class GenericService<T> {

         public T find(Long id) {
            return null;
        }

        public List<T> findAll() {
            return List.of();
        }
    }

    /** Concrete service, which fixes the generic type. */
     public static class UserService extends GenericService<String> {

        public String byName(String name) {
            return name;
        }

        public String byName(String name, int page) {
            return name + page;
        }

        public  Object anything() {
            return null;
        }
    }

    private static JoinPoint joinPoint(Object target, String methodName, Class<?>[] parameterTypes, Object... args) {
        try {
            Method method = target.getClass().getMethod(methodName, parameterTypes);
            MethodSignature signature = mock(MethodSignature.class);
            when(signature.getMethod()).thenReturn(method);
            when(signature.toShortString()).thenReturn(methodName + "(..)");
            JoinPoint joinPoint = mock(JoinPoint.class);
            when(joinPoint.getSignature()).thenReturn(signature);
            when(joinPoint.getTarget()).thenReturn(target);
            when(joinPoint.getArgs()).thenReturn(args);
            return joinPoint;
        } catch (NoSuchMethodException e) {
            throw new IllegalStateException(e);
        }
    }

    private static JoinPoint withArgs(Object... args) {
        return joinPoint(new UserService(), "anything", new Class<?>[0], args);
    }

    /**
     * Two different arguments with the same hashCode must give different keys.
     */
    @Test
     void argumentsKey_ShouldDifferForArgumentsWithSameHashCode() {
        assertEquals("Aa".hashCode(), "BB".hashCode());

        assertNotEquals(CacheKeyGenerator.argumentsKey(withArgs("Aa")),
                CacheKeyGenerator.argumentsKey(withArgs("BB")));
    }

    @Test
     void argumentsKey_WithSingleScalar_ShouldUseItsText() {
        UUID id = UUID.fromString("00000000-0000-0000-0000-000000000001");

        assertEquals("42", CacheKeyGenerator.argumentsKey(withArgs(42L)));
        assertEquals("true", CacheKeyGenerator.argumentsKey(withArgs(true)));
        assertEquals("OPEN", CacheKeyGenerator.argumentsKey(withArgs(Status.OPEN)));
        assertEquals(id.toString(), CacheKeyGenerator.argumentsKey(withArgs(id)));
    }

    /**
     * Strings are quoted to distinguish a null from the string "null".
     */
    @Test
     void argumentsKey_ShouldTellNullFromTheStringNull() {
        assertEquals("[\"null\"]", CacheKeyGenerator.argumentsKey(withArgs("null")));
        assertEquals("[null]", CacheKeyGenerator.argumentsKey(withArgs((Object) null)));
    }

    @Test
     void argumentsKey_WithNoArguments_ShouldBeEmptyList() {
        assertEquals("[]", CacheKeyGenerator.argumentsKey(withArgs()));

        JoinPoint withoutArgs = withArgs();
        when(withoutArgs.getArgs()).thenReturn(null);
        assertEquals("[]", CacheKeyGenerator.argumentsKey(withoutArgs));
    }

    /**
     * Objects are identified by their content, with map entries sorted.
     */
    @Test
     void argumentsKey_WithObjects_ShouldUseTheirContent() {
        assertEquals("[{\"name\":\"ana\",\"page\":2},5]",
                CacheKeyGenerator.argumentsKey(withArgs(new Filter("ana", 2), 5)));
        assertEquals(CacheKeyGenerator.argumentsKey(withArgs(new Filter("ana", 2))),
                CacheKeyGenerator.argumentsKey(withArgs(new Filter("ana", 2))));
        assertEquals("[{\"a\":1,\"b\":2}]",
                CacheKeyGenerator.argumentsKey(withArgs(Map.of("b", 2, "a", 1))));
    }

    /**
     * Very long arguments are summarized with SHA-256, which still depends on the content.
     */
    @Test
     void argumentsKey_WithLongArguments_ShouldUseDigest() {
        String first = CacheKeyGenerator.argumentsKey(withArgs("x".repeat(300)));
        String second = CacheKeyGenerator.argumentsKey(withArgs("x".repeat(299) + "y"));

        assertTrue(first.startsWith("sha256-"));
        assertEquals("sha256-".length() + 64, first.length());
        assertNotEquals(first, second);
    }

    /**
     * An argument that cannot be converted to JSON forces the key to be specified in the annotation.
     */
    @Test
     void argumentsKey_WithUnserializableArgument_ShouldAskForExplicitKey() {
        IllegalStateException exception = assertThrows(IllegalStateException.class,
                () -> CacheKeyGenerator.argumentsKey(withArgs(new Empty())));

        assertTrue(exception.getMessage().contains("'key'"));
        assertTrue(exception.getMessage().contains("anything(..)"));
    }

    /**
     * Two overloads of the same method must have different cache names.
     */
    @Test
     void defaultCacheName_ShouldIncludeClassMethodAndParameterTypes() {
        UserService service = new UserService();
        String one = CacheKeyGenerator.defaultCacheName(
                joinPoint(service, "byName", new Class<?>[] {String.class}, "ana"));
        String two = CacheKeyGenerator.defaultCacheName(
                joinPoint(service, "byName", new Class<?>[] {String.class, int.class}, "ana", 1));

        assertEquals(UserService.class.getName() + ".byName(String)", one);
        assertEquals(UserService.class.getName() + ".byName(String,int)", two);
    }

    @Test
     void defaultCacheName_WithoutMethodSignature_ShouldUseLongSignature() {
        Signature signature = mock(Signature.class);
        when(signature.toLongString()).thenReturn("long signature");
        JoinPoint joinPoint = mock(JoinPoint.class);
        when(joinPoint.getSignature()).thenReturn(signature);

        assertNull(CacheKeyGenerator.methodOf(joinPoint));
        assertEquals("long signature", CacheKeyGenerator.defaultCacheName(joinPoint));
        assertEquals(Object.class, CacheKeyGenerator.returnTypeOf(joinPoint));
    }

    /**
     * The generic return type is resolved against the real class of the service.
     */
    @Test
     void returnTypeOf_ShouldResolveGenericsAgainstTargetClass() {
        UserService service = new UserService();

        Type single = CacheKeyGenerator.returnTypeOf(joinPoint(service, "find", new Class<?>[] {Long.class}, 1L));
        Type list = CacheKeyGenerator.returnTypeOf(joinPoint(service, "findAll", new Class<?>[0]));

        assertEquals(String.class, single);
        ParameterizedType parameterized = assertInstanceOf(ParameterizedType.class, list);
        assertEquals(List.class, parameterized.getRawType());
        assertEquals(String.class, parameterized.getActualTypeArguments()[0]);
    }

    @Test
     void returnTypeOf_WithoutTarget_ShouldUseDeclaredType() {
        JoinPoint joinPoint = joinPoint(new UserService(), "byName", new Class<?>[] {String.class}, "ana");
        when(joinPoint.getTarget()).thenReturn(null);

        assertEquals(String.class, CacheKeyGenerator.returnTypeOf(joinPoint));
    }
}
