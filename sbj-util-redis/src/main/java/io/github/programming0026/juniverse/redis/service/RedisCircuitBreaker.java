package io.github.programming0026.juniverse.redis.service;

import io.lettuce.core.RedisCommandExecutionException;
import io.lettuce.core.RedisException;
import java.time.Duration;
import java.util.concurrent.atomic.AtomicLong;

import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.dao.QueryTimeoutException;

/**
 * Deja de usar Redis durante un tiempo después de un fallo de conexión.
 *
 * <p>Cuando Redis no contesta, cada operación espera hasta agotar {@code readTimeout}. Sin esta
 * pausa, cada petición pagaría esa espera una o varias veces mientras dure la avería. Tras un
 * fallo de conexión o de tiempo de espera, las operaciones de caché se saltan Redis durante
 * {@link #getPause()} y se comportan como si la caché estuviera vacía.</p>
 *
 * <p>Al acabar la pausa, solo una operación vuelve a probar Redis; las demás siguen sin usarlo
 * mientras tanto. Si la prueba sale bien ({@link #recordSuccess()}), Redis vuelve a usarse para
 * todas; si falla, empieza otra pausa. Así, mientras dure la avería, cada pausa cuesta como
 * mucho una espera, por mucho tráfico que haya.</p>
 *
 * <p>Una misma instancia la comparten las anotaciones {@code @Coalesce*}, {@code RedisCacheService}
 * y el {@code @Cacheable} de Spring, así que la avería que detecta una evita la espera en las demás.
 * Los errores de una orden concreta (por ejemplo, una respuesta de error del servidor) no abren
 * la pausa, porque no indican que Redis esté caído.</p>
 *
 * @author Java Universe
 */
public class RedisCircuitBreaker {

    /** Duración de la pausa tras un fallo de conexión. */
    private final Duration pause;

    /**
     * Instante ({@link System#nanoTime()}) hasta el que no se usa Redis; 0 si no hay pausa.
     * Pasado ese instante sin haberse cerrado, la pausa sigue vigente para todos menos para la
     * operación que prueba Redis.
     */
    private final AtomicLong pausedUntilNanos = new AtomicLong();

    /**
     * Crea el cortacircuitos.
     *
     * @param pause tiempo que se deja de usar Redis tras un fallo de conexión; cero lo desactiva
     */
    public RedisCircuitBreaker(Duration pause) {
        if (pause == null || pause.isNegative()) {
            throw new IllegalArgumentException("pause cannot be null or negative");
        }
        this.pause = pause;
    }

    /**
     * Devuelve la duración de la pausa.
     *
     * @return duración de la pausa
     */
    public Duration getPause() {
        return pause;
    }

    /**
     * Indica si hay que saltarse Redis porque hubo un fallo de conexión hace poco.
     *
     * <p>Al acabar la pausa devuelve {@code false} a una sola llamada, que hace de prueba, y
     * {@code true} a las demás hasta que esa prueba registre su resultado. Si la prueba no
     * registra nada (por ejemplo, porque su hilo se interrumpe), otra llamada prueba pasada
     * una nueva pausa.</p>
     *
     * @return {@code true} si la operación no debe llegar a Redis
     */
    public boolean isOpen() {
        long until = pausedUntilNanos.get();
        if (until == 0) {
            return false;
        }
        long now = System.nanoTime();
        if (now - until < 0) {
            return true;
        }
        // La pausa acabó: la primera llamada que consigue reprogramarla es la que prueba Redis.
        long nextProbe = now + pause.toNanos();
        return !pausedUntilNanos.compareAndSet(until, nextProbe == 0 ? 1 : nextProbe);
    }

    /**
     * Registra que una operación contra Redis terminó bien: si había pausa, se cierra y Redis
     * vuelve a usarse para todos.
     */
    public void recordSuccess() {
        if (pausedUntilNanos.get() != 0) {
            pausedUntilNanos.set(0);
        }
    }

    /**
     * Registra un fallo de una operación contra Redis. Si es un fallo de conexión o de tiempo
     * de espera, abre la pausa.
     *
     * @param failure excepción lanzada por la operación
     * @return {@code true} si el fallo abrió la pausa
     */
    public boolean recordFailure(Throwable failure) {
        if (pause.isZero() || !isConnectionFailure(failure)) {
            return false;
        }
        long until = System.nanoTime() + pause.toNanos();
        pausedUntilNanos.set(until == 0 ? 1 : until);
        return true;
    }

    /**
     * Indica si el fallo significa que no se llega a Redis (conexión caída, rechazada o sin
     * respuesta), a diferencia de un error de una orden concreta. Revisa toda la cadena de
     * causas, porque según el camino la excepción llega traducida por Spring o tal cual
     * la lanza Lettuce.
     *
     * @param failure excepción lanzada por la operación
     * @return {@code true} si es un fallo de conexión o de tiempo de espera
     */
    public static boolean isConnectionFailure(Throwable failure) {
        for (Throwable current = failure; current != null; current = current.getCause()) {
            if (current instanceof RedisCommandExecutionException) {
                return false;
            }
            if (current instanceof DataAccessResourceFailureException
                    || current instanceof QueryTimeoutException
                    || current instanceof RedisException) {
                return true;
            }
            if (current.getCause() == current) {
                break;
            }
        }
        return false;
    }
}
