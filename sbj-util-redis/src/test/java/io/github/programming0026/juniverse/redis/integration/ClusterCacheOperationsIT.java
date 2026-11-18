package io.github.programming0026.juniverse.redis.integration;

import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory;

/**
 * Repeats the tests of {@link CacheOperationsIT} against Redis in cluster mode.
 * In a cluster keys are spread across slots, so multi-key deletions
 * and iterations with SCAN or KEYS follow a different path in the client.
 *
 * @author Java Universe
 */
class ClusterCacheOperationsIT extends CacheOperationsIT {

    @Override
    protected LettuceConnectionFactory connectionFactory() {
        return RedisContainers.clusterFactory();
    }
}
