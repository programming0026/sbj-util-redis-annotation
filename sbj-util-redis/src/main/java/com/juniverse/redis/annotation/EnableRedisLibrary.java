package com.juniverse.redis.annotation;

import com.juniverse.redis.util.Mode;
import com.juniverse.redis.config.ObjectMapperConfig;
import com.juniverse.redis.config.DefaultObjectMapperConfig;
import com.juniverse.redis.config.CacheConfig;
import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import org.springframework.context.annotation.Import;

/**
 * @author ajuar
 */
@Target({ElementType.TYPE})
@Retention(RetentionPolicy.RUNTIME)
@Documented
@Import(CacheConfig.class)
public @interface EnableRedisLibrary {

    /**
     * Base de datos.
     * Solo para los modos sentinel y standalone
     *
     * @return base de datos
     */
    int database() default 0;
    /**
     * Servidores.
     *
     * @return servidores configurados
     */
    HostEntry[] hostEntries() default {};

    /**
     * Nombre de usuario.
     *
     * @return cadena
     */
    String userName() default "";

    /**
     * Contraseña.
     *
     * @return cadena
     */
    String pwd() default "";

    /**
     * Modo.
     *
     * @return modo
     */
    Mode mode() default Mode.STANDALONE;

    /**
     * Tiempo de espera de conexión.
     *
     * @return valor entero largo
     */
    long connectionTimeout() default 5000;

    /**
     * Lee tiempo de espera.
     *
     * @return valor entero largo
     */
    long readTimeout() default 3000;

    /**
     * Manejador de errores.
     *
     * @return clase de configuración del ObjectMapper
     */
    Class<? extends ObjectMapperConfig> mapperConfig() default DefaultObjectMapperConfig.class;


    /**
     * Entradas de TTL por caché.
     *
     * @return entradas de TTL
     */
    TTLEntry[] ttlEntries() default {};

    /**
     * Usar SSL.
     *
     * @return {@code true} si la operación tuvo éxito
     */
    boolean useSsl() default false;

    /**
     * Nombre del maestro de sentinel.
     * Obligatorio cuando el modo es SENTINEL.
     *
     * @return nombre del maestro de sentinel
     */
    String sentinelMaster() default "";

}
