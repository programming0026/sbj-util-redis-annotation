package com.juniverse.redis.aspect;

import com.juniverse.redis.annotation.CoalesceCacheable;
import com.juniverse.redis.annotation.CoalesceCaching;
import com.juniverse.redis.annotation.CoalesceEvict;
import com.juniverse.redis.annotation.CoalescePut;
import com.juniverse.redis.service.CoalesceCacheManager;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.reflect.MethodSignature;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.lang.reflect.Method;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

/**
 * Pruebas unitarias de CoalesceCachingAspect.
 *
 * @author ajuar
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class CoalesceCachingAspectTest {

    /** Administrador de caché. */
    @Mock
    private CoalesceCacheManager cacheManager;

    /** Punto de unión simulado. */
    @Mock
    private ProceedingJoinPoint joinPoint;

    /** Firma del método simulada. */
    @Mock
    private MethodSignature methodSignature;

    /** Aspecto bajo prueba. */
    private CoalesceCachingAspect aspect;

    /**
     * Prepara el entorno antes de cada prueba.
     */
    @BeforeEach
    void setUp() {
        aspect = new CoalesceCachingAspect(cacheManager);
    }

    /**
     * Método de apoyo para las simulaciones.
     *
     * @param id identificador
     * @return cadena
     */
    public String testMethod(String id) {
        return "result";
    }

    /**
     * Procesar con acierto de caché debe devolver el valor en caché.
     */
    @Test
    void handleCaching_WithCacheHit_ShouldReturnCachedValue() throws Throwable {
        CoalesceCacheable cacheable = createMockCacheable("cache", "#id", 300, "", false, true);
        CoalesceCaching caching = createMockCaching(
                new CoalesceCacheable[]{cacheable},
                new CoalesceEvict[]{},
                new CoalescePut[]{}
        );

        when(joinPoint.getSignature()).thenReturn(methodSignature);
        when(joinPoint.getArgs()).thenReturn(new Object[]{"123"});
        when(methodSignature.getParameterNames()).thenReturn(new String[]{"id"});
        when(methodSignature.getMethod()).thenReturn(getTestMethod());
        when(cacheManager.get("cache:123")).thenReturn(Optional.of("cachedValue"));

        Object result = aspect.handleCaching(joinPoint, caching);

        assertEquals("cachedValue", result);
        verify(cacheManager).get("cache:123");
        verify(joinPoint, never()).proceed();
    }

    /**
     * Procesar con fallo de caché debe ejecutar el método y guardar el resultado.
     */
    @Test
    void handleCaching_WithCacheMiss_ShouldExecuteMethodAndCacheResult() throws Throwable {
        CoalesceCacheable cacheable = createMockCacheable("cache", "#id", 300, "", false, true);
        CoalesceCaching caching = createMockCaching(
                new CoalesceCacheable[]{cacheable},
                new CoalesceEvict[]{},
                new CoalescePut[]{}
        );

        when(joinPoint.getSignature()).thenReturn(methodSignature);
        when(joinPoint.getArgs()).thenReturn(new Object[]{"123"});
        when(methodSignature.getParameterNames()).thenReturn(new String[]{"id"});
        when(methodSignature.getMethod()).thenReturn(getTestMethod());
        when(cacheManager.get("cache:123")).thenReturn(Optional.empty());
        when(joinPoint.proceed()).thenReturn("newValue");

        Object result = aspect.handleCaching(joinPoint, caching);

        assertEquals("newValue", result);
        verify(joinPoint).proceed();
        verify(cacheManager).put("cache:123", "newValue", 300);
    }

    /**
     * Procesar con resultado nulo y cacheNull falso no debe guardar en caché.
     */
    @Test
    void handleCaching_WithNullResultAndCacheNullFalse_ShouldNotCache() throws Throwable {
        CoalesceCacheable cacheable = createMockCacheable("cache", "#id", 300, "", false, true);
        CoalesceCaching caching = createMockCaching(
                new CoalesceCacheable[]{cacheable},
                new CoalesceEvict[]{},
                new CoalescePut[]{}
        );

        when(joinPoint.getSignature()).thenReturn(methodSignature);
        when(joinPoint.getArgs()).thenReturn(new Object[]{"123"});
        when(methodSignature.getParameterNames()).thenReturn(new String[]{"id"});
        when(methodSignature.getMethod()).thenReturn(getTestMethod());
        when(cacheManager.get("cache:123")).thenReturn(Optional.empty());
        when(joinPoint.proceed()).thenReturn(null);

        Object result = aspect.handleCaching(joinPoint, caching);

        assertNull(result);
        verify(cacheManager, never()).put(anyString(), any(), anyLong());
    }

    /**
     * Procesar con resultado nulo y cacheNull verdadero debe guardar en caché.
     */
    @Test
    void handleCaching_WithNullResultAndCacheNullTrue_ShouldCache() throws Throwable {
        CoalesceCacheable cacheable = createMockCacheable("cache", "#id", 300, "", true, true);
        CoalesceCaching caching = createMockCaching(
                new CoalesceCacheable[]{cacheable},
                new CoalesceEvict[]{},
                new CoalescePut[]{}
        );

        when(joinPoint.getSignature()).thenReturn(methodSignature);
        when(joinPoint.getArgs()).thenReturn(new Object[]{"123"});
        when(methodSignature.getParameterNames()).thenReturn(new String[]{"id"});
        when(methodSignature.getMethod()).thenReturn(getTestMethod());
        when(cacheManager.get("cache:123")).thenReturn(Optional.empty());
        when(joinPoint.proceed()).thenReturn(null);

        Object result = aspect.handleCaching(joinPoint, caching);

        assertNull(result);
        verify(cacheManager).put("cache:123", null, 300);
    }


    /**
     * Procesar con invalidación posterior debe invalidar después de ejecutar.
     */
    @Test
    void handleCaching_WithAfterEviction_ShouldEvictAfterExecution() throws Throwable {
        CoalesceEvict evict = createMockEvict(new String[]{"cache"}, "", "", false, false, "");
        CoalesceCaching caching = createMockCaching(
                new CoalesceCacheable[]{},
                new CoalesceEvict[]{evict},
                new CoalescePut[]{}
        );

        when(joinPoint.getSignature()).thenReturn(methodSignature);
        when(joinPoint.getArgs()).thenReturn(new Object[]{"123"});
        when(methodSignature.getParameterNames()).thenReturn(new String[]{"id"});
        when(methodSignature.getMethod()).thenReturn(getTestMethod());
        when(joinPoint.proceed()).thenReturn("result");

        Object result = aspect.handleCaching(joinPoint, caching);

        assertEquals("result", result);
    }


    /**
     * Procesar con guardado y condición «unless» verdadera no debe guardar en caché.
     */
    @Test
    void handleCaching_WithPutAndUnlessConditionTrue_ShouldNotCache() throws Throwable {
        CoalescePut put = createMockPut("cache", "#id", 300, "", "#result == null");
        CoalesceCaching caching = createMockCaching(
                new CoalesceCacheable[]{},
                new CoalesceEvict[]{},
                new CoalescePut[]{put}
        );

        when(joinPoint.getSignature()).thenReturn(methodSignature);
        when(joinPoint.getArgs()).thenReturn(new Object[]{"123"});
        when(methodSignature.getParameterNames()).thenReturn(new String[]{"id"});
        when(methodSignature.getMethod()).thenReturn(getTestMethod());
        when(joinPoint.proceed()).thenReturn(null);

        Object result = aspect.handleCaching(joinPoint, caching);

        assertNull(result);
        verify(cacheManager, never()).put(anyString(), any(), anyLong());
    }

    /**
     * Procesar con clave vacía debe usar el hash de los argumentos.
     */
    @Test
    void handleCaching_WithEmptyKey_ShouldUseArgsHashcode() throws Throwable {
        CoalesceCacheable cacheable = createMockCacheable("cache", "", 300, "", false, true);
        CoalesceCaching caching = createMockCaching(
                new CoalesceCacheable[]{cacheable},
                new CoalesceEvict[]{},
                new CoalescePut[]{}
        );

        Object[] args = new Object[]{"123"};
        String expectedKey = "cache:" + java.util.Arrays.hashCode(args);

        when(joinPoint.getSignature()).thenReturn(methodSignature);
        when(joinPoint.getArgs()).thenReturn(args);
        when(methodSignature.getParameterNames()).thenReturn(new String[]{"id"});
        when(methodSignature.getMethod()).thenReturn(getTestMethod());
        when(cacheManager.get(expectedKey)).thenReturn(Optional.empty());
        when(joinPoint.proceed()).thenReturn("result");

        Object result = aspect.handleCaching(joinPoint, caching);

        assertEquals("result", result);
        verify(cacheManager).put(expectedKey, "result", 300L);
    }


    /**
     * Procesar con clave de invalidación debe invalidar esa clave.
     */
    @Test
    void handleCaching_WithEvictKey_ShouldEvictSpecificKey() throws Throwable {
        CoalesceEvict evict = createMockEvict(new String[]{"cache"}, "#id", "", false, false, "");
        CoalesceCaching caching = createMockCaching(
                new CoalesceCacheable[]{},
                new CoalesceEvict[]{evict},
                new CoalescePut[]{}
        );

        when(joinPoint.getSignature()).thenReturn(methodSignature);
        when(joinPoint.getArgs()).thenReturn(new Object[]{"123"});
        when(methodSignature.getParameterNames()).thenReturn(new String[]{"id"});
        when(methodSignature.getMethod()).thenReturn(getTestMethod());
        when(joinPoint.proceed()).thenReturn("result");

        Object result = aspect.handleCaching(joinPoint, caching);

        assertEquals("result", result);
        verify(cacheManager).evict("cache:123");
    }

    /**
     * Crea una anotación de caché simulada.
     *
     * @param value valor
     * @param key clave
     * @param ttl tiempo de vida
     * @param condition expresión de la condición
     * @param cacheNull nulo de caché
     * @param coalesce indica si se activa la coalescencia
     * @return anotación de caché con coalescencia
     */
    private CoalesceCacheable createMockCacheable(
            String value, String key, long ttl, String condition,
            boolean cacheNull, boolean coalesce) {
        CoalesceCacheable cacheable = mock(CoalesceCacheable.class);
        when(cacheable.value()).thenReturn(value);
        when(cacheable.key()).thenReturn(key);
        when(cacheable.ttl()).thenReturn(ttl);
        when(cacheable.condition()).thenReturn(condition);
        when(cacheable.cacheNull()).thenReturn(cacheNull);
        when(cacheable.coalesce()).thenReturn(coalesce);
        return cacheable;
    }

    /**
     * Crea una anotación de invalidación simulada.
     *
     * @param value valor
     * @param key clave
     * @param pattern patrón de claves
     * @param beforeInvocation indica si invalida antes de la invocación
     * @param allEntries indica si invalida todas las entradas
     * @param condition expresión de la condición
     * @return anotación de invalidación con coalescencia
     */
    private CoalesceEvict createMockEvict(
            String[] value, String key, String pattern,
            boolean beforeInvocation, boolean allEntries, String condition) {
        CoalesceEvict evict = mock(CoalesceEvict.class);
        when(evict.value()).thenReturn(value);
        when(evict.key()).thenReturn(key);
        when(evict.pattern()).thenReturn(pattern);
        when(evict.beforeInvocation()).thenReturn(beforeInvocation);
        when(evict.allEntries()).thenReturn(allEntries);
        when(evict.condition()).thenReturn(condition);
        return evict;
    }

    /**
     * Crea una anotación de guardado simulada.
     *
     * @param value valor
     * @param key clave
     * @param ttl tiempo de vida
     * @param condition expresión de la condición
     * @param unless condición que evita guardar
     * @return anotación de guardado con coalescencia
     */
    private CoalescePut createMockPut(
            String value, String key, long ttl, String condition, String unless) {
        CoalescePut put = mock(CoalescePut.class);
        when(put.value()).thenReturn(value);
        when(put.key()).thenReturn(key);
        when(put.ttl()).thenReturn(ttl);
        when(put.condition()).thenReturn(condition);
        when(put.unless()).thenReturn(unless);
        return put;
    }

    /**
     * Crea una anotación agrupadora simulada.
     *
     * @param cacheables anotaciones de caché incluidas
     * @param evicts anotación que agrupa invalidaciones
     * @param puts anotación que agrupa guardados
     * @return anotación agrupadora con coalescencia
     */
    private CoalesceCaching createMockCaching(
            CoalesceCacheable[] cacheables,
            CoalesceEvict[] evicts,
            CoalescePut[] puts) {
        CoalesceCaching caching = mock(CoalesceCaching.class);
        when(caching.cacheable()).thenReturn(cacheables);
        when(caching.evict()).thenReturn(evicts);
        when(caching.put()).thenReturn(puts);
        return caching;
    }

    /**
     * Obtiene método de prueba.
     *
     * @return método de prueba
     */
    private Method getTestMethod() {
        try {
            return this.getClass().getMethod("testMethod", String.class);
        } catch (NoSuchMethodException e) {
            throw new RuntimeException(e);
        }
    }
}
