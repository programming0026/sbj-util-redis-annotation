package io.github.programming0026.juniverse.redis.integration;

import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory;

/**
 * Repite las pruebas de {@link CoalesceConcurrencyIT} contra Redis en modo clúster,
 * donde el bloqueo y los avisos por pub/sub pasan por el cliente de clúster.
 *
 * @author Java Universe
 */
class ClusterCoalesceConcurrencyIT extends CoalesceConcurrencyIT {

    @Override
    protected LettuceConnectionFactory connectionFactory() {
        return RedisContainers.clusterFactory();
    }
}
