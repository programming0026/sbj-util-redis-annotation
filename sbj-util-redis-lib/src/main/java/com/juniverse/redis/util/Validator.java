package com.juniverse.redis.util;

import com.juniverse.redis.dto.HostsDto;
import org.springframework.util.StringUtils;

import java.time.Duration;
import java.util.List;
import java.util.Objects;
import java.util.function.Supplier;

public final class Validator {

    private Validator() {

    }
    /**
     * Valida los servidores.
     *
     * @param hosts servidores
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
     * Valida tiempo de espera.
     *
     * @param connectionTimeout tiempo de espera de conexión
     * @param readTimeout       tiempo de espera de lectura
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
     * Valida los servidores del modo standalone.
     *
     * @param hosts servidores
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
     * Valida los servidores del modo clúster.
     *
     * @param hosts servidores
     */
    public static void validateClusterHosts(List<HostsDto> hosts) {
        validateHosts(hosts);
        if (hosts.size() > 1) {
            return;
        }
        throw new IllegalArgumentException(
                "Cluster mode requires host entries for proper redundancy"
        );
    }

    /**
     * Valida los servidores del modo sentinel.
     *
     * @param hosts servidores
     * @param sentinelMaster nombre del maestro de sentinel
     */
    public static void validateSentinelHosts(List<HostsDto> hosts, String sentinelMaster) {
        validateHosts(hosts);
        if (!StringUtils.hasText(sentinelMaster)) {
            throw new IllegalArgumentException(
                    "sentinelMaster must be configured when using SENTINEL mode"
            );
        }
        if (hosts.size() > 1) {
            return;
        }
        throw new IllegalArgumentException(
                "Sentinel mode requires host entries for proper redundancy"
        );
    }
    /**
     * Valida los campos obligatorios.
     * @param cacheName nombre de la caché
     * @param key clave
     * @param loader objeto
     * @param <T> tipo genérico
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
     * Valida tiempo de vida.
     *
     * @param cacheName nombre de caché
     * @param ttl       tiempo de vida
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
     * Valida los parámetros de la operación de caché.
     *
     * @param <T> tipo genérico
     * @param cacheName nombre de caché
     * @param key clave
     * @param loader función que carga el valor
     * @param ttl tiempo de vida
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
     * Valida los parámetros de invalidación.
     *
     * @param cacheName nombre de caché
     * @param key clave
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
     * Valida el nombre de la caché para invalidar todo.
     *
     * @param cacheName nombre de caché
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
     * Valida el patrón para invalidar por patrón.
     *
     * @param pattern patrón de claves
     */
    public static void validatePattern(String pattern) {
        if (!StringUtils.hasText(pattern)) {
            throw new IllegalArgumentException("pattern is required");
        }
    }

    /**
     * Valida formato de clave.
     *
     * @param cacheName nombre de caché
     * @param key clave
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
