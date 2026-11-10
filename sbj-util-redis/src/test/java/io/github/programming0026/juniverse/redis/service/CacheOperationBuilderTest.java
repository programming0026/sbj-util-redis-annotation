package io.github.programming0026.juniverse.redis.service;

import io.github.programming0026.juniverse.redis.dto.CacheResult;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Duration;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;
import java.util.function.Supplier;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Pruebas unitarias de CacheOperationBuilder.
 *
 * @author Java Universe
 */
@ExtendWith(MockitoExtension.class)
class CacheOperationBuilderTest {

        /** Servicio de caché. */
        @Mock
        private RedisCacheService cacheService;

        /** Constructor. */
        private CacheOperationBuilder<String> builder;

        /**
         * Prepara la prueba.
         */
        @BeforeEach
        void setUp() {
                CacheOperationBuilder.Factory factory = new CacheOperationBuilder.Factory(cacheService);
                builder = factory.create();
        }

        /**
         * Obtener de caché sin nombre de caché debe lanzar IllegalStateException.
         */
        @Test
        void cacheable_WithoutCacheName_ShouldThrowIllegalStateException() {
                builder.key("testKey")
                                .loader(() -> "value");

                IllegalStateException exception = assertThrows(IllegalStateException.class, () -> builder.cacheable());
                assertEquals("cacheName is required", exception.getMessage());
        }

        /**
         * Obtener de caché sin clave debe lanzar IllegalStateException.
         */
        @Test
        void cacheable_WithoutKey_ShouldThrowIllegalStateException() {
                builder.cacheName("testCache")
                                .loader(() -> "value");

                IllegalStateException exception = assertThrows(IllegalStateException.class, () -> builder.cacheable());
                assertEquals("key is required", exception.getMessage());
        }

        /**
         * Obtener de caché sin cargador debe lanzar IllegalStateException.
         */
        @Test
        void cacheable_WithoutLoader_ShouldThrowIllegalStateException() {
                builder.cacheName("testCache")
                                .key("testKey");

                IllegalStateException exception = assertThrows(IllegalStateException.class, () -> builder.cacheable());
                assertEquals("loader is required", exception.getMessage());
        }

        /**
         * Obtener de caché con nombre de caché vacío debe lanzar IllegalStateException.
         */
        @Test
        void cacheable_WithEmptyCacheName_ShouldThrowIllegalStateException() {
                builder.cacheName("")
                                .key("testKey")
                                .loader(() -> "value");

                IllegalStateException exception = assertThrows(IllegalStateException.class, () -> builder.cacheable());
                assertEquals("cacheName is required", exception.getMessage());
        }

        /**
         * Obtener de caché con clave vacía debe lanzar IllegalStateException.
         */
        @Test
        void cacheable_WithEmptyKey_ShouldThrowIllegalStateException() {
                builder.cacheName("testCache")
                                .key("")
                                .loader(() -> "value");

                IllegalStateException exception = assertThrows(IllegalStateException.class, () -> builder.cacheable());
                assertEquals("key is required", exception.getMessage());
        }

        /**
         * Obtener de caché con todos los campos obligatorios debe ejecutarse.
         */
        @Test
        void cacheable_WithAllRequiredFields_ShouldExecute() {
                // Dado
                String cacheName = "testCache";
                String key = "testKey";
                String expectedValue = "testValue";
                Supplier<String> loader = () -> expectedValue;

                when(cacheService.cacheableWithResult(eq(cacheName), eq(key), any(), any(Duration.class)))
                                .thenReturn(CacheResult.miss(expectedValue));

                String result = builder.cacheName(cacheName)
                                .key(key)
                                .loader(loader)
                                .cacheable();

                assertEquals(expectedValue, result);
                verify(cacheService).cacheableWithResult(eq(cacheName), eq(key), any(),
                                eq(Duration.ofMinutes(10)));
        }

        /**
         * Obtener de caché con TTL propio debe usar ese TTL.
         */
        @Test
        void cacheable_WithCustomTtl_ShouldUseCustomTtl() {
                String cacheName = "testCache";
                String key = "testKey";
                String expectedValue = "testValue";
                Duration customTtl = Duration.ofHours(2);

                when(cacheService.cacheableWithResult(eq(cacheName), eq(key), any(), eq(customTtl)))
                                .thenReturn(CacheResult.miss(expectedValue));

                String result = builder.cacheName(cacheName)
                                .key(key)
                                .loader(() -> expectedValue)
                                .ttl(customTtl)
                                .cacheable();

                assertEquals(expectedValue, result);
                verify(cacheService).cacheableWithResult(eq(cacheName), eq(key), any(), eq(customTtl));
        }

        /**
         * El constructor debe admitir la API fluida.
         */
        @Test
        void builder_ShouldSupportFluentAPI() {
                String expectedValue = "testValue";
                when(cacheService.cacheableWithResult(anyString(), anyString(), any(), any(Duration.class)))
                                .thenReturn(CacheResult.miss(expectedValue));

                String result = builder
                                .cacheName("cache")
                                .key("key")
                                .loader(() -> expectedValue)
                                .ttl(Duration.ofMinutes(5))
                                .condition(true)
                                .cacheable();

                assertEquals(expectedValue, result);
        }


        /**
         * Obtener de caché con condición falsa debe omitir la caché.
         */
        @Test
        void cacheable_WithConditionFalse_ShouldBypassCache() {
                String expectedValue = "testValue";
                Supplier<String> loader = () -> expectedValue;

                String result = builder.cacheName("testCache")
                                .key("testKey")
                                .loader(loader)
                                .condition(false)
                                .cacheable();

                assertEquals(expectedValue, result);
                verify(cacheService, never()).cacheable(anyString(), anyString(), any(), any(Duration.class));
        }

        /**
         * Obtener de caché con condición verdadera debe usar la caché.
         */
        @Test
        void cacheable_WithConditionTrue_ShouldUseCache() {
                String expectedValue = "testValue";
                when(cacheService.cacheableWithResult(anyString(), anyString(), any(), any(Duration.class)))
                                .thenReturn(CacheResult.miss(expectedValue));

                String result = builder.cacheName("testCache")
                                .key("testKey")
                                .loader(() -> expectedValue)
                                .condition(true)
                                .cacheable();

                assertEquals(expectedValue, result);
                verify(cacheService).cacheableWithResult(anyString(), anyString(), any(), any(Duration.class));
        }



        /**
         * Obtener de caché ante un acierto debe invocar la retrollamada onHit.
         */
        @Test
        void cacheable_OnCacheHit_ShouldInvokeOnHitCallback() {
                String cacheName = "testCache";
                String key = "testKey";
                String cachedValue = "cachedValue";
                AtomicBoolean hitCallbackInvoked = new AtomicBoolean(false);
                Consumer<String> onHitCallback = value -> {
                        hitCallbackInvoked.set(true);
                        assertEquals(cachedValue, value);
                };

                when(cacheService.cacheableWithResult(eq(cacheName), eq(key), any(), any(Duration.class)))
                                .thenReturn(CacheResult.hit(cachedValue));

                String result = builder.cacheName(cacheName)
                                .key(key)
                                .loader(() -> "newValue")
                                .onCacheHit(onHitCallback)
                                .cacheable();

                assertEquals(cachedValue, result);
                assertTrue(hitCallbackInvoked.get(), "onHit callback should have been invoked");
        }

        /**
         * Obtener de caché ante un fallo debe invocar la retrollamada onMiss.
         */
        @Test
        void cacheable_OnCacheMiss_ShouldInvokeOnMissCallback() {
                String cacheName = "testCache";
                String key = "testKey";
                String loadedValue = "loadedValue";
                AtomicBoolean missCallbackInvoked = new AtomicBoolean(false);
                Consumer<String> onMissCallback = value -> {
                        missCallbackInvoked.set(true);
                        assertEquals(loadedValue, value);
                };

                when(cacheService.cacheableWithResult(eq(cacheName), eq(key), any(), any(Duration.class)))
                                .thenReturn(CacheResult.miss(loadedValue));

                String result = builder.cacheName(cacheName)
                                .key(key)
                                .loader(() -> loadedValue)
                                .onCacheMiss(onMissCallback)
                                .cacheable();

                assertEquals(loadedValue, result);
                assertTrue(missCallbackInvoked.get(), "onMiss callback should have been invoked");
        }

        /**
         * Obtener de caché con ambas retrollamadas debe invocar solo la que corresponde.
         */
        @Test
        void cacheable_WithBothCallbacks_ShouldInvokeOnlyRelevantCallback() {
                String cacheName = "testCache";
                String key = "testKey";
                String cachedValue = "cachedValue";
                AtomicBoolean hitCallbackInvoked = new AtomicBoolean(false);
                AtomicBoolean missCallbackInvoked = new AtomicBoolean(false);

                when(cacheService.cacheableWithResult(eq(cacheName), eq(key), any(), any(Duration.class)))
                                .thenReturn(CacheResult.hit(cachedValue));

                String result = builder.cacheName(cacheName)
                                .key(key)
                                .loader(() -> "newValue")
                                .onCacheHit(value -> hitCallbackInvoked.set(true))
                                .onCacheMiss(value -> missCallbackInvoked.set(true))
                                .cacheable();

                assertEquals(cachedValue, result);
                assertTrue(hitCallbackInvoked.get(), "onHit callback should have been invoked");
                assertFalse(missCallbackInvoked.get(), "onMiss callback should NOT have been invoked");
        }

        /**
         * Obtener de caché sin retrollamadas no debe lanzar excepción.
         */
        @Test
        void cacheable_WithoutCallbacks_ShouldNotThrowException() {
                String expectedValue = "testValue";
                when(cacheService.cacheableWithResult(anyString(), anyString(), any(), any(Duration.class)))
                                .thenReturn(CacheResult.miss(expectedValue));

                assertDoesNotThrow(() -> builder.cacheName("testCache")
                                .key("testKey")
                                .loader(() -> expectedValue)
                                .cacheable());
        }


        /**
         * Guardar en caché debe delegar en el servicio.
         */
        @Test
        void cachePut_ShouldDelegateToService() {
                
                String cacheName = "testCache";
                String key = "testKey";
                String expectedValue = "testValue";
                Duration ttl = Duration.ofMinutes(10);

                when(cacheService.cachePut(eq(cacheName), eq(key), any(), eq(ttl)))
                                .thenReturn(expectedValue);

                
                String result = builder.cacheName(cacheName)
                                .key(key)
                                .loader(() -> expectedValue)
                                .ttl(ttl)
                                .cachePut();

                
                assertEquals(expectedValue, result);
                verify(cacheService).cachePut(eq(cacheName), eq(key), any(), eq(ttl));
        }

        /**
         * Guardar en caché con TTL predeterminado debe usar ese valor.
         */
        @Test
        void cachePut_WithDefaultTtl_ShouldUseDefault() {
                
                String expectedValue = "testValue";
                when(cacheService.cachePut(anyString(), anyString(), any(), any(Duration.class)))
                                .thenReturn(expectedValue);

                
                String result = builder.cacheName("testCache")
                                .key("testKey")
                                .loader(() -> expectedValue)
                                .cachePut();

                
                assertEquals(expectedValue, result);
                verify(cacheService).cachePut(anyString(), anyString(), any(), eq(Duration.ofMinutes(10)));
        }


        /**
         * Invalidar la caché debe delegar en el servicio.
         */
        @Test
        void cacheEvict_ShouldDelegateToService() {
                String cacheName = "testCache";
                String key = "testKey";

                builder.cacheName(cacheName)
                                .key(key)
                                .cacheEvict();

                verify(cacheService).cacheEvict(cacheName, key);
        }

        /**
         * Invalidar la caché con un cargador asignado debe seguir funcionando.
         */
        @Test
        void cacheEvict_WithLoaderSet_ShouldStillWork() {
                String cacheName = "testCache";
                String key = "testKey";

                builder.cacheName(cacheName)
                                .key(key)
                                .loader(() -> "value") // Loader not needed for evict
                                .cacheEvict();

                verify(cacheService).cacheEvict(cacheName, key);
        }

        /**
         * El constructor debe tener un TTL predeterminado de 10 minutos.
         */
        @Test
        void builder_ShouldHaveDefaultTtlOf10Minutes() {

                String expectedValue = "testValue";
                when(cacheService.cacheableWithResult(anyString(), anyString(), any(), any(Duration.class)))
                                .thenReturn(CacheResult.miss(expectedValue));

                builder.cacheName("testCache")
                                .key("testKey")
                                .loader(() -> expectedValue)
                                .cacheable();

                verify(cacheService).cacheableWithResult(anyString(), anyString(), any(),
                                eq(Duration.ofMinutes(10)));
        }

        /**
         * El constructor debe tener la condición verdadera por defecto.
         */
        @Test
        void builder_ShouldHaveDefaultConditionTrue() {
                String expectedValue = "testValue";
                when(cacheService.cacheableWithResult(anyString(), anyString(), any(), any(Duration.class)))
                                .thenReturn(CacheResult.miss(expectedValue));

                builder.cacheName("testCache")
                                .key("testKey")
                                .loader(() -> expectedValue)
                                .cacheable();

                verify(cacheService).cacheableWithResult(anyString(), anyString(), any(), any(Duration.class));
        }

        /**
         * Invalidar sin nombre de caché debe lanzar una excepción.
         */
        @Test
        void cacheEvict_WithoutCacheName_ShouldThrowException() {
                IllegalStateException exception = assertThrows(IllegalStateException.class,
                        () -> builder.key("key1").cacheEvict());

                assertEquals("cacheName is required", exception.getMessage());
                verify(cacheService, never()).cacheEvict(anyString(), anyString());
        }

        /**
         * Invalidar sin clave debe lanzar una excepción.
         */
        @Test
        void cacheEvict_WithoutKey_ShouldThrowException() {
                IllegalStateException exception = assertThrows(IllegalStateException.class,
                        () -> builder.cacheName("cache").cacheEvict());

                assertEquals("key is required", exception.getMessage());
                verify(cacheService, never()).cacheEvict(anyString(), anyString());
        }

        /**
         * Invalidar con parámetros válidos debe tener éxito.
         */
        @Test
        void cacheEvict_WithValidParams_ShouldSucceed() {
                assertDoesNotThrow(() -> builder.cacheName("cache").key("key1").cacheEvict());

                verify(cacheService).cacheEvict("cache", "key1");
        }

        /**
         * Invalidar todo debe delegar en el servicio.
         */
        @Test
        void cacheEvictAll_ShouldDelegateToService() {
                String cacheName = "testCache";

                builder.cacheName(cacheName)
                                .cacheEvictAll();

                verify(cacheService).cacheEvictAll(cacheName);
        }

        /**
         * Invalidar todo sin nombre de caché debe lanzar una excepción.
         */
        @Test
        void cacheEvictAll_WithoutCacheName_ShouldThrowException() {
                IllegalStateException exception = assertThrows(IllegalStateException.class,
                        () -> builder.cacheEvictAll());

                assertEquals("cacheName is required", exception.getMessage());
                verify(cacheService, never()).cacheEvictAll(anyString());
        }

        /**
         * Invalidar todo con parámetros válidos debe tener éxito.
         */
        @Test
        void cacheEvictAll_WithValidParams_ShouldSucceed() {
                assertDoesNotThrow(() -> builder.cacheName("cache").cacheEvictAll());

                verify(cacheService).cacheEvictAll("cache");
        }

        /**
         * Invalidar todo con una clave asignada debe ignorarla.
         */
        @Test
        void cacheEvictAll_WithKeySet_ShouldIgnoreKey() {
                String cacheName = "testCache";

                builder.cacheName(cacheName)
                                .key("ignoredKey") // Key is not needed for evictAll
                                .cacheEvictAll();

                verify(cacheService).cacheEvictAll(cacheName);
        }
}
