package com.juniverse.redis.example.config;

import io.github.programming0026.juniverse.redis.annotation.EnableRedisLibrary;
import io.github.programming0026.juniverse.redis.annotation.HostEntry;
import io.github.programming0026.juniverse.redis.annotation.TTLEntry;
import io.github.programming0026.juniverse.redis.util.Mode;
import org.springframework.context.annotation.Configuration;

/**
 * Configuración de Redis con la librería sbj-util-redis
 * Configura Redis en modo STANDALONE (un solo servidor en el puerto 6379) con entradas de TTL para distintas cachés.
 * Los atributos de texto admiten placeholders {@code ${...}}: el host, el usuario, la contraseña y el nombre
 * de la caché se leen de {@code application.properties} y se pueden sobrescribir con variables de entorno
 * o argumentos de línea de comandos (por ejemplo {@code --redis.host=redis.internal}).
 * Los atributos numéricos (puerto, TTL) no admiten placeholders porque Java exige constantes.
 * Las configuraciones SENTINEL y CLUSTER se conservan abajo, comentadas, para cambiar de modo.
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
