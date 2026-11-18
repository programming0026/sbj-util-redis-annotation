package io.github.programming0026.juniverse.redis.integration;

import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory;

/**
 * Repite las pruebas de {@link CacheOperationsIT} contra Redis en modo clúster.
 * En clúster las claves se reparten por slots, así que los borrados de varias claves
 * y los recorridos con SCAN o KEYS siguen otro camino en el cliente.
 *
 * @author Java Universe
 */
class ClusterCacheOperationsIT extends CacheOperationsIT {

    @Override
    protected LettuceConnectionFactory connectionFactory() {
        return RedisContainers.clusterFactory();
    }
}
