package io.github.programming0026.juniverse.redis.integration;

import io.github.programming0026.juniverse.redis.integration.dto.Cluster;
import io.github.programming0026.juniverse.redis.integration.dto.Sentinel;
import java.io.IOException;
import java.net.InetAddress;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.StringJoiner;

import org.testcontainers.DockerClientFactory;
import org.testcontainers.containers.GenericContainer;

/**
 * Starts in Docker the multi-node Redis topologies: Sentinel and cluster with replicas.
 *
 * <p>All the nodes of a topology run in the same container, each on its own port, and
 * those ports are published with the same number on the host. This way the addresses the nodes
 * announce to each other and to the clients ({@code 127.0.0.1:port}) are valid both inside the
 * container and from the tests.</p>
 *
 * @author Java Universe
 */
public final class RedisTopologies {

    /** Password of the data nodes in the Sentinel topology. */
    public static final String DATA_PASSWORD = "data-password";

    /** Password of the sentinels. */
    public static final String SENTINEL_PASSWORD = "sentinel-password";

    /** Name of the master watched by the sentinels. */
    public static final String MASTER_NAME = "mymaster";

    private RedisTopologies() {
    }

    /**
     * Starts the Sentinel topology with authentication on the data nodes and on the sentinels.
     * The sentinels consider the master down after 2 seconds without a response.
     *
     * @return topology ready to use
     */
    public static Sentinel startSentinel() {
        int[] ports = RedisContainers.freePorts(5);
        int master = ports[0];
        int replica = ports[1];
        int[] sentinels = {ports[2], ports[3], ports[4]};

        String dataOptions = " --requirepass " + DATA_PASSWORD + " --masterauth " + DATA_PASSWORD
                + " --replica-announce-ip 127.0.0.1 --save '' --appendonly no --daemonize yes";
        StringBuilder script = new StringBuilder("mkdir -p /data/sentinel && ")
                .append("redis-server --port ").append(master).append(dataOptions).append(" && ")
                .append("redis-server --port ").append(replica).append(" --replicaof 127.0.0.1 ").append(master)
                .append(dataOptions).append(" && ");
        for (int sentinel : sentinels) {
            String conf = "/data/sentinel/" + sentinel + ".conf";
            script.append("printf '%s\\n'")
                    .append(" 'port ").append(sentinel).append("'")
                    .append(" 'requirepass ").append(SENTINEL_PASSWORD).append("'")
                    .append(" 'sentinel sentinel-pass ").append(SENTINEL_PASSWORD).append("'")
                    .append(" 'sentinel monitor ").append(MASTER_NAME).append(" 127.0.0.1 ").append(master)
                    .append(" 2'")
                    .append(" 'sentinel auth-pass ").append(MASTER_NAME).append(' ').append(DATA_PASSWORD).append("'")
                    .append(" 'sentinel down-after-milliseconds ").append(MASTER_NAME).append(" 2000'")
                    .append(" 'sentinel failover-timeout ").append(MASTER_NAME).append(" 10000'")
                    .append(" 'sentinel announce-ip 127.0.0.1'")
                    .append(" > ").append(conf).append(" && ")
                    .append("redis-sentinel ").append(conf).append(" --daemonize yes && ");
        }
        script.append("tail -f /dev/null");

        GenericContainer<?> container = startWithPorts(script.toString(), ports);
        Sentinel topology = new Sentinel(container, master, replica, sentinels);
        awaitOutput("the sentinels did not reach an agreement",
                () -> topology.sentinel("sentinel", "master", MASTER_NAME),
                output -> output.contains("num-other-sentinels\n2") && output.contains("num-slaves\n1"));
        return topology;
    }

    /**
     * Starts a cluster of three masters with one replica each.
     * A node is considered down after 2 seconds without a response.
     *
     * @return cluster ready to use
     */
    public static Cluster startCluster() {
        int[] ports = RedisContainers.freePorts(6);
        StringBuilder script = new StringBuilder();
        StringJoiner nodes = new StringJoiner(" ");
        for (int port : ports) {
            script.append("redis-server --port ").append(port)
                    .append(" --cluster-enabled yes --cluster-node-timeout 2000")
                    .append(" --cluster-config-file nodes-").append(port).append(".conf")
                    .append(" --cluster-announce-ip 127.0.0.1")
                    .append(" --save '' --appendonly no --daemonize yes && ");
            nodes.add("127.0.0.1:" + port);
        }
        script.append("sleep 1 && redis-cli --cluster create ").append(nodes)
                .append(" --cluster-replicas 1 --cluster-yes && tail -f /dev/null");

        GenericContainer<?> container = startWithPorts(script.toString(), ports);
        Cluster topology = new Cluster(container, ports);
        awaitOutput("the cluster did not reach the ok state",
                () -> topology.node(ports[0], "cluster", "info"),
                output -> output.contains("cluster_state:ok") && output.contains("cluster_known_nodes:6"));
        for (int port : ports) {
            awaitOutput("node " + port + " does not know the cluster yet",
                    () -> topology.node(port, "cluster", "info"),
                    output -> output.contains("cluster_state:ok") && output.contains("cluster_slots_ok:16384"));
        }
        return topology;
    }

    /**
     * Address of the Docker host as the tests see it.
     *
     * @return host name or address
     */
    public static String host() {
        return DockerClientFactory.instance().dockerHostIpAddress();
    }

    private static GenericContainer<?> startWithPorts(String script, int[] ports) {
        requireLoopbackDockerHost();
        GenericContainer<?> container = new GenericContainer<>(RedisContainers.IMAGE)
                .withCommand("sh", "-c", script)
                .withStartupTimeout(Duration.ofSeconds(60));
        List<String> bindings = new ArrayList<>();
        for (int port : ports) {
            container.addExposedPort(port);
            bindings.add(port + ":" + port);
        }
        container.setPortBindings(bindings);
        container.start();
        return container;
    }

    /**
     * The nodes announce themselves as 127.0.0.1, so these topologies only work when Docker
     * publishes the ports on the tests' own machine.
     */
    private static void requireLoopbackDockerHost() {
        try {
            if (!InetAddress.getByName(host()).isLoopbackAddress()) {
                throw new IllegalStateException(
                        "Multi-node topologies need a local Docker; the host is " + host());
            }
        } catch (IOException e) {
            throw new IllegalStateException("Could not resolve the Docker host", e);
        }
    }

    public static String cli(GenericContainer<?> container, int port, String password, String... command) {
        List<String> arguments = new ArrayList<>(List.of("redis-cli", "-p", String.valueOf(port)));
        if (password != null) {
            arguments.addAll(List.of("--no-auth-warning", "-a", password));
        }
        arguments.addAll(List.of(command));
        try {
            return container.execInContainer(arguments.toArray(new String[0])).getStdout();
        } catch (IOException e) {
            throw new IllegalStateException("Could not run redis-cli", e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Interrupted while running redis-cli", e);
        }
    }

    private static void awaitOutput(
            String failure, java.util.function.Supplier<String> command, java.util.function.Predicate<String> ready) {
        String[] last = new String[1];
        boolean ok = LibraryApplication.await(() -> {
            last[0] = command.get();
            return ready.test(last[0]);
        }, Duration.ofSeconds(40));
        if (!ok) {
            throw new IllegalStateException(failure + ". Last output: " + last[0]);
        }
    }
}
