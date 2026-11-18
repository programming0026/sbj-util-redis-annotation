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
 * Unit tests for Validator.
 *
 * @author Java Universe
 */
 class ValidatorTest {

    /**
     * Validating servers with a null list must throw an exception.
     */
    @Test
     void validateHosts_WithNullList_ShouldThrowException() {
        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class,
                () -> Validator.validateHosts(null));
        assertEquals("At least one host must be configured", exception.getMessage());
    }

    /**
     * Validating servers with an empty list must throw an exception.
     */
    @Test
     void validateHosts_WithEmptyList_ShouldThrowException() {
        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class,
                () -> Validator.validateHosts(Collections.emptyList()));
        assertEquals("At least one host must be configured", exception.getMessage());
    }

    /**
     * Validating servers with a null host name must throw an exception.
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
     * Validating servers with an empty host name must throw an exception.
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
     * Validating servers with a blank host name must throw an exception.
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
     * Validating servers with a port below the range must throw an exception.
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
     * Validating servers with a port above the range must throw an exception.
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
     * Validating servers with a valid host must not throw an exception.
     */
    @Test
     void validateHosts_WithValidHost_ShouldNotThrowException() {
        List<HostsDto> hosts = Arrays.asList(
                HostsDto.builder().hostName("localhost").port(6379).build()
        );

        assertDoesNotThrow(() -> Validator.validateHosts(hosts));
    }

    /**
     * Validating timeouts with a null connection timeout must throw an exception.
     */
    @Test
     void validateTimeout_WithNullConnectionTimeout_ShouldThrowException() {
        NullPointerException exception = assertThrows(NullPointerException.class,
                () -> Validator.validateTimeout(null, 5000L));
        assertEquals("Connection timeout cannot be null", exception.getMessage());
    }

    /**
     * Validating timeouts with a null read timeout must throw an exception.
     */
    @Test
     void validateTimeout_WithNullReadTimeout_ShouldThrowException() {
        NullPointerException exception = assertThrows(NullPointerException.class,
                () -> Validator.validateTimeout(5000L, null));
        assertEquals("Read timeout cannot be null", exception.getMessage());
    }

    /**
     * Validating timeouts with a negative connection timeout must throw an exception.
     */
    @Test
     void validateTimeout_WithNegativeConnectionTimeout_ShouldThrowException() {
        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class,
                () -> Validator.validateTimeout(-1L, 5000L));
        assertEquals("Invalid connectionTimeout -1.", exception.getMessage());
    }

    /**
     * Validating timeouts with a zero connection timeout must throw an exception.
     */
    @Test
     void validateTimeout_WithZeroConnectionTimeout_ShouldThrowException() {
        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class,
                () -> Validator.validateTimeout(0L, 5000L));
        assertEquals("Invalid connectionTimeout 0.", exception.getMessage());
    }

    /**
     * Validating timeouts with a negative read timeout must throw an exception.
     */
    @Test
     void validateTimeout_WithNegativeReadTimeout_ShouldThrowException() {
        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class,
                () -> Validator.validateTimeout(5000L, -1L));
        assertEquals("Invalid readTimeout -1.", exception.getMessage());
    }

    /**
     * Validating timeouts with a zero read timeout must throw an exception.
     */
    @Test
     void validateTimeout_WithZeroReadTimeout_ShouldThrowException() {
        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class,
                () -> Validator.validateTimeout(5000L, 0L));
        assertEquals("Invalid readTimeout 0.", exception.getMessage());
    }

    /**
     * Validating valid timeouts must not throw an exception.
     */
    @Test
     void validateTimeout_WithValidTimeouts_ShouldNotThrowException() {
        assertDoesNotThrow(() -> Validator.validateTimeout(5000L, 3000L));
    }

    /**
     * Validating standalone mode with several servers must throw an exception.
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
     * Validating standalone mode with one server must not throw an exception.
     */
    @Test
     void validateStandaloneHosts_WithSingleHost_ShouldNotThrowException() {
        List<HostsDto> hosts = Arrays.asList(
                HostsDto.builder().hostName("localhost").port(6379).build()
        );

        assertDoesNotThrow(() -> Validator.validateStandaloneHosts(hosts));
    }

    /**
     * Validating cluster mode with a single server is valid: one node is enough to discover the rest.
     */
    @Test
     void validateClusterHosts_WithSingleHost_ShouldNotThrowException() {
        List<HostsDto> hosts = Arrays.asList(
                HostsDto.builder().hostName("localhost").port(6379).build()
        );

        assertDoesNotThrow(() -> Validator.validateClusterHosts(hosts));
    }

    /**
     * Validating cluster mode with several servers must not throw an exception.
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
     * Validating sentinel with a null master must throw an exception.
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
     * Validating sentinel with an empty master must throw an exception.
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
     * Validating sentinel with a single server is valid.
     */
    @Test
     void validateSentinelHosts_WithSingleHost_ShouldNotThrowException() {
        List<HostsDto> hosts = Arrays.asList(
                HostsDto.builder().hostName("localhost").port(26379).build()
        );

        assertDoesNotThrow(() -> Validator.validateSentinelHosts(hosts, "mymaster"));
    }

    /**
     * Validating sentinel with a valid configuration must not throw an exception.
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
     * Validating required fields with a null cache name must throw an exception.
     */
    @Test
     void validateRequiredFields_WithNullCacheName_ShouldThrowException() {
        Supplier<String> loader = () -> "value";

        IllegalStateException exception = assertThrows(IllegalStateException.class,
                () -> Validator.validateRequiredFields(null, "key", loader));
        assertEquals("cacheName is required", exception.getMessage());
    }

    /**
     * Validating required fields with an empty cache name must throw an exception.
     */
    @Test
     void validateRequiredFields_WithEmptyCacheName_ShouldThrowException() {
        Supplier<String> loader = () -> "value";

        IllegalStateException exception = assertThrows(IllegalStateException.class,
                () -> Validator.validateRequiredFields("", "key", loader));
        assertEquals("cacheName is required", exception.getMessage());
    }

    /**
     * Validating required fields with a null key must throw an exception.
     */
    @Test
     void validateRequiredFields_WithNullKey_ShouldThrowException() {
        Supplier<String> loader = () -> "value";

        IllegalStateException exception = assertThrows(IllegalStateException.class,
                () -> Validator.validateRequiredFields("cache", null, loader));
        assertEquals("key is required", exception.getMessage());
    }

    /**
     * Validating required fields with an empty key must throw an exception.
     */
    @Test
     void validateRequiredFields_WithEmptyKey_ShouldThrowException() {
        Supplier<String> loader = () -> "value";

        IllegalStateException exception = assertThrows(IllegalStateException.class,
                () -> Validator.validateRequiredFields("cache", "", loader));
        assertEquals("key is required", exception.getMessage());
    }

    /**
     * Validating required fields with a null loader must throw an exception.
     */
    @Test
     void validateRequiredFields_WithNullLoader_ShouldThrowException() {
        IllegalStateException exception = assertThrows(IllegalStateException.class,
                () -> Validator.validateRequiredFields("cache", "key", null));
        assertEquals("loader is required", exception.getMessage());
    }

    /**
     * Validating required fields with valid parameters must not throw an exception.
     */
    @Test
     void validateRequiredFields_WithValidParams_ShouldNotThrowException() {
        Supplier<String> loader = () -> "value";

        assertDoesNotThrow(() -> Validator.validateRequiredFields("cache", "key", loader));
    }

    /**
     * Validating TTL with a null cache name must throw an exception.
     */
    @Test
     void validateTTL_WithNullCacheName_ShouldThrowException() {
        IllegalStateException exception = assertThrows(IllegalStateException.class,
                () -> Validator.validateTTL(null, 60L));
        assertEquals("cacheName is required", exception.getMessage());
    }

    /**
     * Validating TTL with an empty cache name must throw an exception.
     */
    @Test
     void validateTTL_WithEmptyCacheName_ShouldThrowException() {
        IllegalStateException exception = assertThrows(IllegalStateException.class,
                () -> Validator.validateTTL("", 60L));
        assertEquals("cacheName is required", exception.getMessage());
    }

    /**
     * Validating TTL with a null TTL must throw an exception.
     */
    @Test
     void validateTTL_WithNullTtl_ShouldThrowException() {
        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class,
                () -> Validator.validateTTL("cache", null));
        assertEquals("ttl must be positive", exception.getMessage());
    }

    /**
     * Validating TTL with a zero TTL must throw an exception.
     */
    @Test
     void validateTTL_WithZeroTtl_ShouldThrowException() {
        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class,
                () -> Validator.validateTTL("cache", 0L));
        assertEquals("ttl must be positive", exception.getMessage());
    }

    /**
     * Validating TTL with a negative TTL must throw an exception.
     */
    @Test
     void validateTTL_WithNegativeTtl_ShouldThrowException() {
        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class,
                () -> Validator.validateTTL("cache", -1L));
        assertEquals("ttl must be positive", exception.getMessage());
    }

    /**
     * Validating TTL with valid parameters must not throw an exception.
     */
    @Test
     void validateTTL_WithValidParams_ShouldNotThrowException() {
        assertDoesNotThrow(() -> Validator.validateTTL("cache", 60L));
    }

    /**
     * Validating the cache operation with a null TTL must throw an exception.
     */
    @Test
     void validateCacheable_WithNullTtl_ShouldThrowException() {
        Supplier<String> loader = () -> "value";

        IllegalStateException exception = assertThrows(IllegalStateException.class,
                () -> Validator.validateCacheable("cache", "key", loader, null));
        assertEquals("ttl is required", exception.getMessage());
    }

    /**
     * Validating the cache operation with a negative TTL must throw an exception.
     */
    @Test
     void validateCacheable_WithNegativeTtl_ShouldThrowException() {
        Supplier<String> loader = () -> "value";

        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class,
                () -> Validator.validateCacheable("cache", "key", loader, Duration.ofMinutes(-1)));
        assertEquals("ttl must be positive", exception.getMessage());
    }

    /**
     * Validating the cache operation with a zero TTL must throw an exception.
     */
    @Test
     void validateCacheable_WithZeroTtl_ShouldThrowException() {
        Supplier<String> loader = () -> "value";

        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class,
                () -> Validator.validateCacheable("cache", "key", loader, Duration.ZERO));
        assertEquals("ttl must be positive", exception.getMessage());
    }

    /**
     * Validating the cache operation with a name containing a colon must throw an exception.
     */
    @Test
     void validateCacheable_WithCacheNameContainingColon_ShouldThrowException() {
        Supplier<String> loader = () -> "value";

        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class,
                () -> Validator.validateCacheable("cache:name", "key", loader, Duration.ofMinutes(10)));
        assertEquals("cacheName cannot contain ':' or '*' characters", exception.getMessage());
    }

    /**
     * Validating the cache operation with a name containing an asterisk must throw an exception.
     */
    @Test
     void validateCacheable_WithCacheNameContainingAsterisk_ShouldThrowException() {
        Supplier<String> loader = () -> "value";

        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class,
                () -> Validator.validateCacheable("cache*name", "key", loader, Duration.ofMinutes(10)));
        assertEquals("cacheName cannot contain ':' or '*' characters", exception.getMessage());
    }

    /**
     * Validating the cache operation with a key containing an asterisk must throw an exception.
     */
    @Test
     void validateCacheable_WithKeyContainingAsterisk_ShouldThrowException() {
        Supplier<String> loader = () -> "value";

        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class,
                () -> Validator.validateCacheable("cache", "key*value", loader, Duration.ofMinutes(10)));
        assertEquals("key cannot contain '*' character", exception.getMessage());
    }

    /**
     * Validating the cache operation with valid parameters must not throw an exception.
     */
    @Test
     void validateCacheable_WithValidParams_ShouldNotThrowException() {
        Supplier<String> loader = () -> "value";

        assertDoesNotThrow(() -> Validator.validateCacheable("cache", "key", loader,
                Duration.ofMinutes(10)));
    }

    /**
     * Validating eviction with a null cache name must throw an exception.
     */
    @Test
     void validateCacheEvict_WithNullCacheName_ShouldThrowException() {
        IllegalStateException exception = assertThrows(IllegalStateException.class,
                () -> Validator.validateCacheEvict(null, "key"));
        assertEquals("cacheName is required", exception.getMessage());
    }

    /**
     * Validating eviction with an empty cache name must throw an exception.
     */
    @Test
     void validateCacheEvict_WithEmptyCacheName_ShouldThrowException() {
        IllegalStateException exception = assertThrows(IllegalStateException.class,
                () -> Validator.validateCacheEvict("", "key"));
        assertEquals("cacheName is required", exception.getMessage());
    }

    /**
     * Validating eviction with a null key must throw an exception.
     */
    @Test
     void validateCacheEvict_WithNullKey_ShouldThrowException() {
        IllegalStateException exception = assertThrows(IllegalStateException.class,
                () -> Validator.validateCacheEvict("cache", null));
        assertEquals("key is required", exception.getMessage());
    }

    /**
     * Validating eviction with an empty key must throw an exception.
     */
    @Test
     void validateCacheEvict_WithEmptyKey_ShouldThrowException() {
        IllegalStateException exception = assertThrows(IllegalStateException.class,
                () -> Validator.validateCacheEvict("cache", ""));
        assertEquals("key is required", exception.getMessage());
    }

    /**
     * Validating eviction with a name containing a colon must throw an exception.
     */
    @Test
     void validateCacheEvict_WithCacheNameContainingColon_ShouldThrowException() {
        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class,
                () -> Validator.validateCacheEvict("cache:name", "key"));
        assertEquals("cacheName cannot contain ':' or '*' characters", exception.getMessage());
    }

    /**
     * Validating eviction with a name containing an asterisk must throw an exception.
     */
    @Test
     void validateCacheEvict_WithCacheNameContainingAsterisk_ShouldThrowException() {
        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class,
                () -> Validator.validateCacheEvict("cache*name", "key"));
        assertEquals("cacheName cannot contain ':' or '*' characters", exception.getMessage());
    }

    /**
     * Validating eviction with a key containing an asterisk must throw an exception.
     */
    @Test
     void validateCacheEvict_WithKeyContainingAsterisk_ShouldThrowException() {
        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class,
                () -> Validator.validateCacheEvict("cache", "key*value"));
        assertEquals("key cannot contain '*' character", exception.getMessage());
    }

    /**
     * Validating eviction with valid parameters must not throw an exception.
     */
    @Test
     void validateCacheEvict_WithValidParams_ShouldNotThrowException() {
        assertDoesNotThrow(() -> Validator.validateCacheEvict("cache", "key"));
    }

    /**
     * Validating evict-all with a null cache name must throw an exception.
     */
    @Test
     void validateCacheEvictAll_WithNullCacheName_ShouldThrowException() {
        IllegalStateException exception = assertThrows(IllegalStateException.class,
                () -> Validator.validateCacheEvict(null));
        assertEquals("cacheName is required", exception.getMessage());
    }

    /**
     * Validating evict-all with an empty cache name must throw an exception.
     */
    @Test
     void validateCacheEvictAll_WithEmptyCacheName_ShouldThrowException() {
        IllegalStateException exception = assertThrows(IllegalStateException.class,
                () -> Validator.validateCacheEvict(""));
        assertEquals("cacheName is required", exception.getMessage());
    }

    /**
     * Validating evict-all with a blank cache name must throw an exception.
     */
    @Test
     void validateCacheEvictAll_WithWhitespaceCacheName_ShouldThrowException() {
        IllegalStateException exception = assertThrows(IllegalStateException.class,
                () -> Validator.validateCacheEvict("   "));
        assertEquals("cacheName is required", exception.getMessage());
    }

    /**
     * Validating evict-all with a name containing a colon must throw an exception.
     */
    @Test
     void validateCacheEvictAll_WithCacheNameContainingColon_ShouldThrowException() {
        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class,
                () -> Validator.validateCacheEvict("cache:name"));
        assertEquals("cacheName cannot contain ':' or '*' characters", exception.getMessage());
    }

    /**
     * Validating evict-all with a name containing an asterisk must throw an exception.
     */
    @Test
     void validateCacheEvictAll_WithCacheNameContainingAsterisk_ShouldThrowException() {
        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class,
                () -> Validator.validateCacheEvict("cache*name"));
        assertEquals("cacheName cannot contain ':' or '*' characters", exception.getMessage());
    }

    /**
     * Validating evict-all with a valid name must not throw an exception.
     */
    @Test
     void validateCacheEvictAll_WithValidCacheName_ShouldNotThrowException() {
        assertDoesNotThrow(() -> Validator.validateCacheEvict("cache"));
    }

    /**
     * Validating a null pattern must throw an exception.
     */
    @Test
     void validatePattern_WithNullPattern_ShouldThrowException() {
        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class,
                () -> Validator.validatePattern(null));
        assertEquals("pattern is required", exception.getMessage());
    }

    /**
     * Validating an empty pattern must throw an exception.
     */
    @Test
     void validatePattern_WithEmptyPattern_ShouldThrowException() {
        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class,
                () -> Validator.validatePattern(""));
        assertEquals("pattern is required", exception.getMessage());
    }

    /**
     * Validating a blank pattern must throw an exception.
     */
    @Test
     void validatePattern_WithWhitespacePattern_ShouldThrowException() {
        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class,
                () -> Validator.validatePattern("   "));
        assertEquals("pattern is required", exception.getMessage());
    }

    /**
     * Validating a valid pattern must not throw an exception.
     */
    @Test
     void validatePattern_WithValidPattern_ShouldNotThrowException() {
        assertDoesNotThrow(() -> Validator.validatePattern("cache:*"));
    }

    /**
     * Valid coalescing timeouts must not throw an exception.
     */
    @Test
     void validateCoalesceTimeouts_WithValidValues_ShouldNotThrowException() {
        assertDoesNotThrow(() -> Validator.validateCoalesceTimeouts(30000L, 1L));
    }

    /**
     * Null coalescing timeouts must be rejected.
     */
    @Test
     void validateCoalesceTimeouts_WithNullValues_ShouldThrowException() {
        assertEquals("Coalesce lock timeout cannot be null",
                assertThrows(NullPointerException.class,
                        () -> Validator.validateCoalesceTimeouts(null, 30000L)).getMessage());
        assertEquals("Coalesce wait timeout cannot be null",
                assertThrows(NullPointerException.class,
                        () -> Validator.validateCoalesceTimeouts(30000L, null)).getMessage());
    }

    /**
     * Non-positive coalescing timeouts must be rejected.
     */
    @Test
     void validateCoalesceTimeouts_WithNonPositiveValues_ShouldThrowException() {
        assertEquals("Invalid coalesceLockTimeout 0.",
                assertThrows(IllegalArgumentException.class,
                        () -> Validator.validateCoalesceTimeouts(0L, 30000L)).getMessage());
        assertEquals("Invalid coalesceWaitTimeout -5.",
                assertThrows(IllegalArgumentException.class,
                        () -> Validator.validateCoalesceTimeouts(30000L, -5L)).getMessage());
    }

    /**
     * The application prefix accepts plain text and rejects spaces and pattern characters.
     */
    @Test
     void validateKeyPrefix_ShouldRejectWhitespaceAndGlobCharacters() {
        assertDoesNotThrow(() -> Validator.validateKeyPrefix(null));
        assertDoesNotThrow(() -> Validator.validateKeyPrefix(""));
        assertDoesNotThrow(() -> Validator.validateKeyPrefix("shop-pedidos:v2"));

        for (String invalid : List.of("my shop", "shop*", "shop?", "shop[1]", "shop\\x")) {
            IllegalArgumentException exception = assertThrows(IllegalArgumentException.class,
                    () -> Validator.validateKeyPrefix(invalid));
            assertTrue(exception.getMessage().contains("keyPrefix"));
        }
    }
}
