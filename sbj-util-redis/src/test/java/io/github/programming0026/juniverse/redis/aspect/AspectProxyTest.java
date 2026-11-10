package io.github.programming0026.juniverse.redis.aspect;

import io.github.programming0026.juniverse.redis.annotation.CoalesceCacheable;
import io.github.programming0026.juniverse.redis.annotation.CoalesceCaching;
import io.github.programming0026.juniverse.redis.annotation.CoalesceEvict;
import io.github.programming0026.juniverse.redis.annotation.CoalescePut;
import io.github.programming0026.juniverse.redis.service.CoalesceCacheManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.aop.aspectj.annotation.AspectJProxyFactory;
import org.springframework.mock.env.MockEnvironment;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
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
    }

    /**
     * Crea el proxy con los tres aspectos y un entorno con propiedades.
     */
    @BeforeEach
    void setUp() {
        cacheManager = mock(CoalesceCacheManager.class);
        MockEnvironment environment = new MockEnvironment().withProperty("cache.prefix", "users");

        AspectJProxyFactory factory = new AspectJProxyFactory(new SampleService());
        factory.setProxyTargetClass(true);
        factory.addAspect(new CoalesceCachingAspect(cacheManager, environment));
        factory.addAspect(new CoalescePutAspect(cacheManager, environment));
        factory.addAspect(new CoalesceEvictAspect(cacheManager, environment));
        proxy = factory.getProxy();
    }

    /**
     * {@code @CoalesceCacheable} debe interceptarse y guardar con el prefijo resuelto.
     */
    @Test
    void cacheable_ShouldBeIntercepted() {
        when(cacheManager.get("users:1")).thenReturn(Optional.empty());

        assertEquals("value-1", proxy.find("1"));

        verify(cacheManager).put("users:1", "value-1", 60L);
    }

    /**
     * {@code @CoalesceCaching} debe interceptarse y aplicar sus guardados.
     */
    @Test
    void caching_ShouldBeIntercepted() {
        assertEquals("updated-2", proxy.update("2"));

        verify(cacheManager).put("users:2", "updated-2", 30L);
    }

    /**
     * {@code @CoalescePut} debe interceptarse y guardar el resultado.
     */
    @Test
    void put_ShouldBeIntercepted() {
        assertEquals("saved-3", proxy.save("3"));

        verify(cacheManager).put("users:3", "saved-3", 45L);
    }

    /**
     * {@code @CoalesceEvict} debe interceptarse e invalidar la clave.
     */
    @Test
    void evict_ShouldBeIntercepted() {
        proxy.remove("4");

        verify(cacheManager).evict("users:4");
        verify(cacheManager, never()).put(anyString(), any(), anyLong());
    }
}
