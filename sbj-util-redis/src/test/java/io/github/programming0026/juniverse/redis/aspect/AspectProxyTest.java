package io.github.programming0026.juniverse.redis.aspect;

import io.github.programming0026.juniverse.redis.annotation.CoalesceCacheable;
import io.github.programming0026.juniverse.redis.annotation.CoalesceCaching;
import io.github.programming0026.juniverse.redis.annotation.CoalesceEvict;
import io.github.programming0026.juniverse.redis.annotation.CoalescePut;
import io.github.programming0026.juniverse.redis.dto.CacheResult;
import io.github.programming0026.juniverse.redis.service.CoalesceCacheManager;
import java.io.IOException;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.util.List;
import java.util.Map;
import java.util.function.Supplier;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.aop.aspectj.annotation.AspectJProxyFactory;
import org.springframework.mock.env.MockEnvironment;


import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Pruebas de los aspectos aplicados a través de un proxy real.
 * Comprueban que los pointcuts enlazan la anotación con el parámetro del aviso,
 * algo que las pruebas que llaman directamente al aspecto no detectan.
 *
 * @author Java Universe
 */
class AspectProxyTest {

    /** Administrador de caché simulado. */
    private CoalesceCacheManager cacheManager;

    /** Servicio interceptado por los aspectos. */
    private SampleService proxy;

    /**
     * Servicio de ejemplo con las anotaciones de la librería.
     */
    public static class SampleService {

        @CoalesceCacheable(value = "${cache.prefix}", key = "#id", ttl = 60, coalesce = false)
        public String find(String id) {
            return "value-" + id;
        }

        @CoalesceCaching(put = @CoalescePut(value = "${cache.prefix}", key = "#id", ttl = 30))
        public String update(String id) {
            return "updated-" + id;
        }

        @CoalescePut(value = "${cache.prefix}", key = "#id", ttl = 45)
        public String save(String id) {
            return "saved-" + id;
        }

        @CoalesceEvict(value = "${cache.prefix}", key = "#id")
        public void remove(String id) {
            // Solo se intercepta.
        }

        @CoalesceCacheable(value = "${cache.prefix}", key = "#id", ttl = 60, coalesce = false)
        @CoalesceCacheable(value = "byName", key = "#id", ttl = 90, coalesce = false)
        public String findTwice(String id) {
            return "twice-" + id;
        }

        @CoalesceCacheable(value = "${cache.prefix}", key = "#id")
        public List<String> findCoalesced(String id) throws IOException {
            if ("io".equals(id)) {
                throw new IOException("checked failure");
            }
            if ("boom".equals(id)) {
                throw new IllegalStateException("unchecked failure");
            }
            return List.of(id);
        }

        @CoalesceCacheable(value = "a", key = "#id", condition = "#id == 'never'")
        @CoalesceCacheable(value = "b", key = "#id", condition = "#id == 'never'")
        public String findWhenNoConditionApplies(String id) {
            return "direct-" + id;
        }

        @CoalescePut(value = "${cache.prefix}", key = "#id")
        public String saveWithDefaultTtl(String id) {
            return "saved-" + id;
        }

        @CoalescePut(value = "${cache.prefix}", key = "#id")
        public String saveNothing(String id) {
            return null;
        }

        @CoalesceCaching(put = @CoalescePut(value = "${cache.prefix}", key = "#id"))
        public String updateNothing(String id) {
            return null;
        }

        @CoalesceCacheable(value = "${cache.prefix}", key = "#a0", ttl = 60, coalesce = false)
        public String findByPosition(String id) {
            return "pos-" + id;
        }

        @CoalesceEvict(value = {"${cache.prefix}", "byName"}, key = "#p0")
        public void removeEverywhere(String id) {
            // Solo se intercepta.
        }

        @CoalescePut(value = "${cache.prefix}", key = "#id", condition = "null")
        public String saveWithNullCondition(String id) {
            return "saved-" + id;
        }

        @CoalesceEvict(value = "${cache.prefix}", key = "#id", condition = "null")
        public void removeWithNullCondition(String id) {
            // Solo se intercepta.
        }
    }

    /**
     * Crea el proxy con los tres aspectos y un entorno con propiedades.
     */
    @BeforeEach
    public void setUp() {
        cacheManager = mock(CoalesceCacheManager.class);
        when(cacheManager.lookup(anyString(), any())).thenReturn(CacheResult.miss(null));
        when(cacheManager.getOrLoad(anyString(), any(), anyLong(), anyBoolean(), any()))
                .thenAnswer(invocation -> invocation.<Supplier<Object>>getArgument(1).get());
        proxy = proxyWith(Map.of());
    }

    /**
     * Crea el proxy con los tres aspectos.
     *
     * @param configuredTtlSeconds TTL en segundos configurado por nombre de caché
     * @return servicio interceptado
     */
    private SampleService proxyWith(Map<String, Long> configuredTtlSeconds) {
        MockEnvironment environment = new MockEnvironment().withProperty("cache.prefix", "users");

        AspectJProxyFactory factory = new AspectJProxyFactory(new SampleService());
        factory.setProxyTargetClass(true);
        factory.addAspect(new CoalesceCachingAspect(cacheManager, environment, configuredTtlSeconds));
        factory.addAspect(new CoalescePutAspect(cacheManager, environment, configuredTtlSeconds));
        factory.addAspect(new CoalesceEvictAspect(cacheManager, environment));
        return factory.getProxy();
    }

    /**
     * {@code @CoalesceCacheable} debe interceptarse y guardar con el prefijo resuelto.
     */
    @Test
    public void cacheable_ShouldBeIntercepted() {
        when(cacheManager.lookup(eq("users:1"), any())).thenReturn(CacheResult.miss(null));

        assertEquals("value-1", proxy.find("1"));

        verify(cacheManager).put(eq("users:1"), eq("value-1"), eq(60L), any());
    }

    /**
     * {@code @CoalesceCaching} debe interceptarse y aplicar sus guardados.
     */
    @Test
    public void caching_ShouldBeIntercepted() {
        assertEquals("updated-2", proxy.update("2"));

        verify(cacheManager).put(eq("users:2"), eq("updated-2"), eq(30L), any());
    }

    /**
     * {@code @CoalescePut} debe interceptarse y guardar el resultado.
     */
    @Test
    public void put_ShouldBeIntercepted() {
        assertEquals("saved-3", proxy.save("3"));

        verify(cacheManager).put(eq("users:3"), eq("saved-3"), eq(45L), any());
    }

    /**
     * {@code @CoalesceEvict} debe interceptarse e invalidar la clave.
     */
    @Test
    public void evict_ShouldBeIntercepted() {
        proxy.remove("4");

        verify(cacheManager).evict("users:4");
        verify(cacheManager, never()).put(anyString(), any(), anyLong(), any());
    }

    /**
     * Dos {@code @CoalesceCacheable} en el mismo método deben interceptarse:
     * sin valor en caché, el método se ejecuta y el resultado se guarda en las dos claves.
     */
    @Test
    public void repeatedCacheable_ShouldBeInterceptedAndCacheUnderEveryKey() {
        when(cacheManager.lookup(anyString(), any())).thenReturn(CacheResult.miss(null));

        assertEquals("twice-5", proxy.findTwice("5"));

        verify(cacheManager).put(eq("users:5"), eq("twice-5"), eq(60L), any());
        verify(cacheManager).put(eq("byName:5"), eq("twice-5"), eq(90L), any());
    }

    /**
     * Con dos {@code @CoalesceCacheable}, basta un acierto en cualquiera de las claves.
     */
    @Test
    public void repeatedCacheable_ShouldReturnHitFromAnyKey() {
        when(cacheManager.lookup(eq("users:6"), any())).thenReturn(CacheResult.miss(null));
        when(cacheManager.lookup(eq("byName:6"), any())).thenReturn(CacheResult.hit("in-cache"));

        assertEquals("in-cache", proxy.findTwice("6"));

        verify(cacheManager, never()).put(anyString(), any(), anyLong(), any());
    }

    /**
     * Una condición de {@code @CoalescePut} que da null cuenta como falsa: no se guarda y no falla.
     */
    @Test
    public void put_WithNullCondition_ShouldSkipCacheWithoutFailing() {
        assertEquals("saved-7", proxy.saveWithNullCondition("7"));

        verify(cacheManager, never()).put(anyString(), any(), anyLong(), any());
    }

    /**
     * Una condición de {@code @CoalesceEvict} que da null cuenta como falsa: no se invalida y no falla.
     */
    @Test
    public void evict_WithNullCondition_ShouldSkipEvictionWithoutFailing() {
        proxy.removeWithNullCondition("8");

        verify(cacheManager, never()).evict(anyString());
    }

    /**
     * Con coalescencia, el aspecto pasa al administrador el tipo de retorno genérico del método
     * y el TTL de reserva cuando la anotación no indica ninguno.
     *
     * @throws IOException nunca en esta prueba
     */
    @Test
    public void coalescedCacheable_ShouldPassReturnTypeAndFallbackTtl() throws IOException {
        assertEquals(List.of("9"), proxy.findCoalesced("9"));

        org.mockito.ArgumentCaptor<Type> type = org.mockito.ArgumentCaptor.forClass(Type.class);
        verify(cacheManager).getOrLoad(eq("users:9"), any(), eq(300L), eq(false), type.capture());
        ParameterizedType returnType = assertInstanceOf(ParameterizedType.class, type.getValue());
        assertEquals(List.class, returnType.getRawType());
        assertEquals(String.class, returnType.getActualTypeArguments()[0]);
    }

    /**
     * Con coalescencia no se lee la caché antes de {@code getOrLoad}, que ya la consulta:
     * así cada petición hace una sola lectura.
     *
     * @throws IOException nunca en esta prueba
     */
    @Test
    public void coalescedCacheable_ShouldNotReadCacheBeforeGetOrLoad() throws IOException {
        proxy.findCoalesced("10");

        verify(cacheManager, never()).lookup(anyString(), any());
        verify(cacheManager).getOrLoad(eq("users:10"), any(), anyLong(), anyBoolean(), any());
    }

    /**
     * Sin coalescencia, un acierto con valor nulo (nulo guardado) se devuelve sin ejecutar el método.
     */
    @Test
    public void cacheable_WithStoredNull_ShouldReturnNullWithoutLoading() {
        when(cacheManager.lookup(eq("users:10"), any())).thenReturn(CacheResult.hit(null));

        assertNull(proxy.find("10"));

        verify(cacheManager, never()).put(anyString(), any(), anyLong(), any());
    }

    /**
     * Las expresiones admiten los argumentos por posición, que no dependen de compilar con -parameters.
     */
    @Test
    public void expressions_ShouldSupportPositionalArguments() {
        assertEquals("pos-15", proxy.findByPosition("15"));

        verify(cacheManager).put(eq("users:15"), eq("pos-15"), eq(60L), any());
    }

    /**
     * Una invalidación con varios nombres de caché borra la clave en todos.
     */
    @Test
    public void evict_WithSeveralCacheNames_ShouldEvictTheKeyInEachOne() {
        proxy.removeEverywhere("16");

        verify(cacheManager).evict("users:16");
        verify(cacheManager).evict("byName:16");
    }

    /**
     * Las excepciones del método llegan a quien llama con su tipo, sean comprobadas o no.
     */
    @Test
    public void coalescedCacheable_ShouldPropagateOriginalExceptions() {
        IOException checked = assertThrows(IOException.class, () -> proxy.findCoalesced("io"));
        IllegalStateException unchecked =
                assertThrows(IllegalStateException.class, () -> proxy.findCoalesced("boom"));

        assertEquals("checked failure", checked.getMessage());
        assertEquals("unchecked failure", unchecked.getMessage());
    }

    /**
     * Sin {@code ttl} en la anotación se usa el configurado para esa caché.
     *
     * @throws IOException nunca en esta prueba
     */
    @Test
    public void annotationsWithoutTtl_ShouldUseTtlConfiguredForTheCache() throws IOException {
        SampleService configured = proxyWith(Map.of("users", 1800L));

        configured.findCoalesced("11");
        configured.saveWithDefaultTtl("11");
        configured.save("11");

        verify(cacheManager).getOrLoad(eq("users:11"), any(), eq(1800L), eq(false), any());
        verify(cacheManager).put(eq("users:11"), eq("saved-11"), eq(1800L), any());
        verify(cacheManager).put(eq("users:11"), eq("saved-11"), eq(45L), any());
    }

    /**
     * Si ninguna de las anotaciones repetidas cumple su condición, el método se ejecuta sin caché.
     */
    @Test
    public void repeatedCacheable_WhenNoConditionApplies_ShouldSkipCache() {
        assertEquals("direct-12", proxy.findWhenNoConditionApplies("12"));

        verify(cacheManager, never()).lookup(anyString(), any());
        verify(cacheManager, never()).put(anyString(), any(), anyLong(), any());
    }

    /**
     * Un {@code @CoalescePut} cuyo método devuelve nulo invalida la entrada en lugar de guardar un nulo.
     */
    @Test
    public void put_WithNullResult_ShouldEvictInsteadOfStoringNull() {
        assertNull(proxy.saveNothing("13"));
        assertNull(proxy.updateNothing("14"));

        verify(cacheManager).evict("users:13");
        verify(cacheManager).evict("users:14");
        verify(cacheManager, never()).put(anyString(), any(), anyLong(), any());
    }
}
