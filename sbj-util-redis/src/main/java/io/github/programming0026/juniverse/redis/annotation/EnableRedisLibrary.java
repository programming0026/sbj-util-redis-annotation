package io.github.programming0026.juniverse.redis.annotation;

import io.github.programming0026.juniverse.redis.util.Mode;
import io.github.programming0026.juniverse.redis.config.ObjectMapperConfig;
import io.github.programming0026.juniverse.redis.config.DefaultObjectMapperConfig;
import io.github.programming0026.juniverse.redis.config.RedisLibraryImportSelector;
import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import org.springframework.context.annotation.Import;

/**
 * @author Java Universe
 */
@Target({ElementType.TYPE})
@Retention(RetentionPolicy.RUNTIME)
@Documented
@Import(RedisLibraryImportSelector.class)
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
     * Nombre de usuario de los nodos de datos.
     * En modo SENTINEL se usa con el maestro y las réplicas, no con los sentinels
     * (para ellos está {@link #sentinelUserName()}).
     *
     * @return nombre de usuario; vacío si no hay autenticación por usuario
     */
    String userName() default "";

    /**
     * Contraseña de los nodos de datos. Indíquela siempre con un placeholder
     * ({@code "${redis.password}"}), nunca escrita en el código.
     * En modo SENTINEL se usa con el maestro y las réplicas, no con los sentinels
     * (para ellos está {@link #sentinelPwd()}).
     *
     * @return contraseña; vacía si no hay autenticación
     */
    String pwd() default "";

    /**
     * Nombre de usuario de los sentinels. Solo se usa en modo SENTINEL.
     *
     * @return nombre de usuario; vacío si los sentinels no piden usuario
     */
    String sentinelUserName() default "";

    /**
     * Contraseña de los sentinels. Solo se usa en modo SENTINEL.
     *
     * @return contraseña; vacía si los sentinels no piden autenticación
     */
    String sentinelPwd() default "";

    /**
     * Modo.
     *
     * @return modo
     */
    Mode mode() default Mode.STANDALONE;

    /**
     * Tiempo máximo para establecer la conexión con Redis.
     *
     * @return milisegundos; debe ser mayor que cero
     */
    long connectionTimeout() default 5000;

    /**
     * Tiempo máximo de espera de la respuesta de cada orden enviada a Redis.
     *
     * <p>Una orden de caché tarda normalmente milisegundos, así que este valor solo importa
     * cuando Redis no contesta: es lo que tarda en detectarse la avería y, por tanto, lo que
     * esperan las peticiones que llegan justo al empezar. Un valor alto alarga ese pico; uno
     * demasiado bajo puede dar por caídas órdenes lentas pero válidas (valores muy grandes,
     * red lenta).</p>
     *
     * @return milisegundos; debe ser mayor que cero
     */
    long readTimeout() default 1000;

    /**
     * De qué nodos se lee cuando hay réplicas (modos CLUSTER y SENTINEL). Admite los nombres de
     * Lettuce: {@code upstream}, {@code upstreamPreferred}, {@code replica},
     * {@code replicaPreferred}, {@code any}, {@code nearest}...
     *
     * <p>Vacío (valor por defecto): se lee siempre del maestro. Leer de réplicas reparte la
     * carga, pero una réplica puede ir unos milisegundos por detrás: una lectura inmediatamente
     * posterior a un guardado o a una invalidación puede ver el estado anterior.</p>
     *
     * @return preferencia de lectura; vacío para leer del maestro
     */
    String readFrom() default "";

    /**
     * Prefijo que se antepone a todas las claves, bloqueos y canales de esta aplicación.
     * Evita que dos aplicaciones que comparten Redis se pisen las claves o reciban los
     * avisos de la otra.
     *
     * <p>Si se deja vacío se usa {@code spring.application.name}; si tampoco está definido,
     * no hay prefijo. Todas las instancias de una misma aplicación deben usar el mismo.</p>
     *
     * @return prefijo, sin el separador {@code :} final
     */
    String keyPrefix() default "";

    /**
     * Paquetes cuyas clases se pueden reconstruir a partir del nombre de clase guardado en Redis.
     * Solo afecta a los valores que se guardan sin tipo conocido: {@code RedisTemplate},
     * {@code RedisCacheService}, {@code @Cacheable} de Spring y los métodos {@code @Coalesce*}
     * que devuelven {@code Object}.
     *
     * <p>Siempre se admiten los paquetes base de la aplicación (los de
     * {@code @SpringBootApplication}; si no los hay, el de la clase que lleva esta anotación)
     * y los tipos básicos de {@code java.lang}, {@code java.util}, {@code java.time} y
     * {@code java.math}. Aquí se añaden los demás, por ejemplo los de una librería compartida
     * de DTO. El valor {@code "*"} admite cualquier clase y no se recomienda.</p>
     *
     * <p>No se usa si se indica un {@link #mapperConfig()} propio.</p>
     *
     * @return paquetes adicionales de confianza
     */
    String[] trustedPackages() default {};

    /**
     * Qué hacer cuando Redis falla en las anotaciones {@code @Coalesce*}.
     * Con {@code true}, el fallo se registra y el método de negocio se ejecuta como si el valor
     * no estuviera en caché. Con {@code false}, el fallo se propaga a quien llama.
     *
     * <p>Mientras Redis no responde, las invalidaciones no se aplican; al recuperarse puede
     * haber datos antiguos hasta que caduquen.</p>
     *
     * @return {@code true} para seguir funcionando sin caché cuando Redis falla
     */
    boolean failOpen() default true;

    /**
     * Duración del bloqueo de una carga con coalescencia. La instancia que carga lo renueva
     * mientras sigue trabajando, así que un método más lento que este tiempo no se ejecuta a la
     * vez en otra instancia. En la práctica es lo que se tarda en detectar que la instancia que
     * cargaba ha muerto: pasado ese tiempo sin renovación, otra petición puede cargar el valor.
     *
     * @return milisegundos; debe ser mayor que cero
     */
    long coalesceLockTimeout() default 30000;

    /**
     * Tiempo que se deja de usar Redis tras un fallo de conexión o de tiempo de espera, para que
     * las peticiones no paguen {@link #readTimeout()} una por una mientras dura la avería. Pasado
     * ese tiempo, una sola petición vuelve a probar Redis. Solo se aplica con {@link #failOpen()};
     * cero lo desactiva.
     *
     * @return milisegundos; cero o mayor
     */
    long failurePause() default 5000;

    /**
     * Tiempo máximo que una petición espera el resultado que está cargando otra.
     * Si se agota, la petición falla con {@code CoalesceException}. Debe superar la duración
     * máxima de los métodos anotados con {@code @CoalesceCacheable}; un valor menor hace que las
     * peticiones fallen antes en lugar de acumularse tras una carga lenta.
     *
     * @return milisegundos; debe ser mayor que cero
     */
    long coalesceWaitTimeout() default 30000;

    /**
     * Clase que construye el {@code ObjectMapper} con el que se serializan los valores.
     * Debe tener un constructor público sin argumentos; si no se puede instanciar,
     * la aplicación no arranca.
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
     * Cifra la conexión con TLS. El certificado del servidor se valida contra el almacén de
     * confianza de la JVM ({@code javax.net.ssl.trustStore}); para otro almacén o para
     * autenticación con certificado de cliente, defina su propia {@code RedisConnectionFactory}.
     *
     * @return {@code true} para conectar con TLS
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
