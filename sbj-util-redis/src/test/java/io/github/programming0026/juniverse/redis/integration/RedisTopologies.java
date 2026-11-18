package io.github.programming0026.juniverse.redis.integration;

import java.io.IOException;
import java.net.InetAddress;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.StringJoiner;

import org.testcontainers.DockerClientFactory;
import org.testcontainers.containers.GenericContainer;

/**
 * Arranca en Docker las topologías de Redis de varios nodos: Sentinel y clúster con réplicas.
 *
 * <p>Todos los nodos de una topología corren en un mismo contenedor, cada uno en su puerto, y
 * esos puertos se publican con el mismo número en el host. Así las direcciones que los nodos
 * se anuncian entre sí y a los clientes ({@code 127.0.0.1:puerto}) valen tanto dentro del
 * contenedor como desde las pruebas.</p>
 *
 * @author Java Universe
 */
final class RedisTopologies {

    /** Contraseña de los nodos de datos en la topología Sentinel. */
    static final String DATA_PASSWORD = "clave-de-datos";

    /** Contraseña de los sentinels. */
    static final String SENTINEL_PASSWORD = "clave-de-centinelas";

    /** Nombre del maestro vigilado por los sentinels. */
    static final String MASTER_NAME = "mymaster";

    private RedisTopologies() {
    }

    /**
     * Maestro, réplica y tres sentinels.
     *
     * @param container contenedor que los aloja
     * @param masterPort puerto del maestro inicial
     * @param replicaPort puerto de la réplica inicial
     * @param sentinelPorts puertos de los sentinels
     */
    record Sentinel(GenericContainer<?> container, int masterPort, int replicaPort, int[] sentinelPorts) {

        /**
         * Ejecuta redis-cli contra un nodo de datos.
         *
         * @param port puerto del nodo
         * @param command orden y argumentos
         * @return salida de la orden
         */
        String data(int port, String... command) {
            return cli(container, port, DATA_PASSWORD, command);
        }

        /**
         * Ejecuta redis-cli contra el primer sentinel.
         *
         * @param command orden y argumentos
         * @return salida de la orden
         */
        String sentinel(String... command) {
            return cli(container, sentinelPorts[0], SENTINEL_PASSWORD, command);
        }
    }

    /**
     * Clúster de tres maestros y tres réplicas.
     *
     * @param container contenedor que los aloja
     * @param ports puertos de los seis nodos
     */
    record Cluster(GenericContainer<?> container, int[] ports) {

        /**
         * Ejecuta redis-cli contra un nodo.
         *
         * @param port puerto del nodo
         * @param command orden y argumentos
         * @return salida de la orden
         */
        String node(int port, String... command) {
            return cli(container, port, null, command);
        }
    }

    /**
     * Arranca la topología Sentinel con autenticación en los datos y en los sentinels.
     * Los sentinels dan por caído al maestro tras 2 segundos sin respuesta.
     *
     * @return topología lista para usar
     */
    static Sentinel startSentinel() {
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
        awaitOutput("los sentinels no se pusieron de acuerdo",
                () -> topology.sentinel("sentinel", "master", MASTER_NAME),
                output -> output.contains("num-other-sentinels\n2") && output.contains("num-slaves\n1"));
        return topology;
    }

    /**
     * Arranca un clúster de tres maestros con una réplica cada uno.
     * Un nodo se da por caído tras 2 segundos sin respuesta.
     *
     * @return clúster listo para usar
     */
    static Cluster startCluster() {
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
        awaitOutput("el clúster no llegó al estado ok",
                () -> topology.node(ports[0], "cluster", "info"),
                output -> output.contains("cluster_state:ok") && output.contains("cluster_known_nodes:6"));
        // Todos los nodos deben conocer ya el reparto de slots antes de que se conecte un cliente.
        for (int port : ports) {
            awaitOutput("el nodo " + port + " no conoce todavía el clúster",
                    () -> topology.node(port, "cluster", "info"),
                    output -> output.contains("cluster_state:ok") && output.contains("cluster_slots_ok:16384"));
        }
        return topology;
    }

    /**
     * Dirección del host de Docker tal como la ven las pruebas.
     *
     * @return nombre o dirección del host
     */
    static String host() {
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
     * Los nodos se anuncian como 127.0.0.1, así que estas topologías solo sirven cuando Docker
     * publica los puertos en la propia máquina de las pruebas.
     */
    private static void requireLoopbackDockerHost() {
        try {
            if (!InetAddress.getByName(host()).isLoopbackAddress()) {
                throw new IllegalStateException(
                        "Las topologías de varios nodos necesitan un Docker local; el host es " + host());
            }
        } catch (IOException e) {
            throw new IllegalStateException("No se pudo resolver el host de Docker", e);
        }
    }

    private static String cli(GenericContainer<?> container, int port, String password, String... command) {
        List<String> arguments = new ArrayList<>(List.of("redis-cli", "-p", String.valueOf(port)));
        if (password != null) {
            arguments.addAll(List.of("--no-auth-warning", "-a", password));
        }
        arguments.addAll(List.of(command));
        try {
            return container.execInContainer(arguments.toArray(new String[0])).getStdout();
        } catch (IOException e) {
            throw new IllegalStateException("No se pudo ejecutar redis-cli", e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Interrumpido al ejecutar redis-cli", e);
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
            throw new IllegalStateException(failure + ". Última salida: " + last[0]);
        }
    }
}
