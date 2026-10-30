package com.juniverse.redis.example.config;

import com.juniverse.redis.annotation.EnableRedisLibrary;
import com.juniverse.redis.annotation.HostEntry;
import com.juniverse.redis.annotation.TTLEntry;
import com.juniverse.redis.util.Mode;
import org.springframework.context.annotation.Configuration;

/**
 * Redis configuration class using sbj-util-redis library
 * Configures Redis in STANDALONE mode (a single server on localhost:6379) with TTL entries for different caches.
 * The SENTINEL and CLUSTER configurations are kept below, commented out, to switch modes.
 *
 * @author ajuar
 */
@Configuration
@EnableRedisLibrary(
        hostEntries = {
            @HostEntry(host = "localhost", port = 6379)
        },
        mode = Mode.STANDALONE,
        ttlEntries = {
            @TTLEntry(name = "products", ttl = 10)
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
