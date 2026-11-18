package io.github.programming0026.juniverse.redis.util;

import io.github.programming0026.juniverse.redis.dto.HostsDto;
import lombok.extern.slf4j.Slf4j;
import org.springframework.util.StringUtils;

import java.time.Duration;
import java.util.List;
import java.util.Objects;
import java.util.function.Supplier;

@Slf4j
public final class Validator {

    private Validator() {

    }
    /**
     * Validates the servers.
     *
     * @param hosts servers
     */
    public static void validateHosts(List<HostsDto> hosts) {
        if (hosts == null || hosts.isEmpty()) {
            throw new IllegalArgumentException("At least one host must be configured");
        }
        for (HostsDto host : hosts) {
            if (host.getHostName() == null || host.getHostName().trim().isEmpty()) {
                throw new IllegalArgumentException("Host name cannot be empty");
            }
            if (host.getPort() < 1 || host.getPort() > 65535) {
                throw new IllegalArgumentException(
                        String.format("Invalid port %d. Port must be between 1 and 65535", host.getPort())
                );
            }
        }
    }

    /**
     * Validates the timeouts.
     *
     * @param connectionTimeout connection timeout
     * @param readTimeout       read timeout
     */
    public static void validateTimeout(Long connectionTimeout, Long readTimeout) {

        Objects.requireNonNull(connectionTimeout, "Connection timeout cannot be null");
        Objects.requireNonNull(readTimeout, "Read timeout cannot be null");

        if (connectionTimeout < 1) {
            throw new IllegalArgumentException(
                    String.format("Invalid connectionTimeout %d.", connectionTimeout)
            );
        }
        if (readTimeout < 1) {
            throw new IllegalArgumentException(
                    String.format("Invalid readTimeout %d.", readTimeout)
            );
        }
    }

    /**
     * Validates the application prefix. It cannot contain spaces or characters that Redis
     * interprets in patterns, because the prefix is part of the deletion patterns.
     *
     * @param keyPrefix prefix; may be null or empty
     */
    public static void validateKeyPrefix(String keyPrefix) {
        if (keyPrefix == null) {
            return;
        }
        for (char c : keyPrefix.toCharArray()) {
            if (Character.isWhitespace(c) || "*?[]\\".indexOf(c) >= 0) {
                throw new IllegalArgumentException(
                        String.format("Invalid keyPrefix '%s': it cannot contain whitespace or any of * ? [ ] \\",
                                keyPrefix));
            }
        }
    }

    /**
     * Validates the coalescing timeouts.
     *
     * @param lockTimeout lock duration in milliseconds
     * @param waitTimeout maximum wait in milliseconds
     */
    public static void validateCoalesceTimeouts(Long lockTimeout, Long waitTimeout) {

        Objects.requireNonNull(lockTimeout, "Coalesce lock timeout cannot be null");
        Objects.requireNonNull(waitTimeout, "Coalesce wait timeout cannot be null");

        if (lockTimeout < 1) {
            throw new IllegalArgumentException(
                    String.format("Invalid coalesceLockTimeout %d.", lockTimeout)
            );
        }
        if (waitTimeout < 1) {
            throw new IllegalArgumentException(
                    String.format("Invalid coalesceWaitTimeout %d.", waitTimeout)
            );
        }
    }

    /**
     * Validates the servers for standalone mode.
     *
     * @param hosts servers
     */
    public static void validateStandaloneHosts(List<HostsDto> hosts) {
        validateHosts(hosts);
        if (hosts.size() != 1) {
            throw new IllegalArgumentException(
                    "Standalone mode requires exactly one host entry"
            );
        }
    }

    /**
     * Validates the servers for cluster mode.
     * A single node is enough to discover the whole cluster (which is what managed services
     * with a single entry point offer), so a single server is valid;
     * a warning is only logged that the initial connection has no alternative node.
     *
     * @param hosts servers
     */
    public static void validateClusterHosts(List<HostsDto> hosts) {
        validateHosts(hosts);
        if (hosts.size() == 1) {
            log.warn("Cluster mode configured with a single host entry: "
                    + "the initial connection has no alternative node if {} is unreachable",
                    hosts.getFirst().getHostName());
        }
    }

    /**
     * Validates the servers for sentinel mode.
     * A single sentinel is valid; a warning is only logged that the initial connection
     * has no alternative.
     *
     * @param hosts servers
     * @param sentinelMaster sentinel master name
     */
    public static void validateSentinelHosts(List<HostsDto> hosts, String sentinelMaster) {
        validateHosts(hosts);
        if (!StringUtils.hasText(sentinelMaster)) {
            throw new IllegalArgumentException(
                    "sentinelMaster must be configured when using SENTINEL mode"
            );
        }
        if (hosts.size() == 1) {
            log.warn("Sentinel mode configured with a single host entry: "
                    + "the initial connection has no alternative sentinel if {} is unreachable",
                    hosts.getFirst().getHostName());
        }
    }
    /**
     * Validates the required fields.
     * @param cacheName cache name
     * @param key key
     * @param loader loader
     * @param <T> generic type
     */
    public static <T> void validateRequiredFields(String cacheName, String key, Supplier<T> loader) {
        if (!StringUtils.hasText(cacheName)) {
            throw new IllegalStateException("cacheName is required");
        }
        if (!StringUtils.hasText(key)) {
            throw new IllegalStateException("key is required");
        }
        if (loader == null) {
            throw new IllegalStateException("loader is required");
        }
    }

    /**
     * Validates the time to live.
     *
     * @param cacheName cache name
     * @param ttl       time to live
     */
    public static void validateTTL(String cacheName, Long ttl) {
        if (!StringUtils.hasText(cacheName)) {
            throw new IllegalStateException("cacheName is required");
        }

        if (ttl == null || ttl <= 0) {
            throw new IllegalArgumentException("ttl must be positive");
        }
    }
    /**
     * Validates the parameters of the cache operation.
     *
     * @param <T> generic type
     * @param cacheName cache name
     * @param key key
     * @param loader function that loads the value
     * @param ttl time to live
     */
    public static <T> void validateCacheable(String cacheName, String key, Supplier<T> loader, Duration ttl) {
        if (ttl == null) {
            throw new IllegalStateException("ttl is required");
        }

        if (ttl.isNegative() || ttl.isZero()) {
            throw new IllegalArgumentException("ttl must be positive");
        }

        validateRequiredFields(cacheName, key, loader);

        validateKeyFormat(cacheName, key);
    }

    /**
     * Validates the eviction parameters.
     *
     * @param cacheName cache name
     * @param key key
     */
    public static void validateCacheEvict(String cacheName, String key) {
        if (!StringUtils.hasText(cacheName)) {
            throw new IllegalStateException("cacheName is required");
        }
        if (!StringUtils.hasText(key)) {
            throw new IllegalStateException("key is required");
        }
        validateKeyFormat(cacheName, key);
    }

    /**
     * Validates the cache name for evicting everything.
     *
     * @param cacheName cache name
     */
    public static void validateCacheEvict(String cacheName) {
        if (!StringUtils.hasText(cacheName)) {
            throw new IllegalStateException("cacheName is required");
        }
        if (cacheName.contains(":") || cacheName.contains("*")) {
            throw new IllegalArgumentException(
                    "cacheName cannot contain ':' or '*' characters"
            );
        }
    }

    /**
     * Validates the pattern for evicting by pattern.
     *
     * @param pattern key pattern
     */
    public static void validatePattern(String pattern) {
        if (!StringUtils.hasText(pattern)) {
            throw new IllegalArgumentException("pattern is required");
        }
    }

    /**
     * Validates the key format.
     *
     * @param cacheName cache name
     * @param key key
     */
    private static void validateKeyFormat(String cacheName, String key) {
        if (cacheName.contains(":") || cacheName.contains("*")) {
            throw new IllegalArgumentException(
                    "cacheName cannot contain ':' or '*' characters"
            );
        }
        if (key.contains("*")) {
            throw new IllegalArgumentException(
                    "key cannot contain '*' character"
            );
        }
    }
}
