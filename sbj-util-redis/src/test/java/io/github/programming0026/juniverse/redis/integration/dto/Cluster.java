package io.github.programming0026.juniverse.redis.integration.dto;

import io.github.programming0026.juniverse.redis.integration.RedisTopologies;
import org.testcontainers.containers.GenericContainer;

/**
 * Cluster of three masters and three replicas.
 *
 * @param container container that hosts them
 * @param ports ports of the six nodes
 * @author Java Universe
 */
public record Cluster(GenericContainer<?> container, int[] ports) {

    /**
     * Runs redis-cli against a node.
     *
     * @param port node port
     * @param command command and arguments
     * @return command output
     */
    public String node(int port, String... command) {
        return RedisTopologies.cli(container, port, null, command);
    }
}
