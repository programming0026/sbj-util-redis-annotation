package com.juniverse.redis.example.config;

import io.github.programming0026.juniverse.redis.annotation.EnableRedisLibrary;
import io.github.programming0026.juniverse.redis.annotation.HostEntry;
import io.github.programming0026.juniverse.redis.annotation.TTLEntry;
import io.github.programming0026.juniverse.redis.enums.Mode;
import org.springframework.context.annotation.Configuration;

/**
 * Redis configuration using the sbj-util-redis library.
 * Configures Redis in STANDALONE mode (a single server on port 6379) with TTL entries for different caches.
 * String attributes support {@code ${...}} placeholders: the host, user, password and cache name
 * are read from {@code application.properties} and can be overridden with environment variables
 * or command-line arguments (for example {@code --redis.host=redis.internal}).
 * Numeric attributes (port, TTL) do not support placeholders because Java requires constants.
 * The SENTINEL and CLUSTER configurations are kept below, commented out, to switch modes.
 *
 * @author Java Universe
 */
@Configuration
@EnableRedisLibrary(
        hostEntries = {
            @HostEntry(host = "${redis.host}", port = 6379)
        },
        userName = "${redis.username:}",
        pwd = "${redis.password:}",
        mode = Mode.STANDALONE,
        ttlEntries = {
            @TTLEntry(name = "${cache.products.name}", ttl = 10)
        }
)
/*@EnableRedisLibrary(
        hostEntries = {
            @HostEntry(host = "localhost", port = 26379),
            @HostEntry(host = "localhost", port = 26380),
            @HostEntry(host = "localhost", port = 26381)
        },
        mode = Mode.SENTINEL,
        sentinelMaster = "mymaster",
        ttlEntries = {
            @TTLEntry(name = "products", ttl = 10)
        }
)*/
/*@EnableRedisLibrary(
        hostEntries = {
            @HostEntry(host = "localhost", port = 7000),
            @HostEntry(host = "localhost", port = 7001),
            @HostEntry(host = "localhost", port = 7002),
            @HostEntry(host = "localhost", port = 7003),
            @HostEntry(host = "localhost", port = 7004),
            @HostEntry(host = "localhost", port = 7005)
        },
        mode = Mode.CLUSTER,
        ttlEntries = {
            @TTLEntry(name = "products", ttl = 10)
        }
)*/
public class AppConfig {

}
