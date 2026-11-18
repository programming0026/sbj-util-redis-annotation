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
 * Pruebas de las claves de caché que se generan cuando la anotación no indica
 * {@code value} o {@code key}.
 *
 * @author Java Universe
 */
class CacheKeyGeneratorTest {

    /** Estado de ejemplo. */
    public enum Status { OPEN }

    /** Filtro de búsqueda de ejemplo. */
    public record Filter(String name, int page) {
    }

    /** Objeto sin campos: no sirve para formar una clave. */
    public static class Empty {
    }

    /**
     * Servicio genérico de ejemplo.
     *
     * @param <T> tipo de la entidad
     */
    public abstract static class GenericService<T> {

        public T find(Long id) {
            return null;
        }

        public List<T> findAll() {
            return List.of();
        }
    }

    /** Servicio concreto, que fija el tipo genérico. */
    public static class UserService extends GenericService<String> {

        public String byName(String name) {
            return name;
        }

        public String byName(String name, int page) {
            return name + page;
        }

        public Object anything() {
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
     * Dos argumentos distintos con el mismo hashCode deben dar claves distintas.
     */
    @Test
    public void argumentsKey_ShouldDifferForArgumentsWithSameHashCode() {
        assertEquals("Aa".hashCode(), "BB".hashCode());

        assertNotEquals(CacheKeyGenerator.argumentsKey(withArgs("Aa")),
                CacheKeyGenerator.argumentsKey(withArgs("BB")));
    }

    @Test
    public void argumentsKey_WithSingleScalar_ShouldUseItsText() {
        UUID id = UUID.fromString("00000000-0000-0000-0000-000000000001");

        assertEquals("42", CacheKeyGenerator.argumentsKey(withArgs(42L)));
        assertEquals("true", CacheKeyGenerator.argumentsKey(withArgs(true)));
        assertEquals("OPEN", CacheKeyGenerator.argumentsKey(withArgs(Status.OPEN)));
        assertEquals(id.toString(), CacheKeyGenerator.argumentsKey(withArgs(id)));
    }

    /**
     * Las cadenas van entre comillas para distinguir un nulo de la cadena "null".
     */
    @Test
    public void argumentsKey_ShouldTellNullFromTheStringNull() {
        assertEquals("[\"null\"]", CacheKeyGenerator.argumentsKey(withArgs("null")));
        assertEquals("[null]", CacheKeyGenerator.argumentsKey(withArgs((Object) null)));
    }

    @Test
    public void argumentsKey_WithNoArguments_ShouldBeEmptyList() {
        assertEquals("[]", CacheKeyGenerator.argumentsKey(withArgs()));

        JoinPoint withoutArgs = withArgs();
        when(withoutArgs.getArgs()).thenReturn(null);
        assertEquals("[]", CacheKeyGenerator.argumentsKey(withoutArgs));
    }

    /**
     * Los objetos se identifican por su contenido, con las entradas de los mapas ordenadas.
     */
    @Test
    public void argumentsKey_WithObjects_ShouldUseTheirContent() {
        assertEquals("[{\"name\":\"ana\",\"page\":2},5]",
                CacheKeyGenerator.argumentsKey(withArgs(new Filter("ana", 2), 5)));
        assertEquals(CacheKeyGenerator.argumentsKey(withArgs(new Filter("ana", 2))),
                CacheKeyGenerator.argumentsKey(withArgs(new Filter("ana", 2))));
        assertEquals("[{\"a\":1,\"b\":2}]",
                CacheKeyGenerator.argumentsKey(withArgs(Map.of("b", 2, "a", 1))));
    }

    /**
     * Unos argumentos muy largos se resumen con SHA-256, que sigue dependiendo del contenido.
     */
    @Test
    public void argumentsKey_WithLongArguments_ShouldUseDigest() {
        String first = CacheKeyGenerator.argumentsKey(withArgs("x".repeat(300)));
        String second = CacheKeyGenerator.argumentsKey(withArgs("x".repeat(299) + "y"));

        assertTrue(first.startsWith("sha256-"));
        assertEquals("sha256-".length() + 64, first.length());
        assertNotEquals(first, second);
    }

    /**
     * Un argumento que no se puede convertir a JSON obliga a indicar la clave en la anotación.
     */
    @Test
    public void argumentsKey_WithUnserializableArgument_ShouldAskForExplicitKey() {
        IllegalStateException exception = assertThrows(IllegalStateException.class,
                () -> CacheKeyGenerator.argumentsKey(withArgs(new Empty())));

        assertTrue(exception.getMessage().contains("'key'"));
        assertTrue(exception.getMessage().contains("anything(..)"));
    }

    /**
     * Dos sobrecargas del mismo método deben tener nombres de caché distintos.
     */
    @Test
    public void defaultCacheName_ShouldIncludeClassMethodAndParameterTypes() {
        UserService service = new UserService();
        String one = CacheKeyGenerator.defaultCacheName(
                joinPoint(service, "byName", new Class<?>[] {String.class}, "ana"));
        String two = CacheKeyGenerator.defaultCacheName(
                joinPoint(service, "byName", new Class<?>[] {String.class, int.class}, "ana", 1));

        assertEquals(UserService.class.getName() + ".byName(String)", one);
        assertEquals(UserService.class.getName() + ".byName(String,int)", two);
    }

    @Test
    public void defaultCacheName_WithoutMethodSignature_ShouldUseLongSignature() {
        Signature signature = mock(Signature.class);
        when(signature.toLongString()).thenReturn("long signature");
        JoinPoint joinPoint = mock(JoinPoint.class);
        when(joinPoint.getSignature()).thenReturn(signature);

        assertNull(CacheKeyGenerator.methodOf(joinPoint));
        assertEquals("long signature", CacheKeyGenerator.defaultCacheName(joinPoint));
        assertEquals(Object.class, CacheKeyGenerator.returnTypeOf(joinPoint));
    }

    /**
     * El tipo de retorno genérico se resuelve contra la clase real del servicio.
     */
    @Test
    public void returnTypeOf_ShouldResolveGenericsAgainstTargetClass() {
        UserService service = new UserService();

        Type single = CacheKeyGenerator.returnTypeOf(joinPoint(service, "find", new Class<?>[] {Long.class}, 1L));
        Type list = CacheKeyGenerator.returnTypeOf(joinPoint(service, "findAll", new Class<?>[0]));

        assertEquals(String.class, single);
        ParameterizedType parameterized = assertInstanceOf(ParameterizedType.class, list);
        assertEquals(List.class, parameterized.getRawType());
        assertEquals(String.class, parameterized.getActualTypeArguments()[0]);
    }

    @Test
    public void returnTypeOf_WithoutTarget_ShouldUseDeclaredType() {
        JoinPoint joinPoint = joinPoint(new UserService(), "byName", new Class<?>[] {String.class}, "ana");
        when(joinPoint.getTarget()).thenReturn(null);

        assertEquals(String.class, CacheKeyGenerator.returnTypeOf(joinPoint));
    }
}
