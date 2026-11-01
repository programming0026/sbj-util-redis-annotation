package com.juniverse.redis.service;

import com.juniverse.redis.dto.RedisStatusDto;
import io.lettuce.core.api.async.RedisAsyncCommands;
import io.lettuce.core.cluster.api.async.RedisAdvancedClusterAsyncCommands;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataAccessException;
import org.springframework.data.redis.connection.RedisConnection;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.util.StringUtils;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

/**
 * @author ajuar
 */
@Slf4j
public class RedisHealthChecker {

    /**
     * Plantilla de Redis.
     */
    private final RedisTemplate<String, Object> redisTemplate;

    public RedisHealthChecker(RedisTemplate<String, Object> redisTemplate) {
        this.redisTemplate = redisTemplate;
    }

    /**
     * Crea estado.
     *
     * @param message mensaje
     * @return DTO de estado de Redis
     */
    private RedisStatusDto createStatus(String message) {

        return RedisStatusDto.builder()
                .connected(false)
                .errorMessage(message)
                .responseTime(0L)
                .maxMemory(0L)
                .usedMemory(0L)
                .connectedClients(0)
                .redisVersion("")
                .build();
    }

    /**
     * Comprueba si Redis está activo.
     *
     * @return DTO de estado de Redis
     */
    public RedisStatusDto isRedisActive() {

        try {
            if (redisTemplate == null) {
                log.error("RedisTemplate is null");
                return createStatus("RedisTemplate is not configured");
            }
            var connectionFactory = redisTemplate.getConnectionFactory();
            if (connectionFactory == null) {
                log.error("ConnectionFactory is null");
                return createStatus("ConnectionFactory is not available");
            }

            try (RedisConnection connection = connectionFactory.getConnection()) {

                long startTime = System.currentTimeMillis();
                String pong = connection.ping();
                long endTime = System.currentTimeMillis() - startTime;

                Map<String, String> info = getRedisInfo(connection);

                boolean isConnected = "PONG".equalsIgnoreCase(pong);
                String message = isConnected
                        ? "Redis is up and running and responding correctly"
                        : "Redis is not responding as expected. Response: " + pong;

                if (!isConnected) {
                    log.warn(message);
                }

                long usedMemory = parseLong(info.get("used_memory"), 0L);
                long maxMemory = parseLong(info.get("total_system_memory"), 0L);
                int connectedClients = parseInt(info.get("connected_clients"), 0);
                String redisVersion = info.getOrDefault("redis_version", "");

                return RedisStatusDto.builder()
                        .connected(isConnected)
                        .errorMessage(message)
                        .responseTime(endTime)
                        .usedMemory(usedMemory)
                        .maxMemory(maxMemory)
                        .connectedClients(connectedClients)
                        .redisVersion(redisVersion)
                        .build();
            }

        } catch (DataAccessException e) {
            log.error("Error checking Redis status: {}", e.getMessage());
            return createStatus(String.format("Error checking Redis status: %s", e.getMessage()));
        } catch (Exception e) {
            log.error("Unexpected error checking Redis status: {}", e.getMessage());
            return createStatus(String.format("Unexpected error checking Redis status: %s", e.getMessage()));
        }
    }

    /**
     * Obtiene la información de Redis con una conexión nativa de Lettuce.
     * Admite los modos standalone, clúster y sentinel.
     *
     * @param connection conexión a Redis
     * @return propiedades de información de Redis de de de mapa
     */
    private Map<String, String> getRedisInfo(RedisConnection connection) {
        Map<String, String> info = new HashMap<>();

        try {
            Object nativeConnection = connection.getNativeConnection();
            log.debug("Native connection type: {}", nativeConnection.getClass().getName());

            String infoString = null;

            if (nativeConnection instanceof RedisAdvancedClusterAsyncCommands) {
                log.debug("Detected Redis Cluster connection");
                RedisAdvancedClusterAsyncCommands<?, ?> clusterCommands =
                        (RedisAdvancedClusterAsyncCommands<?, ?>) nativeConnection;
                infoString = clusterCommands.info().get(5, TimeUnit.SECONDS);

            } else if (nativeConnection instanceof RedisAsyncCommands) {
                log.debug("Detected Redis Standalone/Sentinel connection");
                RedisAsyncCommands<?, ?> asyncCommands = (RedisAsyncCommands<?, ?>) nativeConnection;
                infoString = asyncCommands.info().get(5, TimeUnit.SECONDS);
            } else {
                log.warn("Unknown native connection type: {}. Attempting fallback to serverCommands",
                        nativeConnection.getClass().getName());
                var properties = connection.serverCommands().info();
                if (properties != null) {
                    for (Map.Entry<Object, Object> entry : properties.entrySet()) {
                        Object key = entry.getKey();
                        Object value = entry.getValue();
                        info.put(String.valueOf(key), String.valueOf(value));
                    }
                    log.debug("Successfully retrieved {} properties using serverCommands", info.size());
                    return info;
                }
            }

            if (StringUtils.hasText(infoString)) {
                info = parseRedisInfo(infoString);
                log.debug("Successfully parsed {} properties from INFO command", info.size());
            } else {
                log.warn("INFO command returned null or empty string");
            }

        } catch (InterruptedException e) {
            log.error("Failed to get Redis info: {}", e.getMessage());
            Thread.currentThread().interrupt();
        } catch(TimeoutException | ExecutionException e) {
            log.error("Execution error while getting Redis info: {}", e.getMessage());
        }

        return info;
    }

    /**
     * Interpreta la salida del comando INFO de Redis.
     *
     * @param infoString salida del comando INFO
     * @return mapa de pares clave-valor
     */
    private Map<String, String> parseRedisInfo(String infoString) {
        Map<String, String> result = new HashMap<>();

        if (!StringUtils.hasText(infoString)) {
            log.warn("INFO command returned empty or null string");
            return result;
        }

        for (String line : infoString.split("\r?\n")) {
            String trimmedLine = line.trim();
            if (trimmedLine.isEmpty() || trimmedLine.startsWith("#")) {
                continue;
            }

            int colonIndex = trimmedLine.indexOf(':');
            if (colonIndex > 0) {
                String key = trimmedLine.substring(0, colonIndex).trim();
                String value = trimmedLine.substring(colonIndex + 1).trim();
                result.put(key, value);
                log.trace("Parsed property: {} = {}", key, value);
            }
        }

        return result;
    }

    /**
     * Convierte un valor long de forma segura.
     *
     * @param value        valor en texto
     * @param defaultValue valor predeterminado
     * @return número convertido o el valor predeterminado
     */
    private long parseLong(String value, long defaultValue) {
        try {
            return StringUtils.hasText(value) ? Long.parseLong(value) : defaultValue;
        } catch (NumberFormatException e) {
            log.debug("Failed to parse long value '{}': {}", value, e.getMessage());
            return defaultValue;
        }
    }

    /**
     * Convierte un valor int de forma segura.
     *
     * @param value        valor en texto
     * @param defaultValue valor predeterminado
     * @return número convertido o el valor predeterminado
     */
    private int parseInt(String value, int defaultValue) {
        try {
            return StringUtils.hasText(value) ? Integer.parseInt(value) : defaultValue;
        } catch (NumberFormatException e) {
            log.debug("Failed to parse int value '{}': {}", value, e.getMessage());
            return defaultValue;
        }
    }
}
