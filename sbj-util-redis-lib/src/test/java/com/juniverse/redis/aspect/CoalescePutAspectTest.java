package com.juniverse.redis.aspect;

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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

/**
 * Pruebas unitarias de CoalescePutAspect.
 *
 * @author ajuar
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class CoalescePutAspectTest {

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
    private CoalescePutAspect aspect;

    /**
     * Prepara el entorno antes de cada prueba.
     */
    @BeforeEach
    void setUp() {
        aspect = new CoalescePutAspect(cacheManager);
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
     * Un guardado con condición falsa no debe guardar en caché.
     */
    @Test
    void handleSinglePut_WithConditionFalse_ShouldNotCache() throws Throwable {
        CoalescePut put = createMockPut("cache", "#id", 300, "#id == '999'", "");
        setupJoinPoint();
        when(joinPoint.proceed()).thenReturn("result");

        Object result = aspect.handleSinglePut(joinPoint, put);

        assertEquals("result", result);
        verify(cacheManager, never()).put(anyString(), any(), anyLong());
    }


    /**
     * Un guardado con «unless» verdadero no debe guardar en caché.
     */
    @Test
    void handleSinglePut_WithUnlessTrue_ShouldNotCache() throws Throwable {
        CoalescePut put = createMockPut("cache", "#id", 300, "", "#result == 'result'");
        setupJoinPoint();
        when(joinPoint.proceed()).thenReturn("result");

        Object result = aspect.handleSinglePut(joinPoint, put);

        assertEquals("result", result);
        verify(cacheManager, never()).put(anyString(), any(), anyLong());
    }

    /**
     * Un guardado con «unless» falso debe guardar en caché.
     */
    @Test
    void handleSinglePut_WithUnlessFalse_ShouldCache() throws Throwable {
        CoalescePut put = createMockPut("cache", "#id", 300, "", "#result == 'other'");
        setupJoinPoint();
        when(joinPoint.proceed()).thenReturn("result");

        Object result = aspect.handleSinglePut(joinPoint, put);

        assertEquals("result", result);
        verify(cacheManager).put("cache:123", "result", 300);
    }


    /**
     * Un guardado con comprobación de nulo en «unless» no debe guardar nulos.
     */
    @Test
    void handleSinglePut_WithUnlessNullCheck_ShouldNotCacheNull() throws Throwable {
        CoalescePut put = createMockPut("cache", "#id", 300, "", "#result == null");
        setupJoinPoint();
        when(joinPoint.proceed()).thenReturn(null);

        Object result = aspect.handleSinglePut(joinPoint, put);

        assertEquals(null, result);
        verify(cacheManager, never()).put(anyString(), any(), anyLong());
    }

    /**
     * Un guardado con condición y «unless» verdaderos no debe guardar en caché.
     */
    @Test
    void handleSinglePut_WithConditionAndUnlessBothTrue_ShouldNotCache() throws Throwable {
        CoalescePut put = createMockPut(
                "cache", "#id", 300,
                "#id == '123'",
                "#result == 'result'"
        );
        setupJoinPoint();
        when(joinPoint.proceed()).thenReturn("result");

        Object result = aspect.handleSinglePut(joinPoint, put);

        assertEquals("result", result);
        verify(cacheManager, never()).put(anyString(), any(), anyLong());
    }


    /**
     * Prepara el punto de unión con valores predeterminados.
     */
    private void setupJoinPoint() {
        when(joinPoint.getSignature()).thenReturn(methodSignature);
        when(joinPoint.getArgs()).thenReturn(new Object[]{"123"});
        when(methodSignature.getParameterNames()).thenReturn(new String[]{"id"});
        when(methodSignature.getMethod()).thenReturn(getTestMethod());
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
