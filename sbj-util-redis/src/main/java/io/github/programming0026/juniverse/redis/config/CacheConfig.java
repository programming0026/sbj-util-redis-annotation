package io.github.programming0026.juniverse.redis.config;

import io.github.programming0026.juniverse.redis.annotation.EnableRedisLibrary;
import io.github.programming0026.juniverse.redis.aspect.CoalesceCachingAspect;
import io.github.programming0026.juniverse.redis.aspect.CoalesceEvictAspect;
import io.github.programming0026.juniverse.redis.aspect.CoalescePutAspect;
import io.github.programming0026.juniverse.redis.dto.CoalesceSettings;
import io.github.programming0026.juniverse.redis.dto.HostsDto;
import io.github.programming0026.juniverse.redis.service.CacheMetrics;
import io.github.programming0026.juniverse.redis.service.CacheOperationBuilder;
import io.github.programming0026.juniverse.redis.service.CacheValueCodec;
import io.github.programming0026.juniverse.redis.service.CoalesceCacheManager;
import io.github.programming0026.juniverse.redis.service.RedisCacheService;
import io.github.programming0026.juniverse.redis.service.RedisCircuitBreaker;
import io.github.programming0026.juniverse.redis.service.RedisHealthChecker;
import io.github.programming0026.juniverse.redis.util.AnnotationPlaceholderResolver;
import io.github.programming0026.juniverse.redis.util.CacheDefaults;
import io.github.programming0026.juniverse.redis.enums.Mode;
import io.github.programming0026.juniverse.redis.util.Validator;
import io.lettuce.core.ClientOptions;
import io.lettuce.core.ReadFrom;
import io.lettuce.core.SocketOptions;
import io.lettuce.core.cluster.ClusterClientOptions;
import io.lettuce.core.cluster.ClusterTopologyRefreshOptions;
import org.springframework.core.task.SimpleAsyncTaskExecutor;
import org.springframework.beans.factory.BeanFactory;
import org.springframework.beans.factory.BeanFactoryAware;
import org.springframework.boot.autoconfigure.AutoConfigurationPackages;
import org.springframework.context.EnvironmentAware;
import org.springframework.data.redis.serializer.RedisSerializer;
import org.springframework.util.ClassUtils;
import org.springframework.core.env.Environment;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.cache.CacheManager;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.EnableAspectJAutoProxy;
import org.springframework.context.annotation.ImportAware;
import org.springframework.core.annotation.AnnotatedElementUtils;
import org.springframework.core.annotation.AnnotationAttributes;
import org.springframework.core.type.AnnotationMetadata;
import org.springframework.data.redis.cache.RedisCacheConfiguration;
import org.springframework.data.redis.cache.RedisCacheManager;
import org.springframework.data.redis.cache.RedisCacheWriter;
import org.springframework.data.redis.connection.RedisClusterConfiguration;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.connection.RedisSentinelConfiguration;
import org.springframework.data.redis.connection.RedisStandaloneConfiguration;
import org.springframework.data.redis.connection.lettuce.LettuceClientConfiguration;
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.serializer.RedisSerializationContext;
import org.springframework.data.redis.serializer.StringRedisSerializer;
import org.springframework.util.StringUtils;

import java.time.Duration;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.TimeUnit;
import org.springframework.data.redis.listener.RedisMessageListenerContainer;


/**
 * Configuración que activa {@link EnableRedisLibrary}.
 *
 * <p>Todos los beans son condicionales: si la aplicación define uno del mismo tipo (o, en el
 * caso de la plantilla, con el nombre {@code redisTemplate}), la librería usa ese y no crea el
 * suyo. La plantilla y la fábrica de conexiones llevan los mismos nombres que usa Spring Boot,
 * {@code redisTemplate} y {@code redisConnectionFactory}, para que su autoconfiguración de Redis
 * no cree otras. Esta clase se importa mediante {@link RedisLibraryImportSelector}, que es lo
 * que hace fiable el orden en que se evalúan las condiciones.</p>
 *
 * @author Java Universe
 */
@Configuration
@EnableCaching
@EnableAspectJAutoProxy
@Slf4j
public class CacheConfig implements ImportAware, EnvironmentAware, BeanFactoryAware {

    /** Cada cuánto se reintenta la suscripción a los avisos mientras Redis no responde. */
    private static final Duration SUBSCRIPTION_RETRY_INTERVAL = Duration.ofSeconds(5);

    /** Cada cuánto se vuelve a consultar la topología del clúster aunque no haya errores. */
    private static final Duration CLUSTER_TOPOLOGY_REFRESH_PERIOD = Duration.ofSeconds(60);

    /**
     * Atributo del nombre de usuario.
     */
    private static final String ATTRIBUTE_USER_NAME = "userName";

    /**
     * Atributos tal como vienen en la anotación, sin resolver.
     */
    private AnnotationAttributes attributes;

    /**
     * Atributos con los placeholders resueltos. Se calculan la primera vez que se usan.
     */
    private AnnotationAttributes resolvedAttributes;

    /**
     * Entorno con el que se resuelven los placeholders {@code ${...}}.
     */
    private Environment environment;

    /** Cortacircuitos compartido por las tres formas de caché; se crea al primer uso. */
    private RedisCircuitBreaker circuitBreaker;

    /** Fábrica de beans, de la que se obtienen los paquetes base de la aplicación. */
    private BeanFactory beanFactory;

    /** Nombre de la clase que lleva {@link EnableRedisLibrary}. */
    private String importingClassName;

    /**
     * Recibe la fábrica de beans.
     *
     * @param beanFactory fábrica de beans de la aplicación
     */
    @Override
    public void setBeanFactory(BeanFactory beanFactory) {
        this.beanFactory = beanFactory;
    }

    /**
     * Prefijo de la aplicación para claves, bloqueos y canales: el de la anotación o,
     * si está vacío, {@code spring.application.name}.
     *
     * @return prefijo sin separador final; vacío si no hay ninguno
     */
    private String keyPrefix() {
        String keyPrefix = attributes().getString("keyPrefix");
        if (!StringUtils.hasText(keyPrefix) && environment != null) {
            keyPrefix = environment.getProperty("spring.application.name", "");
        }
        keyPrefix = keyPrefix == null ? "" : keyPrefix.trim();
        Validator.validateKeyPrefix(keyPrefix);
        return keyPrefix;
    }

    /**
     * Lee de la anotación el TTL de cada caché.
     *
     * @return TTL por nombre de caché
     */
    private Map<String, Duration> configuredTtls() {
        Map<String, Duration> ttls = new HashMap<>();
        for (AnnotationAttributes entry : attributes().getAnnotationArray("ttlEntries")) {
            if (entry != null) {
                String name = entry.getString("name");
                Long ttl = entry.getNumber("ttl");
                Validator.validateTTL(name, ttl);
                // Los mapas de atributos construidos a mano pueden no traer la unidad.
                TimeUnit unit = entry.containsKey("unit") ? entry.getEnum("unit") : TimeUnit.MINUTES;
                ttls.put(name, Duration.of(ttl, unit.toChronoUnit()));
            }
        }
        return ttls;
    }

    /**
     * TTL de cada caché en segundos, que es la unidad de las anotaciones {@code @Coalesce*}.
     *
     * @return segundos por nombre de caché; como mínimo uno
     */
    private Map<String, Long> configuredTtlSeconds() {
        Map<String, Long> seconds = new HashMap<>();
        configuredTtls().forEach((name, ttl) -> seconds.put(name, Math.max(1, ttl.toSeconds())));
        return seconds;
    }

    /**
     * Paquetes cuyas clases se pueden reconstruir a partir del nombre guardado en Redis:
     * los paquetes base de la aplicación más los indicados en la anotación.
     *
     * @return paquetes de confianza
     */
    private List<String> trustedPackages() {
        List<String> packages = new ArrayList<>(List.of(attributes().getStringArray("trustedPackages")));
        if (beanFactory != null && AutoConfigurationPackages.has(beanFactory)) {
            packages.addAll(AutoConfigurationPackages.get(beanFactory));
        } else if (importingClassName != null) {
            packages.add(ClassUtils.getPackageName(importingClassName));
        }
        return packages;
    }

    /**
     * Crea la plantilla interna de la caché con coalescencia. Lee y escribe bytes sin
     * transformar porque los valores los serializa {@link CacheValueCodec} con el tipo
     * de cada método. No se registra como bean para no interferir con las plantillas
     * de la aplicación.
     *
     * @param connectionFactory fábrica de conexiones
     * @return plantilla de claves de texto y valores en bytes
     */
    private RedisTemplate<String, byte[]> rawTemplate(RedisConnectionFactory connectionFactory) {
        RedisTemplate<String, byte[]> template = new RedisTemplate<>();
        template.setConnectionFactory(connectionFactory);
        StringRedisSerializer stringSerializer = new StringRedisSerializer();
        template.setKeySerializer(stringSerializer);
        template.setHashKeySerializer(stringSerializer);
        template.setValueSerializer(RedisSerializer.byteArray());
        template.setHashValueSerializer(RedisSerializer.byteArray());
        template.afterPropertiesSet();
        return template;
    }

    /**
     * Devuelve los atributos de {@link EnableRedisLibrary} con los placeholders resueltos.
     * Si no hay entorno (por ejemplo, en pruebas unitarias), los valores se usan tal cual.
     *
     * @return atributos resueltos
     */
    private AnnotationAttributes attributes() {
        if (resolvedAttributes == null) {
            resolvedAttributes = environment == null
                    ? attributes
                    : AnnotationPlaceholderResolver.resolve(attributes, environment::resolveRequiredPlaceholders);
        }
        return resolvedAttributes;
    }

    /**
     * Recibe el entorno de Spring.
     *
     * @param environment entorno de la aplicación
     */
    @Override
    public void setEnvironment(Environment environment) {
        this.environment = environment;
        this.resolvedAttributes = null;
    }

    /**
     * Administrador de caché con coalescencia.
     * 
     * @param connectionFactory fábrica de conexiones
     * @param redisMessageListener escucha de mensaje
     * @return administrador CoalesceCacheManager
     */
    @Bean
    @ConditionalOnMissingBean
    public CoalesceCacheManager coalesceCacheManager(RedisConnectionFactory connectionFactory,
            RedisMessageListenerContainer redisMessageListener) {
        return new CoalesceCacheManager(
                rawTemplate(connectionFactory),
                redisMessageListener,
                new CacheValueCodec(getObjectMapperConfig().configure()),
                coalesceSettings(),
                keyPrefix(),
                cacheMetrics(),
                circuitBreaker());
    }

    /**
     * Devuelve el cortacircuitos compartido: una avería de Redis detectada por cualquiera de las
     * tres formas de caché evita la espera en las demás. Con {@code failOpen = false} no hay pausa.
     *
     * @return cortacircuitos compartido
     */
    private synchronized RedisCircuitBreaker circuitBreaker() {
        if (circuitBreaker == null) {
            circuitBreaker = new RedisCircuitBreaker(attributes().getBoolean("failOpen")
                    ? coalesceSettings().failurePause()
                    : Duration.ZERO);
        }
        return circuitBreaker;
    }

    /**
     * Devuelve los contadores de la caché: el bean {@link CacheMetrics} si existe (lo registra
     * {@link CacheMetricsConfiguration} cuando Micrometer está disponible, o la aplicación),
     * y si no, una implementación vacía.
     *
     * @return contadores de la caché
     */
    private CacheMetrics cacheMetrics() {
        return beanFactory == null
                ? CacheMetrics.NOOP
                : beanFactory.getBeanProvider(CacheMetrics.class).getIfAvailable(() -> CacheMetrics.NOOP);
    }

    /**
     * Lee de la anotación los ajustes de coalescencia.
     *
     * @return ajustes de coalescencia
     */
    private CoalesceSettings coalesceSettings() {
        Long lockTimeout = attributes().getNumber("coalesceLockTimeout");
        Long waitTimeout = attributes().getNumber("coalesceWaitTimeout");
        Validator.validateCoalesceTimeouts(lockTimeout, waitTimeout);
        // Los mapas de atributos construidos a mano pueden no traer la pausa.
        long failurePause = attributes().containsKey("failurePause")
                ? attributes().<Long>getNumber("failurePause")
                : CoalesceSettings.defaults().failurePause().toMillis();
        if (failurePause < 0) {
            throw new IllegalArgumentException(String.format("Invalid failurePause %d.", failurePause));
        }
        return new CoalesceSettings(
                attributes().getBoolean("failOpen"),
                Duration.ofMillis(lockTimeout),
                Duration.ofMillis(waitTimeout),
                Duration.ofMillis(failurePause));
    }

    /**
     * Aspecto de coalescencia.
     *
     * @param cacheManager administrador de caché
     * @return aspecto CoalesceCachingAspect
     */
    @Bean
    @ConditionalOnMissingBean
    public CoalesceCachingAspect coalesceCachingAspect(CoalesceCacheManager cacheManager) {
        return new CoalesceCachingAspect(cacheManager, environment, configuredTtlSeconds());
    }

    /**
     * Aspecto de guardado en caché.
     *
     * @param cacheManager administrador de caché
     * @return aspecto CoalescePutAspect
     */
    @Bean
    @ConditionalOnMissingBean
    public CoalescePutAspect coalescePutAspect(CoalesceCacheManager cacheManager) {
        return new CoalescePutAspect(cacheManager, environment, configuredTtlSeconds());
    }

    /**
     * Aspecto de invalidación de caché.
     *
     * @param cacheManager administrador de caché
     * @return aspecto CoalesceEvictAspect
     */
    @Bean
    @ConditionalOnMissingBean
    public CoalesceEvictAspect coalesceEvictAspect(CoalesceCacheManager cacheManager) {
        return new CoalesceEvictAspect(cacheManager, environment);
    }

    /**
     * Escucha de mensajes de Redis.
     * 
     * @param connectionFactory fábrica de conexiones
     * @return escucha de mensaje de Redis
     */
    @Bean
    @ConditionalOnMissingBean(RedisMessageListenerContainer.class)
    public RedisMessageListenerContainer redisMessageListenerContainer(
            RedisConnectionFactory connectionFactory) {
        // Con la degradación activa, que Redis no esté disponible al arrancar no debe impedir
        // que arranque la aplicación: la suscripción se reintenta hasta conseguirla.
        RedisMessageListenerContainer container = attributes().getBoolean("failOpen")
                ? new ResilientRedisMessageListenerContainer(SUBSCRIPTION_RETRY_INTERVAL)
                : new RedisMessageListenerContainer();
        container.setConnectionFactory(connectionFactory);
        SimpleAsyncTaskExecutor taskExecutor = new SimpleAsyncTaskExecutor("redis-coalesce-");
        taskExecutor.setVirtualThreads(true);
        container.setTaskExecutor(taskExecutor);
        return container;
    }
    
    /**
     * Verificador de salud de Redis.
     *
     * @param redisTemplate plantilla de Redis
     * @return verificador de salud de Redis
     */
    @Bean
    @ConditionalOnMissingBean
    public RedisHealthChecker redisHealthChecker(RedisTemplate<String, Object> redisTemplate) {
        return new RedisHealthChecker(redisTemplate);
    }

    /**
     * Servicio de caché sobre Redis.
     *
     * @param redisTemplate plantilla de Redis
     * @return servicio de caché de Redis
     */
    @Bean
    @ConditionalOnMissingBean
    public RedisCacheService redisCacheService(RedisTemplate<String, Object> redisTemplate) {
        return new RedisCacheService(redisTemplate, keyPrefix(), configuredTtls(), circuitBreaker());
    }

    /**
     * Fábrica del constructor de operaciones de caché.
     *
     * @param redisCacheService servicio de caché de Redis
     * @return fábrica de constructor de operación de caché
     */
    @Bean
    @ConditionalOnMissingBean
    public CacheOperationBuilder.Factory cacheOperationBuilderFactory(RedisCacheService redisCacheService) {
        return new CacheOperationBuilder.Factory(redisCacheService);
    }


    private List<HostsDto> getHostEntries() {
        List<HostsDto> list = new ArrayList<>();
        AnnotationAttributes[] hostArray = attributes().getAnnotationArray("hostEntries");
        for (AnnotationAttributes entry : hostArray) {
            if (entry != null) {
                list.add(parseHost(entry.getString("host"), entry.getNumber("port")));
            }
        }
        return list;
    }

    /**
     * Interpreta un servidor. El atributo {@code host} puede llevar también el puerto
     * ({@code host:puerto} o {@code [ipv6]:puerto}), que entonces manda sobre el atributo
     * {@code port}. Así el puerto puede venir de una propiedad:
     * {@code host = "${redis.host}:${redis.port}"}.
     *
     * @param host valor del atributo {@code host}, con los placeholders ya resueltos
     * @param defaultPort valor del atributo {@code port}
     * @return servidor y puerto
     */
    private static HostsDto parseHost(String host, int defaultPort) {
        String text = host == null ? "" : host.trim();
        String hostName = text;
        String portText = null;

        if (text.startsWith("[")) {
            int close = text.indexOf(']');
            if (close > 0) {
                hostName = text.substring(1, close);
                if (text.length() > close + 1 && text.charAt(close + 1) == ':') {
                    portText = text.substring(close + 2);
                }
            }
        } else if (text.indexOf(':') >= 0 && text.indexOf(':') == text.lastIndexOf(':')) {
            // Un solo ':' separa servidor y puerto; con varios es una dirección IPv6 sin puerto.
            hostName = text.substring(0, text.indexOf(':'));
            portText = text.substring(text.indexOf(':') + 1);
        }

        int port = defaultPort;
        if (portText != null) {
            try {
                port = Integer.parseInt(portText.trim());
            } catch (NumberFormatException e) {
                throw new IllegalArgumentException(
                        String.format("Invalid port '%s' in host entry '%s'", portText, host), e);
            }
        }
        return HostsDto.builder().hostName(hostName.trim()).port(port).build();
    }

    /**
     * Crea la configuración standalone.
     *
     * @return configuración standalone de Redis
     */

    private RedisStandaloneConfiguration createStandaloneConfig() {
        List<HostsDto> hosts = getHostEntries();
        Validator.validateStandaloneHosts(hosts);
        HostsDto host = hosts.getFirst();
        RedisStandaloneConfiguration config = new RedisStandaloneConfiguration();
        config.setHostName(Objects.requireNonNull(host.getHostName(), "host is required"));
        config.setPort(host.getPort());
        String userName = attributes().getString(ATTRIBUTE_USER_NAME);
        String pwd = attributes().getString("pwd");
        config.setDatabase(attributes().getNumber("database"));
        if (StringUtils.hasText(userName)) {
            config.setUsername(userName);
        }
        if (StringUtils.hasText(pwd)) {
            config.setPassword(pwd.toCharArray());
        }
        return config;
    }


    /**
     * Crea la configuración de clúster.
     *
     * @return configuración de clúster de Redis
     */
    private RedisClusterConfiguration createClusterConfig() {
        List<HostsDto> hosts = getHostEntries();
        Validator.validateClusterHosts(hosts);
        List<String> nodes = hosts.stream()
                .map(h -> String.format("%s:%d", h.getHostName(), h.getPort()))
                .toList();

        RedisClusterConfiguration config = new RedisClusterConfiguration(nodes);
        String userName = attributes().getString(ATTRIBUTE_USER_NAME);
        String pwd = attributes().getString("pwd");
        if (StringUtils.hasText(userName)) {
            config.setUsername(userName);
        }
        if (StringUtils.hasText(pwd)) {
            config.setPassword(pwd.toCharArray());
        }
        return config;
    }

    /**
     * Crea la configuración de sentinel.
     *
     * @return configuración de sentinel
     */

    private RedisSentinelConfiguration createSentinelConfig() {

        String sentinelMaster = attributes().getString("sentinelMaster");
        List<HostsDto> hosts = getHostEntries();
        Validator.validateSentinelHosts(hosts, sentinelMaster);

        RedisSentinelConfiguration sentinelConfig =
                new RedisSentinelConfiguration()
                        .master(sentinelMaster);

        for (HostsDto host : hosts) {
            sentinelConfig.sentinel(host.getHostName(), host.getPort());
        }
        sentinelConfig.setDatabase(attributes().getNumber("database"));

        // userName y pwd autentican contra el maestro y las réplicas, igual que en los otros modos.
        String userName = attributes().getString(ATTRIBUTE_USER_NAME);
        String pwd = attributes().getString("pwd");
        if (StringUtils.hasText(userName)) {
            sentinelConfig.setUsername(userName);
        }
        if (StringUtils.hasText(pwd)) {
            sentinelConfig.setPassword(pwd);
        }

        // Los sentinels tienen su propia autenticación, independiente de la de los datos.
        String sentinelUserName = attributes().getString("sentinelUserName");
        String sentinelPwd = attributes().getString("sentinelPwd");
        if (StringUtils.hasText(sentinelUserName)) {
            sentinelConfig.setSentinelUsername(sentinelUserName);
        }
        if (StringUtils.hasText(sentinelPwd)) {
            sentinelConfig.setSentinelPassword(sentinelPwd);
        }
        return sentinelConfig;
    }

    /**
     * Crea la fábrica de conexiones de Redis.
     *
     * @return fábrica de conexiones de Redis
     */
    @Bean(name = "redisConnectionFactory")
    @ConditionalOnMissingBean(RedisConnectionFactory.class)
    public RedisConnectionFactory createRedisConnectionFactory() {
        LettuceClientConfiguration clientConfiguration;
        boolean useSsl = attributes().getBoolean("useSsl");
        Long connectionTimeout = attributes().getNumber("connectionTimeout");
        Long readTimeout = attributes().getNumber("readTimeout");
        Mode mode = attributes().getEnum("mode");

        Validator.validateTimeout(connectionTimeout, readTimeout);

        // connectionTimeout limita el establecimiento de la conexión (opción del socket);
        // readTimeout limita la espera de la respuesta de cada orden.
        // El keep-alive detecta las conexiones que un cortafuegos o un balanceador cortó en silencio.
        // Sin datos durante 30 s, se sondea la conexión cada 10 s; tras 3 sondeos sin respuesta se
        // da por muerta y Lettuce reconecta. Donde el sistema no admite estos tiempos, Lettuce
        // usa el keep-alive básico.
        SocketOptions socketOptions = SocketOptions.builder()
                .connectTimeout(Duration.ofMillis(connectionTimeout))
                .keepAlive(SocketOptions.KeepAliveOptions.builder()
                        .enable()
                        .idle(Duration.ofSeconds(30))
                        .interval(Duration.ofSeconds(10))
                        .count(3)
                        .build())
                .build();

        // Con la degradación activa conviene fallar enseguida mientras no hay conexión, en lugar
        // de encolar las órdenes hasta que venza readTimeout: la petición sigue sin caché.
        ClientOptions.DisconnectedBehavior disconnectedBehavior = attributes().getBoolean("failOpen")
                ? ClientOptions.DisconnectedBehavior.REJECT_COMMANDS
                : ClientOptions.DisconnectedBehavior.DEFAULT;

        ClientOptions clientOptions;
        if (mode == Mode.CLUSTER) {
            // Sin refresco de topología, tras una conmutación por fallo o un movimiento de slots
            // el cliente seguiría enviando órdenes a los nodos antiguos.
            ClusterTopologyRefreshOptions topologyRefresh = ClusterTopologyRefreshOptions.builder()
                    .enableAllAdaptiveRefreshTriggers()
                    .enablePeriodicRefresh(CLUSTER_TOPOLOGY_REFRESH_PERIOD)
                    .build();
            clientOptions = ClusterClientOptions.builder()
                    .socketOptions(socketOptions)
                    .disconnectedBehavior(disconnectedBehavior)
                    .topologyRefreshOptions(topologyRefresh)
                    .build();
        } else {
            clientOptions = ClientOptions.builder()
                    .socketOptions(socketOptions)
                    .disconnectedBehavior(disconnectedBehavior)
                    .build();
        }

        LettuceClientConfiguration.LettuceClientConfigurationBuilder clientConfigurationBuilder =
                LettuceClientConfiguration
                        .builder()
                        .clientOptions(clientOptions)
                        .commandTimeout(Duration.ofMillis(readTimeout));
        String readFrom = attributes().getString("readFrom");
        if (StringUtils.hasText(readFrom)) {
            clientConfigurationBuilder.readFrom(ReadFrom.valueOf(readFrom.trim()));
        }
        clientConfiguration = useSsl
                ? clientConfigurationBuilder.useSsl().and().build()
                : clientConfigurationBuilder.build();

        return switch (mode) {
            case Mode.CLUSTER ->
                    new LettuceConnectionFactory(Objects.requireNonNull(createClusterConfig()), clientConfiguration);
            case Mode.STANDALONE ->
                    new LettuceConnectionFactory(Objects.requireNonNull(createStandaloneConfig()), clientConfiguration);
            case Mode.SENTINEL ->
                    new LettuceConnectionFactory(Objects.requireNonNull(createSentinelConfig()), clientConfiguration);
            default -> throw new IllegalArgumentException("Invalid mode");
        };

    }

    /**
     * Crea plantilla de Redis.
     *
     * @param <T> tipo genérico
     * @param connectionFactory fábrica de conexiones
     * @return plantilla de Redis
     */
    @Bean(name = "redisTemplate")
    @ConditionalOnMissingBean(name = "redisTemplate")
    @SuppressWarnings("unchecked")
    public <T> RedisTemplate<String, T> createRedisTemplate(RedisConnectionFactory connectionFactory) {
        RedisTemplate<String, T> template = new RedisTemplate<>();
        template.setConnectionFactory(connectionFactory);

        StringRedisSerializer stringSerializer = new StringRedisSerializer();
        template.setKeySerializer(stringSerializer);
        template.setHashKeySerializer(stringSerializer);

        ObjectMapperConfig mapperConfig = getObjectMapperConfig();
        CustomJacksonJsonRedisSerializer<T> jsonSerializer = new CustomJacksonJsonRedisSerializer<>(
                mapperConfig.configure(), (Class<T>) Object.class);
        template.setValueSerializer(jsonSerializer);
        template.setHashValueSerializer(jsonSerializer);

        template.afterPropertiesSet();

        return template;
    }

    /**
     * Obtiene configuración de conversor de objeto.
     *
     * @return configuración de conversor de objeto
     */
    private ObjectMapperConfig getObjectMapperConfig() {
        var configClass = attributes().getClass("mapperConfig");
        if (configClass == DefaultObjectMapperConfig.class) {
            log.debug("Using default ObjectMapperConfig");
            return new DefaultObjectMapperConfig(trustedPackages());
        }
        try {

            log.debug("Attempting to instantiate custom ObjectMapperConfig: {}",
                    configClass.getName());
            ObjectMapperConfig config = (ObjectMapperConfig) configClass
                    .getDeclaredConstructor()
                    .newInstance();
            log.debug("Successfully instantiated custom ObjectMapperConfig: {}",
                    configClass.getName());
            return config;
        } catch (ReflectiveOperationException ex) {
            // Seguir con la configuración predeterminada cambiaría el formato de lo que se guarda
            // en Redis sin que nadie lo notase: es mejor no arrancar.
            throw new IllegalStateException(
                    "Cannot instantiate the ObjectMapperConfig " + configClass.getName()
                    + " declared in @EnableRedisLibrary(mapperConfig). "
                    + "It must be a concrete class with a public no-args constructor.", ex);
        }
    }

    /**
     * Administrador de caché.
     * @param connectionFactory fábrica de conexiones
     * @return administrador de caché
     */
    @Bean
    @ConditionalOnMissingBean(CacheManager.class)
    public CacheManager cacheManager(RedisConnectionFactory connectionFactory) {
        ObjectMapperConfig mapperConfig = getObjectMapperConfig();

        RedisCacheConfiguration redisCacheConfiguration = RedisCacheConfiguration
                .defaultCacheConfig()
                .serializeKeysWith(
                        RedisSerializationContext.SerializationPair.fromSerializer(new StringRedisSerializer()))
                .serializeValuesWith(RedisSerializationContext.SerializationPair
                        .fromSerializer(new CustomJacksonJsonRedisSerializer<>(mapperConfig.configure(),
                                Object.class)));

        String namespace = CacheDefaults.namespace(keyPrefix());
        RedisCacheConfiguration defaults = namespace.isEmpty()
                ? redisCacheConfiguration
                : redisCacheConfiguration.prefixCacheNameWith(namespace);

        Map<String, RedisCacheConfiguration> cacheConfiguration = new HashMap<>();
        configuredTtls().forEach((cacheName, ttl) -> cacheConfiguration.put(cacheName, defaults.entryTtl(ttl)));
        // Desde Spring Data Redis 4 las escrituras de la caché son asíncronas por defecto: una
        // lectura inmediata puede no ver lo recién guardado y los errores de escritura se pierden.
        // Con escrituras inmediatas, guardar e invalidar terminan antes de devolver el control.
        RedisCacheWriter cacheWriter = RedisCacheWriter.create(
                connectionFactory, RedisCacheWriter.RedisCacheWriterConfigurer::immediateWrites);
        RedisCacheManager redisCacheManager = RedisCacheManager.builder(cacheWriter)
                .cacheDefaults(defaults)
                .withInitialCacheConfigurations(cacheConfiguration)
                .build();
        if (!attributes().getBoolean("failOpen")) {
            return redisCacheManager;
        }
        // Al envolverlo, Spring ya no lo inicializa por su cuenta.
        redisCacheManager.afterPropertiesSet();
        return new FailOpenCacheManager(redisCacheManager, circuitBreaker());
    }

    /**
     * Busca {@link EnableRedisLibrary} en las superclases y en las anotaciones compuestas.
     *
     * @param className nombre de la clase de configuración
     * @return atributos de la anotación, o {@code null} si no está
     */
    private static AnnotationAttributes findInHierarchy(String className) {
        try {
            Class<?> configClass = ClassUtils.forName(className, CacheConfig.class.getClassLoader());
            return AnnotatedElementUtils.findMergedAnnotationAttributes(
                    configClass, EnableRedisLibrary.class, false, true);
        } catch (ClassNotFoundException | LinkageError e) {
            return null;
        }
    }

    /**
     * Recibe los metadatos de la importación.
     *
     * @param importMetadata metadatos de la anotación que importa la configuración
     */
    @Override
    public void setImportMetadata(AnnotationMetadata importMetadata) {
        this.attributes = AnnotationAttributes.fromMap(
                importMetadata.getAnnotationAttributes(EnableRedisLibrary.class.getName()));
        this.importingClassName = importMetadata.getClassName();
        this.resolvedAttributes = null;
        if (this.attributes == null && this.importingClassName != null) {
            // La anotación puede estar en una superclase de la clase de configuración
            // o dentro de otra anotación propia de la aplicación.
            this.attributes = findInHierarchy(this.importingClassName);
        }
        if (this.attributes == null) {
            throw new IllegalStateException(
                    """
                    @EnableRedisLibrary annotation not found.
                    Please ensure your configuration class is annotated with @EnableRedisLibrary
                    """);
        }
    }
}
