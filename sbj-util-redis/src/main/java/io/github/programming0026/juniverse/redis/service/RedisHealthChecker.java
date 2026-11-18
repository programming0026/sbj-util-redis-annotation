package io.github.programming0026.juniverse.redis.service;

import io.github.programming0026.juniverse.redis.dto.RedisStatusDto;
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
 * @author Java Universe
 */
@Slf4j
public class RedisHealthChecker {

    /** Maximum number of seconds to wait for the response to the INFO command. */
    private static final long INFO_TIMEOUT_SECONDS = 5;

    /**
     * Redis template.
     */
    private final RedisTemplate<String, Object> redisTemplate;

    public RedisHealthChecker(RedisTemplate<String, Object> redisTemplate) {
        this.redisTemplate = redisTemplate;
    }

    /**
     * Creates a status.
     *
     * @param message message
     * @return Redis status DTO
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
     * Checks whether Redis is active.
     *
     * @return Redis status DTO
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

                long startTime = System.nanoTime();
                String pong = connection.ping();
                long endTime = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - startTime);

                Map<String, String> info = getRedisInfo(connection);

                boolean isConnected = "PONG".equalsIgnoreCase(pong);
                String message = isConnected
                        ? "Redis is up and running and responding correctly"
                        : "Redis is not responding as expected. Response: " + pong;

                if (!isConnected) {
                    log.warn(message);
                }

                long usedMemory = parseLong(info.get("used_memory"), 0L);
                // maxmemory is the limit configured in Redis; 0 means "no limit".
                long maxMemory = parseLong(info.get("maxmemory"), 0L);
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
            log.error("Unexpected error checking Redis status", e);
            return createStatus(String.format("Unexpected error checking Redis status: %s", e.getMessage()));
        }
    }

    /**
     * Gets Redis information using a native Lettuce connection.
     * Supports the standalone, cluster and sentinel modes.
     *
     * @param connection Redis connection
     * @return properties of the INFO command, by name
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
                infoString = clusterCommands.info().get(INFO_TIMEOUT_SECONDS, TimeUnit.SECONDS);

            } else if (nativeConnection instanceof RedisAsyncCommands) {
                log.debug("Detected Redis Standalone/Sentinel connection");
                RedisAsyncCommands<?, ?> asyncCommands = (RedisAsyncCommands<?, ?>) nativeConnection;
                infoString = asyncCommands.info().get(INFO_TIMEOUT_SECONDS, TimeUnit.SECONDS);
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
     * Parses the output of the Redis INFO command.
     *
     * @param infoString output of the INFO command
     * @return map of key-value pairs
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
     * Safely converts a long value.
     *
     * @param value        text value
     * @param defaultValue default value
     * @return converted number or the default value
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
     * Safely converts an int value.
     *
     * @param value        text value
     * @param defaultValue default value
     * @return converted number or the default value
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
