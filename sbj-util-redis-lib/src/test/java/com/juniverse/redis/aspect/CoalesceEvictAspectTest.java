package com.juniverse.redis.aspect;

import com.juniverse.redis.annotation.CoalesceEvict;
import com.juniverse.redis.annotation.CoalesceEvicts;
import com.juniverse.redis.service.CoalesceCacheManager;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.Signature;
import org.aspectj.lang.reflect.MethodSignature;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.lang.reflect.Method;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.anyString;

import static org.mockito.Mockito.*;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

/**
 * Pruebas unitarias de CoalesceEvictAspect.
 *
 * @author ajuar
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class CoalesceEvictAspectTest {

    /** Administrador de caché. */
    @Mock
    private CoalesceCacheManager cacheManager;

    /** Punto de unión simulado. */
    @Mock
    private ProceedingJoinPoint joinPoint;

    /** Firma del método simulada. */
    @Mock
    private MethodSignature methodSignature;

    /** Firma del método. */
    @Mock
    private Signature signature;

    /** Aspecto bajo prueba. */
    private CoalesceEvictAspect aspect;

    /**
     * Prepara el entorno antes de cada prueba.
     */
    @BeforeEach
    void setUp() {
        aspect = new CoalesceEvictAspect(cacheManager);
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
     * Una invalidación con todas las entradas debe invalidar todo.
     */
    @Test
    void handleSingleEvict_WithAllEntries_ShouldEvictAll() throws Throwable {
        CoalesceEvict evict = createMockEvict(new String[] { "cache" }, "", "", true, true, "");
        setupJoinPoint();
        when(joinPoint.proceed()).thenReturn("result");

        Object result = aspect.handleSingleEvict(joinPoint, evict);

        assertEquals("result", result);
        verify(cacheManager).evictAll("cache");
        verify(joinPoint).proceed();
    }



    /**
     * Una invalidación con clave debe invalidar esa clave.
     */
    @Test
    void handleSingleEvict_WithKey_ShouldEvictSpecificKey() throws Throwable {
        CoalesceEvict evict = createMockEvict(new String[] { "cache" }, "#id", "", true, false, "");
        setupJoinPoint();
        when(joinPoint.proceed()).thenReturn("result");

        Object result = aspect.handleSingleEvict(joinPoint, evict);

        assertEquals("result", result);
        verify(cacheManager).evict("cache:123");
        verify(joinPoint).proceed();
    }

    /**
     * Una invalidación con clave vacía debe usar el hash de los argumentos.
     */
    @Test
    void handleSingleEvict_WithEmptyKey_ShouldUseArgsHashcode() throws Throwable {
        CoalesceEvict evict = createMockEvict(new String[] { "cache" }, "", "", true, false, "");
        Object[] args = new Object[] { "123" };
        String expectedKey = "cache:" + java.util.Arrays.hashCode(args);

        when(joinPoint.getSignature()).thenReturn(methodSignature);
        when(joinPoint.getArgs()).thenReturn(args);
        when(methodSignature.getParameterNames()).thenReturn(new String[] { "id" });
        when(methodSignature.getMethod()).thenReturn(getTestMethod());
        when(joinPoint.proceed()).thenReturn("result");

        Object result = aspect.handleSingleEvict(joinPoint, evict);

        assertEquals("result", result);
        verify(cacheManager).evict(expectedKey);
    }

    /**
     * Una invalidación con valor vacío debe usar la firma del método.
     */
    @Test
    void handleSingleEvict_WithEmptyValue_ShouldUseMethodSignature() throws Throwable {
        CoalesceEvict evict = createMockEvict(new String[] {}, "", "", true, false, "");

        when(joinPoint.getSignature()).thenReturn(signature);
        when(signature.toShortString()).thenReturn("testMethod");
        when(joinPoint.getArgs()).thenReturn(new Object[] { "123" });
        when(joinPoint.proceed()).thenReturn("result");

        Object result = aspect.handleSingleEvict(joinPoint, evict);

        assertEquals("result", result);
        verify(cacheManager).evict(anyString());
    }

    /**
     * Una invalidación previa a la invocación debe invalidar antes de continuar.
     */
    @Test
    void handleSingleEvict_BeforeInvocation_ShouldEvictBeforeProceed() throws Throwable {
        CoalesceEvict evict = createMockEvict(new String[] { "cache" }, "", "", true, true, "");
        setupJoinPoint();
        when(joinPoint.proceed()).thenReturn("result");

        aspect.handleSingleEvict(joinPoint, evict);

        verify(cacheManager).evictAll("cache");
        verify(joinPoint).proceed();
    }

    /**
     * Una invalidación posterior a la invocación debe invalidar después de continuar.
     */
    @Test
    void handleSingleEvict_AfterInvocation_ShouldEvictAfterProceed() throws Throwable {
        CoalesceEvict evict = createMockEvict(new String[] { "cache" }, "", "", false, true, "");
        setupJoinPoint();
        when(joinPoint.proceed()).thenReturn("result");

        aspect.handleSingleEvict(joinPoint, evict);

        verify(joinPoint).proceed();
        verify(cacheManager).evictAll("cache");
    }

    /**
     * Una invalidación con condición falsa no debe invalidar.
     */
    @Test
    void handleSingleEvict_WithConditionFalse_ShouldNotEvict() throws Throwable {
        CoalesceEvict evict = createMockEvict(new String[] { "cache" }, "", "", true, true, "#id == '999'");
        setupJoinPoint();
        when(joinPoint.proceed()).thenReturn("result");

        Object result = aspect.handleSingleEvict(joinPoint, evict);

        assertEquals("result", result);
        verify(cacheManager, never()).evictAll(anyString());
    }

    /**
     * Una invalidación con condición verdadera debe invalidar.
     */
    @Test
    void handleSingleEvict_WithConditionTrue_ShouldEvict() throws Throwable {
        CoalesceEvict evict = createMockEvict(new String[] { "cache" }, "", "", true, true, "#id == '123'");
        setupJoinPoint();
        when(joinPoint.proceed()).thenReturn("result");

        Object result = aspect.handleSingleEvict(joinPoint, evict);

        assertEquals("result", result);
        verify(cacheManager).evictAll("cache");
    }

    /**
     * Una invalidación con excepción debe relanzar la excepción.
     */
    @Test
    void handleSingleEvict_WithException_ShouldThrowException() throws Throwable {
        CoalesceEvict evict = createMockEvict(new String[] { "cache" }, "", "", true, true, "");
        setupJoinPoint();
        when(joinPoint.proceed()).thenThrow(new RuntimeException("Test exception"));

        assertThrows(RuntimeException.class, () -> aspect.handleSingleEvict(joinPoint, evict));
        verify(cacheManager).evictAll("cache");
    }

    /**
     * Varias invalidaciones deben procesarse todas.
     */
    @Test
    void handleMultipleEvicts_ShouldProcessAllEvictions() throws Throwable {
        CoalesceEvict evict1 = createMockEvict(new String[] { "cache1" }, "", "", true, true, "");
        CoalesceEvict evict2 = createMockEvict(new String[] { "cache2" }, "", "", true, true, "");
        CoalesceEvicts evicts = createMockEvicts(new CoalesceEvict[] { evict1, evict2 });

        setupJoinPoint();
        when(joinPoint.proceed()).thenReturn("result");

        Object result = aspect.handleMultipleEvicts(joinPoint, evicts);

        assertEquals("result", result);
        verify(cacheManager).evictAll("cache1");
        verify(cacheManager).evictAll("cache2");
    }

    /**
     * Varias invalidaciones con momentos mezclados deben respetar el orden.
     */
    @Test
    void handleMultipleEvicts_WithMixedTiming_ShouldRespectOrder() throws Throwable {
        CoalesceEvict evict1 = createMockEvict(new String[] { "cache1" }, "", "", true, true, "");
        CoalesceEvict evict2 = createMockEvict(new String[] { "cache2" }, "", "", false, true, "");
        CoalesceEvicts evicts = createMockEvicts(new CoalesceEvict[] { evict1, evict2 });

        setupJoinPoint();
        when(joinPoint.proceed()).thenReturn("result");

        aspect.handleMultipleEvicts(joinPoint, evicts);

        verify(cacheManager).evictAll("cache1");
        verify(cacheManager).evictAll("cache2");
        verify(joinPoint).proceed();
    }

    /**
     * Varias invalidaciones con todas las entradas deben invalidar todo para varios
     * prefijos.
     */
    @Test
    void handleMultipleEvicts_WithAllEntries_ShouldEvictAllForMultiplePrefixes() throws Throwable {
        CoalesceEvict evict = createMockEvict(
                new String[] { "cache1", "cache2" }, "", "", true, true, "");
        CoalesceEvicts evicts = createMockEvicts(new CoalesceEvict[] { evict });

        setupJoinPoint();
        when(joinPoint.proceed()).thenReturn("result");

        aspect.handleMultipleEvicts(joinPoint, evicts);

        verify(cacheManager).evictAll("cache1");
        verify(cacheManager).evictAll("cache2");
    }

    /**
     * Una invalidación con expresión de patrón debe evaluar el patrón.
     */
    @Test
    void handleSingleEvict_WithPatternExpression_ShouldEvaluatePattern() throws Throwable {
        CoalesceEvict evict = createMockEvict(new String[] {}, "", "'cache:' + #id", true, false, "");
        setupJoinPoint();
        when(joinPoint.proceed()).thenReturn("result");

        Object result = aspect.handleSingleEvict(joinPoint, evict);

        assertEquals("result", result);
        verify(cacheManager).evictPattern("cache:123");
    }

    /**
     * Prepara el punto de unión con valores predeterminados.
     */
    private void setupJoinPoint() {
        when(joinPoint.getSignature()).thenReturn(methodSignature);
        when(joinPoint.getArgs()).thenReturn(new Object[] { "123" });
        when(methodSignature.getParameterNames()).thenReturn(new String[] { "id" });
        when(methodSignature.getMethod()).thenReturn(getTestMethod());
    }

    /**
     * Crea una anotación de invalidación simulada.
     *
     * @param value            valor
     * @param key              clave
     * @param pattern          patrón
     * @param beforeInvocation indica si invalida antes de la invocación
     * @param allEntries       indica si invalida todas las entradas
     * @param condition        condición
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
     * Crea una anotación agrupadora de invalidaciones simulada.
     *
     * @param value valor
     * @return anotación agrupadora de invalidaciones
     */
    private CoalesceEvicts createMockEvicts(CoalesceEvict[] value) {
        CoalesceEvicts evicts = mock(CoalesceEvicts.class);
        when(evicts.value()).thenReturn(value);
        return evicts;
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
