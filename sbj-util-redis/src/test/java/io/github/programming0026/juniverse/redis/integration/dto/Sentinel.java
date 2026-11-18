package io.github.programming0026.juniverse.redis.integration.dto;

import io.github.programming0026.juniverse.redis.integration.RedisTopologies;
import org.testcontainers.containers.GenericContainer;

/**
 * Master, replica and three sentinels.
 *
 * @param container container that hosts them
 * @param masterPort port of the initial master
 * @param replicaPort port of the initial replica
 * @param sentinelPorts ports of the sentinels
 * @author Java Universe
 */
public record Sentinel(GenericContainer<?> container, int masterPort, int replicaPort, int[] sentinelPorts) {

    /**
     * Runs redis-cli against a data node.
     *
     * @param port node port
     * @param command command and arguments
     * @return command output
     */
    public String data(int port, String... command) {
        return RedisTopologies.cli(container, port, RedisTopologies.DATA_PASSWORD, command);
    }

    /**
     * Runs redis-cli against the first sentinel.
     *
     * @param command command and arguments
     * @return command output
     */
    public String sentinel(String... command) {
        return RedisTopologies.cli(container, sentinelPorts[0], RedisTopologies.SENTINEL_PASSWORD, command);
    }
}
