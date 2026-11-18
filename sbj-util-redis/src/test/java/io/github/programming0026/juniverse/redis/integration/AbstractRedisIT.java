package io.github.programming0026.juniverse.redis.integration;

import io.github.programming0026.juniverse.redis.config.CoalesceSettings;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import org.junit.jupiter.api.AfterEach;
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory;
import org.testcontainers.junit.jupiter.Testcontainers;

/**
 * Base de las pruebas de integración contra un Redis real.
 * Si no hay Docker disponible, las pruebas se omiten en lugar de fallar.
 *
 * <p>Las pruebas marcadas con {@code @Disabled} reproducen fallos descritos en
 * {@code PLAN_MEJORAS.md}: afirman el comportamiento correcto y hoy fallan. Se activan
 * al corregir cada hallazgo. Para ejecutarlas y ver el fallo:</p>
 * <pre>{@code
 * mvn verify -Djunit.jupiter.conditions.deactivate=org.junit.*DisabledCondition
 * }</pre>
 *
 * @author Java Universe
 */
@Testcontainers(disabledWithoutDocker = true)
abstract class AbstractRedisIT {

    /** Instancias creadas por la prueba en curso. */
    private final List<RedisInstance> instances = new ArrayList<>();

    /** Ejecutores creados por la prueba en curso. */
    private final List<ExecutorService> executors = new ArrayList<>();

    /**
     * Crea la fábrica de conexiones del Redis contra el que corre la prueba.
     * Por defecto es el contenedor standalone compartido.
     *
     * @return fábrica de conexiones sin inicializar
     */
    protected LettuceConnectionFactory connectionFactory() {
        return RedisContainers.standaloneFactory();
    }

    /**
     * Crea una instancia de aplicación nueva contra el Redis de la prueba.
     *
     * @return instancia lista para usar
     */
    protected RedisInstance newInstance() {
        return register(new RedisInstance(connectionFactory()));
    }

    /**
     * Crea una instancia de aplicación con ajustes de coalescencia propios.
     *
     * @param settings ajustes de coalescencia
     * @return instancia lista para usar
     */
    protected RedisInstance newInstance(CoalesceSettings settings) {
        return register(new RedisInstance(connectionFactory(), settings));
    }

    /**
     * Registra una instancia para cerrarla al terminar la prueba.
     *
     * @param instance instancia creada por la prueba
     * @return la misma instancia
     */
    protected RedisInstance register(RedisInstance instance) {
        instances.add(instance);
        return instance;
    }

    /**
     * Lanza todas las tareas a la vez, cada una en su hilo.
     *
     * @param <T> tipo del resultado
     * @param tasks tareas que deben empezar al mismo tiempo
     * @return futuros en el mismo orden que las tareas
     */
    protected <T> List<Future<T>> runTogether(List<Callable<T>> tasks) {
        ExecutorService executor = Executors.newFixedThreadPool(tasks.size());
        executors.add(executor);
        CountDownLatch start = new CountDownLatch(1);
        List<Future<T>> futures = new ArrayList<>();
        for (Callable<T> task : tasks) {
            futures.add(executor.submit(() -> {
                start.await();
                return task.call();
            }));
        }
        start.countDown();
        return futures;
    }

    /**
     * Lanza una tarea en segundo plano.
     *
     * @param <T> tipo del resultado
     * @param task tarea
     * @return futuro con el resultado
     */
    protected <T> Future<T> runAsync(Callable<T> task) {
        ExecutorService executor = Executors.newSingleThreadExecutor();
        executors.add(executor);
        return executor.submit(task);
    }

    /**
     * Vacía Redis y cierra lo que abrió la prueba.
     */
    @AfterEach
    void cleanUp() {
        executors.forEach(ExecutorService::shutdownNow);
        executors.clear();
        if (shouldFlush() && !instances.isEmpty()) {
            instances.getFirst().flushAll();
        }
        instances.forEach(RedisInstance::close);
        instances.clear();
    }

    /**
     * Indica si hay que vaciar Redis al terminar cada prueba.
     *
     * @return {@code true} por defecto
     */
    protected boolean shouldFlush() {
        return true;
    }
}
