package com.juniverse.redis.example.config;

import io.github.programming0026.juniverse.redis.annotation.EnableRedisLibrary;
import io.github.programming0026.juniverse.redis.annotation.HostEntry;
import io.github.programming0026.juniverse.redis.annotation.TTLEntry;
import io.github.programming0026.juniverse.redis.util.Mode;
import org.springframework.context.annotation.Configuration;

/**
 * Configuración de Redis con la librería sbj-util-redis
 * Configura Redis en modo STANDALONE (un solo servidor en localhost:6379) con entradas de TTL para distintas cachés.
 * Las configuraciones SENTINEL y CLUSTER se conservan abajo, comentadas, para cambiar de modo.
 *
 * @author Java Universe
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
