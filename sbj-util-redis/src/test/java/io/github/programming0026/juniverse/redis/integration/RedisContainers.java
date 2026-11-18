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
 * Contenedores de Redis compartidos por las pruebas de integración.
 * Cada contenedor se arranca una sola vez, la primera vez que se pide, y Testcontainers
 * lo elimina al terminar la JVM.
 *
 * @author Java Universe
 */
final class RedisContainers {

    /** Imagen de Redis usada en todas las pruebas. */
    static final DockerImageName IMAGE = DockerImageName.parse("redis:7-alpine");

    /** Puerto de Redis dentro del contenedor standalone. */
    private static final int REDIS_PORT = 6379;

    /** Tiempo máximo de cada orden; corto para que un fallo no alargue la prueba. */
    private static final Duration COMMAND_TIMEOUT = Duration.ofSeconds(5);

    /** Contenedor standalone compartido. */
    private static GenericContainer<?> standalone;

    /** Contenedor de clúster (un solo nodo con todos los slots) compartido. */
    private static GenericContainer<?> cluster;

    /** Puerto del nodo del clúster; es el mismo dentro y fuera del contenedor. */
    private static int clusterPort;

    private RedisContainers() {
    }

    /**
     * Crea un contenedor standalone nuevo, sin arrancar.
     * Sirve para las pruebas que necesitan detener Redis sin afectar a las demás.
     *
     * @return contenedor sin arrancar
     */
    static GenericContainer<?> newStandalone() {
        return new GenericContainer<>(IMAGE).withExposedPorts(REDIS_PORT);
    }

    /**
     * Crea una fábrica de conexiones hacia el contenedor standalone compartido.
     *
     * @return fábrica de conexiones sin inicializar
     */
    static synchronized LettuceConnectionFactory standaloneFactory() {
        if (standalone == null) {
            standalone = newStandalone();
            standalone.start();
        }
        return standaloneFactory(standalone);
    }

    /**
     * Crea una fábrica de conexiones hacia un contenedor standalone concreto.
     *
     * @param container contenedor ya arrancado
     * @return fábrica de conexiones sin inicializar
     */
    static LettuceConnectionFactory standaloneFactory(GenericContainer<?> container) {
        return standaloneFactory(container, COMMAND_TIMEOUT);
    }

    /**
     * Crea una fábrica de conexiones hacia un contenedor standalone concreto.
     *
     * @param container contenedor ya arrancado
     * @param commandTimeout tiempo máximo de cada orden
     * @return fábrica de conexiones sin inicializar
     */
    static LettuceConnectionFactory standaloneFactory(GenericContainer<?> container, Duration commandTimeout) {
        RedisStandaloneConfiguration config = new RedisStandaloneConfiguration(
                container.getHost(), container.getMappedPort(REDIS_PORT));
        return new LettuceConnectionFactory(config, clientConfiguration(commandTimeout));
    }

    /**
     * Crea una fábrica de conexiones hacia el clúster compartido.
     *
     * @return fábrica de conexiones sin inicializar
     */
    static synchronized LettuceConnectionFactory clusterFactory() {
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
     * Arranca un clúster de un solo nodo que posee los 16384 slots.
     * Un nodo de clúster anuncia su propia dirección a los clientes, así que el puerto
     * debe ser el mismo dentro y fuera del contenedor y la dirección anunciada debe ser
     * la del host de Docker.
     *
     * @return contenedor arrancado y con el clúster en estado correcto
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
            throw new IllegalStateException("No se pudo arrancar el clúster de Redis", e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Interrumpido al arrancar el clúster de Redis", e);
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
        throw new IllegalStateException("El clúster de Redis no llegó al estado ok");
    }

    /**
     * Busca un puerto libre para el nodo del clúster.
     * Redis usa además el puerto + 10000 para el bus del clúster, así que el puerto
     * elegido no puede pasar de 55535.
     *
     * @return puerto libre
     * @throws IOException si no se encuentra ninguno
     */
    private static int findFreePort() throws IOException {
        return freePorts(1)[0];
    }

    /**
     * Busca varios puertos libres distintos, todos por debajo de 55535 para que el puerto
     * + 10000 del bus del clúster siga siendo válido.
     *
     * @param count cuántos puertos hacen falta
     * @return puertos libres
     */
    static int[] freePorts(int count) {
        java.util.Set<Integer> ports = new java.util.LinkedHashSet<>();
        for (int attempt = 0; attempt < 500 && ports.size() < count; attempt++) {
            int candidate = ThreadLocalRandom.current().nextInt(20000, 50000);
            try (ServerSocket socket = new ServerSocket(candidate)) {
                ports.add(socket.getLocalPort());
            } catch (IOException e) {
                // Ocupado: se prueba otro.
            }
        }
        if (ports.size() < count) {
            throw new IllegalStateException("No se encontraron " + count + " puertos libres");
        }
        return ports.stream().mapToInt(Integer::intValue).toArray();
    }
}
