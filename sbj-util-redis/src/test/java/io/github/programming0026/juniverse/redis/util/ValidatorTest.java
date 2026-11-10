package io.github.programming0026.juniverse.redis.util;

import io.github.programming0026.juniverse.redis.dto.HostsDto;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.function.Supplier;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Pruebas unitarias de Validator.
 *
 * @author Java Universe
 */
class ValidatorTest {

    /**
     * Validar servidores con lista nula debe lanzar una excepción.
     */
    @Test
    void validateHosts_WithNullList_ShouldThrowException() {
        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class,
                () -> Validator.validateHosts(null));
        assertEquals("At least one host must be configured", exception.getMessage());
    }

    /**
     * Validar servidores con lista vacía debe lanzar una excepción.
     */
    @Test
    void validateHosts_WithEmptyList_ShouldThrowException() {
        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class,
                () -> Validator.validateHosts(Collections.emptyList()));
        assertEquals("At least one host must be configured", exception.getMessage());
    }

    /**
     * Validar servidores con nombre de host nulo debe lanzar una excepción.
     */
    @Test
    void validateHosts_WithNullHostname_ShouldThrowException() {
        List<HostsDto> hosts = Arrays.asList(
                HostsDto.builder().hostName(null).port(6379).build()
        );

        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class,
                () -> Validator.validateHosts(hosts));
        assertEquals("Host name cannot be empty", exception.getMessage());
    }

    /**
     * Validar servidores con nombre de host vacío debe lanzar una excepción.
     */
    @Test
    void validateHosts_WithEmptyHostname_ShouldThrowException() {
        List<HostsDto> hosts = Arrays.asList(
                HostsDto.builder().hostName("").port(6379).build()
        );

        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class,
                () -> Validator.validateHosts(hosts));
        assertEquals("Host name cannot be empty", exception.getMessage());
    }

    /**
     * Validar servidores con nombre de host en blanco debe lanzar una excepción.
     */
    @Test
    void validateHosts_WithWhitespaceHostname_ShouldThrowException() {
        List<HostsDto> hosts = Arrays.asList(
                HostsDto.builder().hostName("   ").port(6379).build()
        );

        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class,
                () -> Validator.validateHosts(hosts));
        assertEquals("Host name cannot be empty", exception.getMessage());
    }

    /**
     * Validar servidores con puerto por debajo del rango debe lanzar una excepción.
     */
    @Test
    void validateHosts_WithPortBelowRange_ShouldThrowException() {
        List<HostsDto> hosts = Arrays.asList(
                HostsDto.builder().hostName("localhost").port(0).build()
        );

        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class,
                () -> Validator.validateHosts(hosts));
        assertEquals("Invalid port 0. Port must be between 1 and 65535", exception.getMessage());
    }

    /**
     * Validar servidores con puerto por encima del rango debe lanzar una excepción.
     */
    @Test
    void validateHosts_WithPortAboveRange_ShouldThrowException() {
        List<HostsDto> hosts = Arrays.asList(
                HostsDto.builder().hostName("localhost").port(65536).build()
        );

        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class,
                () -> Validator.validateHosts(hosts));
        assertEquals("Invalid port 65536. Port must be between 1 and 65535", exception.getMessage());
    }

    /**
     * Validar servidores con un host válido no debe lanzar excepción.
     */
    @Test
    void validateHosts_WithValidHost_ShouldNotThrowException() {
        List<HostsDto> hosts = Arrays.asList(
                HostsDto.builder().hostName("localhost").port(6379).build()
        );

        assertDoesNotThrow(() -> Validator.validateHosts(hosts));
    }

    /**
     * Validar tiempos con espera de conexión nula debe lanzar una excepción.
     */
    @Test
    void validateTimeout_WithNullConnectionTimeout_ShouldThrowException() {
        NullPointerException exception = assertThrows(NullPointerException.class,
                () -> Validator.validateTimeout(null, 5000L));
        assertEquals("Connection timeout cannot be null", exception.getMessage());
    }

    /**
     * Validar tiempos con espera de lectura nula debe lanzar una excepción.
     */
    @Test
    void validateTimeout_WithNullReadTimeout_ShouldThrowException() {
        NullPointerException exception = assertThrows(NullPointerException.class,
                () -> Validator.validateTimeout(5000L, null));
        assertEquals("Read timeout cannot be null", exception.getMessage());
    }

    /**
     * Validar tiempos con espera de conexión negativa debe lanzar una excepción.
     */
    @Test
    void validateTimeout_WithNegativeConnectionTimeout_ShouldThrowException() {
        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class,
                () -> Validator.validateTimeout(-1L, 5000L));
        assertEquals("Invalid connectionTimeout -1.", exception.getMessage());
    }

    /**
     * Validar tiempos con espera de conexión cero debe lanzar una excepción.
     */
    @Test
    void validateTimeout_WithZeroConnectionTimeout_ShouldThrowException() {
        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class,
                () -> Validator.validateTimeout(0L, 5000L));
        assertEquals("Invalid connectionTimeout 0.", exception.getMessage());
    }

    /**
     * Validar tiempos con espera de lectura negativa debe lanzar una excepción.
     */
    @Test
    void validateTimeout_WithNegativeReadTimeout_ShouldThrowException() {
        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class,
                () -> Validator.validateTimeout(5000L, -1L));
        assertEquals("Invalid readTimeout -1.", exception.getMessage());
    }

    /**
     * Validar tiempos con espera de lectura cero debe lanzar una excepción.
     */
    @Test
    void validateTimeout_WithZeroReadTimeout_ShouldThrowException() {
        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class,
                () -> Validator.validateTimeout(5000L, 0L));
        assertEquals("Invalid readTimeout 0.", exception.getMessage());
    }

    /**
     * Validar tiempos válidos no debe lanzar excepción.
     */
    @Test
    void validateTimeout_WithValidTimeouts_ShouldNotThrowException() {
        assertDoesNotThrow(() -> Validator.validateTimeout(5000L, 3000L));
    }

    /**
     * Validar el modo standalone con varios servidores debe lanzar una excepción.
     */
    @Test
    void validateStandaloneHosts_WithMultipleHosts_ShouldThrowException() {
        List<HostsDto> hosts = Arrays.asList(
                HostsDto.builder().hostName("localhost").port(6379).build(),
                HostsDto.builder().hostName("localhost").port(6380).build()
        );

        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class,
                () -> Validator.validateStandaloneHosts(hosts));
        assertEquals("Standalone mode requires exactly one host entry", exception.getMessage());
    }

    /**
     * Validar el modo standalone con un servidor no debe lanzar excepción.
     */
    @Test
    void validateStandaloneHosts_WithSingleHost_ShouldNotThrowException() {
        List<HostsDto> hosts = Arrays.asList(
                HostsDto.builder().hostName("localhost").port(6379).build()
        );

        assertDoesNotThrow(() -> Validator.validateStandaloneHosts(hosts));
    }

    /**
     * Validar el modo clúster con un solo servidor debe lanzar una excepción.
     */
    @Test
    void validateClusterHosts_WithSingleHost_ShouldThrowException() {
        List<HostsDto> hosts = Arrays.asList(
                HostsDto.builder().hostName("localhost").port(6379).build()
        );

        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class,
                () -> Validator.validateClusterHosts(hosts));
        assertEquals("Cluster mode requires host entries for proper redundancy",
                exception.getMessage());
    }

    /**
     * Validar el modo clúster con varios servidores no debe lanzar excepción.
     */
    @Test
    void validateClusterHosts_WithMultipleHosts_ShouldNotThrowException() {
        List<HostsDto> hosts = Arrays.asList(
                HostsDto.builder().hostName("localhost").port(6379).build(),
                HostsDto.builder().hostName("localhost").port(6380).build()
        );

        assertDoesNotThrow(() -> Validator.validateClusterHosts(hosts));
    }

    /**
     * Validar sentinel con maestro nulo debe lanzar una excepción.
     */
    @Test
    void validateSentinelHosts_WithNullSentinelMaster_ShouldThrowException() {
        List<HostsDto> hosts = Arrays.asList(
                HostsDto.builder().hostName("localhost").port(26379).build(),
                HostsDto.builder().hostName("localhost").port(26380).build()
        );

        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class,
                () -> Validator.validateSentinelHosts(hosts, null));
        assertEquals("sentinelMaster must be configured when using SENTINEL mode",
                exception.getMessage());
    }

    /**
     * Validar sentinel con maestro vacío debe lanzar una excepción.
     */
    @Test
    void validateSentinelHosts_WithEmptySentinelMaster_ShouldThrowException() {
        List<HostsDto> hosts = Arrays.asList(
                HostsDto.builder().hostName("localhost").port(26379).build(),
                HostsDto.builder().hostName("localhost").port(26380).build()
        );

        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class,
                () -> Validator.validateSentinelHosts(hosts, ""));
        assertEquals("sentinelMaster must be configured when using SENTINEL mode",
                exception.getMessage());
    }

    /**
     * Validar sentinel con un solo servidor debe lanzar una excepción.
     */
    @Test
    void validateSentinelHosts_WithSingleHost_ShouldThrowException() {
        List<HostsDto> hosts = Arrays.asList(
                HostsDto.builder().hostName("localhost").port(26379).build()
        );

        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class,
                () -> Validator.validateSentinelHosts(hosts, "mymaster"));
        assertEquals("Sentinel mode requires host entries for proper redundancy",
                exception.getMessage());
    }

    /**
     * Validar sentinel con una configuración válida no debe lanzar excepción.
     */
    @Test
    void validateSentinelHosts_WithValidConfig_ShouldNotThrowException() {
        List<HostsDto> hosts = Arrays.asList(
                HostsDto.builder().hostName("localhost").port(26379).build(),
                HostsDto.builder().hostName("localhost").port(26380).build()
        );

        assertDoesNotThrow(() -> Validator.validateSentinelHosts(hosts, "mymaster"));
    }

    /**
     * Validar campos obligatorios con nombre de caché nulo debe lanzar una excepción.
     */
    @Test
    void validateRequiredFields_WithNullCacheName_ShouldThrowException() {
        Supplier<String> loader = () -> "value";

        IllegalStateException exception = assertThrows(IllegalStateException.class,
                () -> Validator.validateRequiredFields(null, "key", loader));
        assertEquals("cacheName is required", exception.getMessage());
    }

    /**
     * Validar campos obligatorios con nombre de caché vacío debe lanzar una excepción.
     */
    @Test
    void validateRequiredFields_WithEmptyCacheName_ShouldThrowException() {
        Supplier<String> loader = () -> "value";

        IllegalStateException exception = assertThrows(IllegalStateException.class,
                () -> Validator.validateRequiredFields("", "key", loader));
        assertEquals("cacheName is required", exception.getMessage());
    }

    /**
     * Validar campos obligatorios con clave nula debe lanzar una excepción.
     */
    @Test
    void validateRequiredFields_WithNullKey_ShouldThrowException() {
        Supplier<String> loader = () -> "value";

        IllegalStateException exception = assertThrows(IllegalStateException.class,
                () -> Validator.validateRequiredFields("cache", null, loader));
        assertEquals("key is required", exception.getMessage());
    }

    /**
     * Validar campos obligatorios con clave vacía debe lanzar una excepción.
     */
    @Test
    void validateRequiredFields_WithEmptyKey_ShouldThrowException() {
        Supplier<String> loader = () -> "value";

        IllegalStateException exception = assertThrows(IllegalStateException.class,
                () -> Validator.validateRequiredFields("cache", "", loader));
        assertEquals("key is required", exception.getMessage());
    }

    /**
     * Validar campos obligatorios con cargador nulo debe lanzar una excepción.
     */
    @Test
    void validateRequiredFields_WithNullLoader_ShouldThrowException() {
        IllegalStateException exception = assertThrows(IllegalStateException.class,
                () -> Validator.validateRequiredFields("cache", "key", null));
        assertEquals("loader is required", exception.getMessage());
    }

    /**
     * Validar campos obligatorios con parámetros válidos no debe lanzar excepción.
     */
    @Test
    void validateRequiredFields_WithValidParams_ShouldNotThrowException() {
        Supplier<String> loader = () -> "value";

        assertDoesNotThrow(() -> Validator.validateRequiredFields("cache", "key", loader));
    }

    /**
     * Validar TTL con nombre de caché nulo debe lanzar una excepción.
     */
    @Test
    void validateTTL_WithNullCacheName_ShouldThrowException() {
        IllegalStateException exception = assertThrows(IllegalStateException.class,
                () -> Validator.validateTTL(null, 60L));
        assertEquals("cacheName is required", exception.getMessage());
    }

    /**
     * Validar TTL con nombre de caché vacío debe lanzar una excepción.
     */
    @Test
    void validateTTL_WithEmptyCacheName_ShouldThrowException() {
        IllegalStateException exception = assertThrows(IllegalStateException.class,
                () -> Validator.validateTTL("", 60L));
        assertEquals("cacheName is required", exception.getMessage());
    }

    /**
     * Validar TTL con TTL nulo debe lanzar una excepción.
     */
    @Test
    void validateTTL_WithNullTtl_ShouldThrowException() {
        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class,
                () -> Validator.validateTTL("cache", null));
        assertEquals("ttl must be positive", exception.getMessage());
    }

    /**
     * Validar TTL con TTL cero debe lanzar una excepción.
     */
    @Test
    void validateTTL_WithZeroTtl_ShouldThrowException() {
        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class,
                () -> Validator.validateTTL("cache", 0L));
        assertEquals("ttl must be positive", exception.getMessage());
    }

    /**
     * Validar TTL con TTL negativo debe lanzar una excepción.
     */
    @Test
    void validateTTL_WithNegativeTtl_ShouldThrowException() {
        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class,
                () -> Validator.validateTTL("cache", -1L));
        assertEquals("ttl must be positive", exception.getMessage());
    }

    /**
     * Validar TTL con parámetros válidos no debe lanzar excepción.
     */
    @Test
    void validateTTL_WithValidParams_ShouldNotThrowException() {
        assertDoesNotThrow(() -> Validator.validateTTL("cache", 60L));
    }

    /**
     * Validar la operación de caché con TTL nulo debe lanzar una excepción.
     */
    @Test
    void validateCacheable_WithNullTtl_ShouldThrowException() {
        Supplier<String> loader = () -> "value";

        IllegalStateException exception = assertThrows(IllegalStateException.class,
                () -> Validator.validateCacheable("cache", "key", loader, null));
        assertEquals("ttl is required", exception.getMessage());
    }

    /**
     * Validar la operación de caché con TTL negativo debe lanzar una excepción.
     */
    @Test
    void validateCacheable_WithNegativeTtl_ShouldThrowException() {
        Supplier<String> loader = () -> "value";

        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class,
                () -> Validator.validateCacheable("cache", "key", loader, Duration.ofMinutes(-1)));
        assertEquals("ttl must be positive", exception.getMessage());
    }

    /**
     * Validar la operación de caché con TTL cero debe lanzar una excepción.
     */
    @Test
    void validateCacheable_WithZeroTtl_ShouldThrowException() {
        Supplier<String> loader = () -> "value";

        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class,
                () -> Validator.validateCacheable("cache", "key", loader, Duration.ZERO));
        assertEquals("ttl must be positive", exception.getMessage());
    }

    /**
     * Validar la operación de caché con nombre que contiene dos puntos debe lanzar una excepción.
     */
    @Test
    void validateCacheable_WithCacheNameContainingColon_ShouldThrowException() {
        Supplier<String> loader = () -> "value";

        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class,
                () -> Validator.validateCacheable("cache:name", "key", loader, Duration.ofMinutes(10)));
        assertEquals("cacheName cannot contain ':' or '*' characters", exception.getMessage());
    }

    /**
     * Validar la operación de caché con nombre que contiene asterisco debe lanzar una excepción.
     */
    @Test
    void validateCacheable_WithCacheNameContainingAsterisk_ShouldThrowException() {
        Supplier<String> loader = () -> "value";

        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class,
                () -> Validator.validateCacheable("cache*name", "key", loader, Duration.ofMinutes(10)));
        assertEquals("cacheName cannot contain ':' or '*' characters", exception.getMessage());
    }

    /**
     * Validar la operación de caché con clave que contiene asterisco debe lanzar una excepción.
     */
    @Test
    void validateCacheable_WithKeyContainingAsterisk_ShouldThrowException() {
        Supplier<String> loader = () -> "value";

        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class,
                () -> Validator.validateCacheable("cache", "key*value", loader, Duration.ofMinutes(10)));
        assertEquals("key cannot contain '*' character", exception.getMessage());
    }

    /**
     * Validar la operación de caché con parámetros válidos no debe lanzar excepción.
     */
    @Test
    void validateCacheable_WithValidParams_ShouldNotThrowException() {
        Supplier<String> loader = () -> "value";

        assertDoesNotThrow(() -> Validator.validateCacheable("cache", "key", loader,
                Duration.ofMinutes(10)));
    }

    /**
     * Validar invalidación con nombre de caché nulo debe lanzar una excepción.
     */
    @Test
    void validateCacheEvict_WithNullCacheName_ShouldThrowException() {
        IllegalStateException exception = assertThrows(IllegalStateException.class,
                () -> Validator.validateCacheEvict(null, "key"));
        assertEquals("cacheName is required", exception.getMessage());
    }

    /**
     * Validar invalidación con nombre de caché vacío debe lanzar una excepción.
     */
    @Test
    void validateCacheEvict_WithEmptyCacheName_ShouldThrowException() {
        IllegalStateException exception = assertThrows(IllegalStateException.class,
                () -> Validator.validateCacheEvict("", "key"));
        assertEquals("cacheName is required", exception.getMessage());
    }

    /**
     * Validar invalidación con clave nula debe lanzar una excepción.
     */
    @Test
    void validateCacheEvict_WithNullKey_ShouldThrowException() {
        IllegalStateException exception = assertThrows(IllegalStateException.class,
                () -> Validator.validateCacheEvict("cache", null));
        assertEquals("key is required", exception.getMessage());
    }

    /**
     * Validar invalidación con clave vacía debe lanzar una excepción.
     */
    @Test
    void validateCacheEvict_WithEmptyKey_ShouldThrowException() {
        IllegalStateException exception = assertThrows(IllegalStateException.class,
                () -> Validator.validateCacheEvict("cache", ""));
        assertEquals("key is required", exception.getMessage());
    }

    /**
     * Validar invalidación con nombre que contiene dos puntos debe lanzar una excepción.
     */
    @Test
    void validateCacheEvict_WithCacheNameContainingColon_ShouldThrowException() {
        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class,
                () -> Validator.validateCacheEvict("cache:name", "key"));
        assertEquals("cacheName cannot contain ':' or '*' characters", exception.getMessage());
    }

    /**
     * Validar invalidación con nombre que contiene asterisco debe lanzar una excepción.
     */
    @Test
    void validateCacheEvict_WithCacheNameContainingAsterisk_ShouldThrowException() {
        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class,
                () -> Validator.validateCacheEvict("cache*name", "key"));
        assertEquals("cacheName cannot contain ':' or '*' characters", exception.getMessage());
    }

    /**
     * Validar invalidación con clave que contiene asterisco debe lanzar una excepción.
     */
    @Test
    void validateCacheEvict_WithKeyContainingAsterisk_ShouldThrowException() {
        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class,
                () -> Validator.validateCacheEvict("cache", "key*value"));
        assertEquals("key cannot contain '*' character", exception.getMessage());
    }

    /**
     * Validar invalidación con parámetros válidos no debe lanzar excepción.
     */
    @Test
    void validateCacheEvict_WithValidParams_ShouldNotThrowException() {
        assertDoesNotThrow(() -> Validator.validateCacheEvict("cache", "key"));
    }

    /**
     * Validar invalidar todo con nombre de caché nulo debe lanzar una excepción.
     */
    @Test
    void validateCacheEvictAll_WithNullCacheName_ShouldThrowException() {
        IllegalStateException exception = assertThrows(IllegalStateException.class,
                () -> Validator.validateCacheEvict(null));
        assertEquals("cacheName is required", exception.getMessage());
    }

    /**
     * Validar invalidar todo con nombre de caché vacío debe lanzar una excepción.
     */
    @Test
    void validateCacheEvictAll_WithEmptyCacheName_ShouldThrowException() {
        IllegalStateException exception = assertThrows(IllegalStateException.class,
                () -> Validator.validateCacheEvict(""));
        assertEquals("cacheName is required", exception.getMessage());
    }

    /**
     * Validar invalidar todo con nombre de caché en blanco debe lanzar una excepción.
     */
    @Test
    void validateCacheEvictAll_WithWhitespaceCacheName_ShouldThrowException() {
        IllegalStateException exception = assertThrows(IllegalStateException.class,
                () -> Validator.validateCacheEvict("   "));
        assertEquals("cacheName is required", exception.getMessage());
    }

    /**
     * Validar invalidar todo con nombre que contiene dos puntos debe lanzar una excepción.
     */
    @Test
    void validateCacheEvictAll_WithCacheNameContainingColon_ShouldThrowException() {
        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class,
                () -> Validator.validateCacheEvict("cache:name"));
        assertEquals("cacheName cannot contain ':' or '*' characters", exception.getMessage());
    }

    /**
     * Validar invalidar todo con nombre que contiene asterisco debe lanzar una excepción.
     */
    @Test
    void validateCacheEvictAll_WithCacheNameContainingAsterisk_ShouldThrowException() {
        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class,
                () -> Validator.validateCacheEvict("cache*name"));
        assertEquals("cacheName cannot contain ':' or '*' characters", exception.getMessage());
    }

    /**
     * Validar invalidar todo con un nombre válido no debe lanzar excepción.
     */
    @Test
    void validateCacheEvictAll_WithValidCacheName_ShouldNotThrowException() {
        assertDoesNotThrow(() -> Validator.validateCacheEvict("cache"));
    }

    /**
     * Validar patrón nulo debe lanzar una excepción.
     */
    @Test
    void validatePattern_WithNullPattern_ShouldThrowException() {
        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class,
                () -> Validator.validatePattern(null));
        assertEquals("pattern is required", exception.getMessage());
    }

    /**
     * Validar patrón vacío debe lanzar una excepción.
     */
    @Test
    void validatePattern_WithEmptyPattern_ShouldThrowException() {
        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class,
                () -> Validator.validatePattern(""));
        assertEquals("pattern is required", exception.getMessage());
    }

    /**
     * Validar patrón en blanco debe lanzar una excepción.
     */
    @Test
    void validatePattern_WithWhitespacePattern_ShouldThrowException() {
        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class,
                () -> Validator.validatePattern("   "));
        assertEquals("pattern is required", exception.getMessage());
    }

    /**
     * Validar un patrón válido no debe lanzar excepción.
     */
    @Test
    void validatePattern_WithValidPattern_ShouldNotThrowException() {
        assertDoesNotThrow(() -> Validator.validatePattern("cache:*"));
    }
}
