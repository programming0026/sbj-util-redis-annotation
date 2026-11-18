package io.github.programming0026.juniverse.redis.integration;

import java.io.IOException;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.ThreadLocalRandom;

import org.springframework.data.redis.connection.RedisClusterConfiguration;
import org.springframework.data.redis.connection.RedisStandaloneConfiguration;
import org.springframework.data.redis.connection.lettuce.LettuceClientConfiguration;
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory;
import org.testcontainers.DockerClientFactory;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.utility.DockerImageName;

/**
 * Redis containers shared by the integration tests.
 * Each container is started only once, the first time it is requested, and Testcontainers
 * removes it when the JVM ends.
 *
 * @author Java Universe
 */
public final class RedisContainers {

    /** Redis image used in all the tests. */
    public static final DockerImageName IMAGE = DockerImageName.parse("redis:7-alpine");

    /** Redis port inside the standalone container. */
    private static final int REDIS_PORT = 6379;

    /** Maximum time of each command; short so that a failure does not lengthen the test. */
    private static final Duration COMMAND_TIMEOUT = Duration.ofSeconds(5);

    /** Shared standalone container. */
    private static GenericContainer<?> standalone;

    /** Shared cluster container (a single node with all the slots). */
    private static GenericContainer<?> cluster;

    /** Port of the cluster node; it is the same inside and outside the container. */
    private static int clusterPort;

    private RedisContainers() {
    }

    /**
     * Creates a new standalone container, not started.
     * Useful for tests that need to stop Redis without affecting the others.
     *
     * @return container not started
     */
    public static GenericContainer<?> newStandalone() {
        return new GenericContainer<>(IMAGE).withExposedPorts(REDIS_PORT);
    }

    /**
     * Creates a connection factory to the shared standalone container.
     *
     * @return uninitialized connection factory
     */
    public static synchronized LettuceConnectionFactory standaloneFactory() {
        if (standalone == null) {
            standalone = newStandalone();
            standalone.start();
        }
        return standaloneFactory(standalone);
    }

    /**
     * Creates a connection factory to a specific standalone container.
     *
     * @param container container already started
     * @return uninitialized connection factory
     */
    public static LettuceConnectionFactory standaloneFactory(GenericContainer<?> container) {
        return standaloneFactory(container, COMMAND_TIMEOUT);
    }

    /**
     * Creates a connection factory to a specific standalone container.
     *
     * @param container container already started
     * @param commandTimeout maximum time of each command
     * @return uninitialized connection factory
     */
    public static LettuceConnectionFactory standaloneFactory(GenericContainer<?> container, Duration commandTimeout) {
        RedisStandaloneConfiguration config = new RedisStandaloneConfiguration(
                container.getHost(), container.getMappedPort(REDIS_PORT));
        return new LettuceConnectionFactory(config, clientConfiguration(commandTimeout));
    }

    /**
     * Creates a connection factory to the shared cluster.
     *
     * @return uninitialized connection factory
     */
    public static synchronized LettuceConnectionFactory clusterFactory() {
        if (cluster == null) {
            cluster = startCluster();
        }
        RedisClusterConfiguration config = new RedisClusterConfiguration(
                List.of(cluster.getHost() + ":" + clusterPort));
        return new LettuceConnectionFactory(config, clientConfiguration(COMMAND_TIMEOUT));
    }

    private static LettuceClientConfiguration clientConfiguration(Duration commandTimeout) {
        return LettuceClientConfiguration.builder()
                .commandTimeout(commandTimeout)
                .shutdownTimeout(Duration.ZERO)
                .build();
    }

    /**
     * Starts a single-node cluster that owns all 16384 slots.
     * A cluster node announces its own address to clients, so the port
     * must be the same inside and outside the container and the announced address must be
     * the Docker host's.
     *
     * @return container started with the cluster in a healthy state
     */
    private static GenericContainer<?> startCluster() {
        try {
            clusterPort = findFreePort();
            String port = String.valueOf(clusterPort);
            String announceIp = InetAddress
                    .getByName(DockerClientFactory.instance().dockerHostIpAddress())
                    .getHostAddress();

            GenericContainer<?> container = new GenericContainer<>(IMAGE)
                    .withCommand("redis-server",
                            "--port", port,
                            "--cluster-enabled", "yes",
                            "--cluster-announce-ip", announceIp,
                            "--save", "",
                            "--appendonly", "no");
            container.addExposedPort(clusterPort);
            container.setPortBindings(List.of(port + ":" + port));
            container.start();

            container.execInContainer("redis-cli", "-p", port, "cluster", "addslotsrange", "0", "16383");
            waitForClusterOk(container, port);
            return container;
        } catch (IOException e) {
            throw new IllegalStateException("Could not start the Redis cluster", e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Interrupted while starting the Redis cluster", e);
        }
    }

    private static void waitForClusterOk(GenericContainer<?> container, String port)
            throws IOException, InterruptedException {
        long deadline = System.nanoTime() + Duration.ofSeconds(20).toNanos();
        while (System.nanoTime() < deadline) {
            String info = container.execInContainer("redis-cli", "-p", port, "cluster", "info").getStdout();
            if (info.contains("cluster_state:ok")) {
                return;
            }
            Thread.sleep(100);
        }
        throw new IllegalStateException("The Redis cluster did not reach the ok state");
    }

    /**
     * Looks for a free port for the cluster node.
     * Redis also uses the port + 10000 for the cluster bus, so the chosen
     * port cannot exceed 55535.
     *
     * @return free port
     */
    private static int findFreePort() {
        return freePorts(1)[0];
    }

    /**
     * Looks for several different free ports, all below 55535 so that the port
     * + 10000 of the cluster bus remains valid.
     *
     * @param count how many ports are needed
     * @return free ports
     */
    public static int[] freePorts(int count) {
        java.util.Set<Integer> ports = new java.util.LinkedHashSet<>();
        for (int attempt = 0; attempt < 500 && ports.size() < count; attempt++) {
            int candidate = ThreadLocalRandom.current().nextInt(20000, 50000);
            try (ServerSocket socket = new ServerSocket(candidate)) {
                ports.add(socket.getLocalPort());
            } catch (IOException e) {
                // Busy: try another one.
            }
        }
        if (ports.size() < count) {
            throw new IllegalStateException("Could not find " + count + " free ports");
        }
        return ports.stream().mapToInt(Integer::intValue).toArray();
    }
}
