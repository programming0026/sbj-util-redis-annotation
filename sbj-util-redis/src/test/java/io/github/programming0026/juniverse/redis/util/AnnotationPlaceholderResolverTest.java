package io.github.programming0026.juniverse.redis.util;

import io.github.programming0026.juniverse.redis.annotation.CoalesceCacheable;
import io.github.programming0026.juniverse.redis.annotation.CoalesceCaching;
import io.github.programming0026.juniverse.redis.annotation.CoalesceEvict;
import org.junit.jupiter.api.Test;
import org.springframework.core.annotation.AnnotationAttributes;
import org.springframework.mock.env.MockEnvironment;

import java.util.function.UnaryOperator;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Pruebas unitarias de AnnotationPlaceholderResolver.
 *
 * @author Java Universe
 */
class AnnotationPlaceholderResolverTest {

    /** Entorno con las propiedades de prueba. */
    private final MockEnvironment environment = new MockEnvironment()
            .withProperty("cache.users", "users-v2")
            .withProperty("cache.ttl.name", "products")
            .withProperty("redis.host", "redis.internal");

    /** Resolvedor basado en el entorno. */
    private final UnaryOperator<String> resolver = environment::resolveRequiredPlaceholders;

    /**
     * Métodos anotados que sirven de fuente de anotaciones.
     */
    @SuppressWarnings("unused")
    static class Annotated {

        @CoalesceCacheable(value = "${cache.users}", key = "#id", ttl = 60, coalesce = false)
        void cacheable() {
        }

        @CoalesceCacheable(value = "${cache.missing:fallback}")
        void cacheableWithDefault() {
        }

        @CoalesceCacheable(value = "${cache.missing}")
        void cacheableMissing() {
        }

        @CoalesceEvict(value = {"${cache.users}", "static"}, allEntries = true)
        void evict() {
        }

        @CoalesceCaching(cacheable = @CoalesceCacheable("${cache.users}"),
                evict = @CoalesceEvict("${cache.ttl.name}"))
        void caching() {
        }
    }

    /**
     * Obtiene una anotación de un método de {@link Annotated}.
     */
    private static <A extends java.lang.annotation.Annotation> A annotation(String method, Class<A> type)
            throws NoSuchMethodException {
        return Annotated.class.getDeclaredMethod(method).getAnnotation(type);
    }

    /**
     * Los atributos de texto deben resolverse y el resto conservarse.
     */
    @Test
    void resolve_ShouldReplaceStringAttributesAndKeepOthers() throws Exception {
        CoalesceCacheable resolved = AnnotationPlaceholderResolver.resolve(
                annotation("cacheable", CoalesceCacheable.class), resolver);

        assertEquals("users-v2", resolved.value());
        assertEquals("#id", resolved.key());
        assertEquals(60, resolved.ttl());
        assertFalse(resolved.coalesce());
    }

    /**
     * Debe aplicarse el valor por defecto del placeholder.
     */
    @Test
    void resolve_WithDefaultValue_ShouldUseDefault() throws Exception {
        CoalesceCacheable resolved = AnnotationPlaceholderResolver.resolve(
                annotation("cacheableWithDefault", CoalesceCacheable.class), resolver);

        assertEquals("fallback", resolved.value());
    }

    /**
     * Un placeholder sin propiedad debe fallar con el resolvedor estricto.
     */
    @Test
    void resolve_WithMissingProperty_ShouldThrow() throws Exception {
        CoalesceCacheable annotation = annotation("cacheableMissing", CoalesceCacheable.class);

        assertThrows(IllegalArgumentException.class,
                () -> AnnotationPlaceholderResolver.resolve(annotation, resolver));
    }

    /**
     * Los arreglos de texto deben resolverse elemento por elemento.
     */
    @Test
    void resolve_ShouldReplaceStringArrays() throws Exception {
        CoalesceEvict resolved = AnnotationPlaceholderResolver.resolve(
                annotation("evict", CoalesceEvict.class), resolver);

        assertArrayEquals(new String[]{"users-v2", "static"}, resolved.value());
        assertTrue(resolved.allEntries());
    }

    /**
     * Las anotaciones anidadas deben resolverse.
     */
    @Test
    void resolve_ShouldReplaceNestedAnnotations() throws Exception {
        CoalesceCaching resolved = AnnotationPlaceholderResolver.resolve(
                annotation("caching", CoalesceCaching.class), resolver);

        assertEquals("users-v2", resolved.cacheable()[0].value());
        assertEquals("products", resolved.evict()[0].value()[0]);
    }

    /**
     * Los mapas de atributos deben resolverse, incluidos los anidados.
     */
    @Test
    void resolve_WithAnnotationAttributes_ShouldReplaceNestedValues() {
        AnnotationAttributes host = new AnnotationAttributes();
        host.put("host", "${redis.host}");
        host.put("port", 6379);
        AnnotationAttributes attributes = new AnnotationAttributes();
        attributes.put("pwd", "${redis.pwd:secret}");
        attributes.put("database", 2);
        attributes.put("hostEntries", new AnnotationAttributes[]{host, null});
        attributes.put("sentinelMaster", null);

        AnnotationAttributes resolved = AnnotationPlaceholderResolver.resolve(attributes, resolver);

        assertEquals("secret", resolved.getString("pwd"));
        assertEquals(2, resolved.<Integer>getNumber("database"));
        AnnotationAttributes[] hosts = (AnnotationAttributes[]) resolved.get("hostEntries");
        assertEquals("redis.internal", hosts[0].getString("host"));
        assertEquals(6379, hosts[0].<Integer>getNumber("port"));
        assertNull(hosts[1]);
        assertNull(resolved.get("sentinelMaster"));
        assertEquals("${redis.host}", host.getString("host"), "the original must not change");
    }
}
