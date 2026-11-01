package com.juniverse.redis.example.util;

import lombok.extern.slf4j.Slf4j;

/**
 * Clase de utilidades para operaciones comunes
 *
 * @author linux
 */
@Slf4j
public final class Utility {

    /** Propiedad del sistema que reemplaza el retraso simulado en milisegundos (las pruebas unitarias la ponen en 0). */
    public static final String DELAY_PROPERTY = "example.slow-service-millis";

    private static final long DEFAULT_DELAY_MILLIS = 3000L;

    /**
     * Constructor privado para evitar la instanciación
     */
    private Utility() {
    }

    /**
     * Simula un servicio lento esperando 3 segundos (o el valor de la
     * propiedad del sistema {@value #DELAY_PROPERTY}, en milisegundos)
     * Se usa para mostrar las ventajas de la caché
     */
    public static void simulateSlowService() {
        try {
            Thread.sleep(Long.getLong(DELAY_PROPERTY, DEFAULT_DELAY_MILLIS));
        } catch (InterruptedException e) {
            log.error("Error: ", e);
            Thread.currentThread().interrupt();
        }
    }
}
