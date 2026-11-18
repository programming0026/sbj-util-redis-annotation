package io.github.programming0026.juniverse.redis.integration;

import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory;

/**
 * Repeats the tests of {@link CoalesceConcurrencyIT} against Redis in cluster mode,
 * where the lock and the pub/sub notifications go through the cluster client.
 *
 * @author Java Universe
 */
class ClusterCoalesceConcurrencyIT extends CoalesceConcurrencyIT {

    @Override
    protected LettuceConnectionFactory connectionFactory() {
        return RedisContainers.clusterFactory();
    }
}
