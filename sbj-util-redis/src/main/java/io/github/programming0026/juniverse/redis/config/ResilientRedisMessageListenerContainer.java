package io.github.programming0026.juniverse.redis.config;

import java.time.Duration;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.listener.RedisMessageListenerContainer;

/**
 * Escucha de mensajes que no impide arrancar la aplicación si Redis no está disponible.
 *
 * <p>El contenedor de Spring Data Redis lanza una excepción al arrancar si no consigue
 * suscribirse, y con ello detiene el arranque de toda la aplicación. Este registra el fallo,
 * deja que la aplicación arranque y vuelve a intentar la suscripción periódicamente hasta
 * conseguirla.</p>
 *
 * <p>Mientras no hay suscripción, la coalescencia entre instancias sigue siendo correcta:
 * quien espera el resultado de otra instancia consulta la caché periódicamente y no depende
 * de recibir el aviso.</p>
 *
 * @author Java Universe
 */
@Slf4j
public class ResilientRedisMessageListenerContainer extends RedisMessageListenerContainer {

    /** Cada cuánto se reintenta la suscripción. */
    private final Duration retryInterval;

    /** Hilo que reintenta la suscripción. */
    private final ScheduledThreadPoolExecutor retryExecutor;

    /** Indica si el contenedor fue detenido o destruido, para no seguir reintentando. */
    private volatile boolean stopRequested;

    /**
     * Crea la escucha.
     *
     * @param retryInterval cada cuánto se reintenta la suscripción mientras Redis no responde
     */
    public ResilientRedisMessageListenerContainer(Duration retryInterval) {
        this.retryInterval = retryInterval;
        this.retryExecutor = new ScheduledThreadPoolExecutor(1, runnable -> {
            Thread thread = new Thread(runnable, "redis-coalesce-subscribe-retry");
            thread.setDaemon(true);
            return thread;
        });
    }

    /**
     * Arranca la escucha. Si Redis no responde, lo registra y lo reintenta más tarde
     * en lugar de fallar.
     */
    @Override
    public void start() {
        stopRequested = false;
        tryToStart();
    }

    private void tryToStart() {
        if (stopRequested) {
            return;
        }
        try {
            super.start();
            log.debug("Subscribed to Redis coalesce signals");
        } catch (RuntimeException e) {
            log.warn("Could not subscribe to Redis ({}); the application continues without cross-instance "
                    + "signals and will retry every {} ms", e.toString(), retryInterval.toMillis());
            resetAfterFailedStart();
            if (!stopRequested && !retryExecutor.isShutdown()) {
                retryExecutor.schedule(this::tryToStart, retryInterval.toMillis(), TimeUnit.MILLISECONDS);
            }
        }
    }

    /**
     * Devuelve el contenedor al estado «detenido» para que el siguiente intento empiece de cero.
     */
    private void resetAfterFailedStart() {
        try {
            super.stop();
        } catch (RuntimeException e) {
            log.debug("Ignoring failure while resetting the Redis listener container: {}", e.toString());
        }
    }

    /**
     * Detiene la escucha y deja de reintentar.
     */
    @Override
    public void stop() {
        stopRequested = true;
        super.stop();
    }

    /**
     * Libera los recursos al cerrar el contexto.
     *
     * @throws Exception si falla el cierre del contenedor
     */
    @Override
    public void destroy() throws Exception {
        stopRequested = true;
        retryExecutor.shutdownNow();
        super.destroy();
    }
}
