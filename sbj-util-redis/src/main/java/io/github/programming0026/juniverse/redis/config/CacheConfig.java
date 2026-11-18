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
 * Configuration that activates {@link EnableRedisLibrary}.
 *
 * <p>All beans are conditional: if the application defines one of the same type (or, in the
 * case of the template, with the name {@code redisTemplate}), the library uses that one and does not create
 * its own. The template and the connection factory use the same names as Spring Boot,
 * {@code redisTemplate} and {@code redisConnectionFactory}, so that its Redis autoconfiguration
 * does not create others. This class is imported through {@link RedisLibraryImportSelector}, which is what
 * makes the order in which the conditions are evaluated reliable.</p>
 *
 * @author Java Universe
 */
@Configuration
@EnableCaching
@EnableAspectJAutoProxy
@Slf4j
public class CacheConfig implements ImportAware, EnvironmentAware, BeanFactoryAware {

    /** How often the subscription to notifications is retried while Redis is not responding. */
    private static final Duration SUBSCRIPTION_RETRY_INTERVAL = Duration.ofSeconds(5);

    /** How often the cluster topology is queried again even when there are no errors. */
    private static final Duration CLUSTER_TOPOLOGY_REFRESH_PERIOD = Duration.ofSeconds(60);

    /**
     * User name attribute.
     */
    private static final String ATTRIBUTE_USER_NAME = "userName";

    /**
     * Attributes as they come in the annotation, unresolved.
     */
    private AnnotationAttributes attributes;

    /**
     * Attributes with placeholders resolved. Computed the first time they are used.
     */
    private AnnotationAttributes resolvedAttributes;

    /**
     * Environment used to resolve the {@code ${...}} placeholders.
     */
    private Environment environment;

    /** Circuit breaker shared by the three forms of cache; created on first use. */
    private RedisCircuitBreaker circuitBreaker;

    /** Bean factory, from which the application's base packages are obtained. */
    private BeanFactory beanFactory;

    /** Name of the class carrying {@link EnableRedisLibrary}. */
    private String importingClassName;

    /**
     * Receives the bean factory.
     *
     * @param beanFactory application bean factory
     */
    @Override
    public void setBeanFactory(BeanFactory beanFactory) {
        this.beanFactory = beanFactory;
    }

    /**
     * Application prefix for keys, locks and channels: the one in the annotation or,
     * if empty, {@code spring.application.name}.
     *
     * @return prefix without trailing separator; empty if there is none
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
     * Reads the TTL of each cache from the annotation.
     *
     * @return TTL per cache name
     */
    private Map<String, Duration> configuredTtls() {
        Map<String, Duration> ttls = new HashMap<>();
        for (AnnotationAttributes entry : attributes().getAnnotationArray("ttlEntries")) {
            if (entry != null) {
                String name = entry.getString("name");
                Long ttl = entry.getNumber("ttl");
                Validator.validateTTL(name, ttl);
                // Hand-built attribute maps may not include the unit.
                TimeUnit unit = entry.containsKey("unit") ? entry.getEnum("unit") : TimeUnit.MINUTES;
                ttls.put(name, Duration.of(ttl, unit.toChronoUnit()));
            }
        }
        return ttls;
    }

    /**
     * TTL of each cache in seconds, which is the unit of the {@code @Coalesce*} annotations.
     *
     * @return seconds per cache name; at least one
     */
    private Map<String, Long> configuredTtlSeconds() {
        Map<String, Long> seconds = new HashMap<>();
        configuredTtls().forEach((name, ttl) -> seconds.put(name, Math.max(1, ttl.toSeconds())));
        return seconds;
    }

    /**
     * Packages whose classes may be rebuilt from the name stored in Redis:
     * the application's base packages plus those given in the annotation.
     *
     * @return trusted packages
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
     * Creates the internal template of the coalescing cache. It reads and writes bytes without
     * transformation because the values are serialized by {@link CacheValueCodec} with the type
     * of each method. It is not registered as a bean so as not to interfere with the
     * application's templates.
     *
     * @param connectionFactory connection factory
     * @return template with string keys and byte values
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
     * Returns the {@link EnableRedisLibrary} attributes with placeholders resolved.
     * If there is no environment (for example, in unit tests), the values are used as they are.
     *
     * @return resolved attributes
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
     * Receives the Spring environment.
     *
     * @param environment application environment
     */
    @Override
    public void setEnvironment(Environment environment) {
        this.environment = environment;
        this.resolvedAttributes = null;
    }

    /**
     * Coalescing cache manager.
     *
     * @param connectionFactory connection factory
     * @param redisMessageListener message listener
     * @return CoalesceCacheManager
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
     * Returns the shared circuit breaker: a Redis failure detected by any of the
     * three forms of cache avoids the wait in the others. With {@code failOpen = false} there is no pause.
     *
     * @return shared circuit breaker
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
     * Returns the cache counters: the {@link CacheMetrics} bean if it exists (registered by
     * {@link CacheMetricsConfiguration} when Micrometer is available, or by the application),
     * and otherwise a no-op implementation.
     *
     * @return cache counters
     */
    private CacheMetrics cacheMetrics() {
        return beanFactory == null
                ? CacheMetrics.NOOP
                : beanFactory.getBeanProvider(CacheMetrics.class).getIfAvailable(() -> CacheMetrics.NOOP);
    }

    /**
     * Reads the coalescing settings from the annotation.
     *
     * @return coalescing settings
     */
    private CoalesceSettings coalesceSettings() {
        Long lockTimeout = attributes().getNumber("coalesceLockTimeout");
        Long waitTimeout = attributes().getNumber("coalesceWaitTimeout");
        Validator.validateCoalesceTimeouts(lockTimeout, waitTimeout);
        // Hand-built attribute maps may not include the pause.
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
     * Coalescing aspect.
     *
     * @param cacheManager cache manager
     * @return CoalesceCachingAspect
     */
    @Bean
    @ConditionalOnMissingBean
    public CoalesceCachingAspect coalesceCachingAspect(CoalesceCacheManager cacheManager) {
        return new CoalesceCachingAspect(cacheManager, environment, configuredTtlSeconds());
    }

    /**
     * Cache put aspect.
     *
     * @param cacheManager cache manager
     * @return CoalescePutAspect
     */
    @Bean
    @ConditionalOnMissingBean
    public CoalescePutAspect coalescePutAspect(CoalesceCacheManager cacheManager) {
        return new CoalescePutAspect(cacheManager, environment, configuredTtlSeconds());
    }

    /**
     * Cache eviction aspect.
     *
     * @param cacheManager cache manager
     * @return CoalesceEvictAspect
     */
    @Bean
    @ConditionalOnMissingBean
    public CoalesceEvictAspect coalesceEvictAspect(CoalesceCacheManager cacheManager) {
        return new CoalesceEvictAspect(cacheManager, environment);
    }

    /**
     * Redis message listener.
     *
     * @param connectionFactory connection factory
     * @return Redis message listener container
     */
    @Bean
    @ConditionalOnMissingBean(RedisMessageListenerContainer.class)
    public RedisMessageListenerContainer redisMessageListenerContainer(
            RedisConnectionFactory connectionFactory) {
        // With degradation enabled, Redis being unavailable at startup must not prevent
        // the application from starting: the subscription is retried until it succeeds.
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
     * Redis health checker.
     *
     * @param redisTemplate Redis template
     * @return Redis health checker
     */
    @Bean
    @ConditionalOnMissingBean
    public RedisHealthChecker redisHealthChecker(RedisTemplate<String, Object> redisTemplate) {
        return new RedisHealthChecker(redisTemplate);
    }

    /**
     * Cache service on top of Redis.
     *
     * @param redisTemplate Redis template
     * @return Redis cache service
     */
    @Bean
    @ConditionalOnMissingBean
    public RedisCacheService redisCacheService(RedisTemplate<String, Object> redisTemplate) {
        return new RedisCacheService(redisTemplate, keyPrefix(), configuredTtls(), circuitBreaker());
    }

    /**
     * Factory of the cache operation builder.
     *
     * @param redisCacheService Redis cache service
     * @return cache operation builder factory
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
     * Parses a server. The {@code host} attribute may also carry the port
     * ({@code host:port} or {@code [ipv6]:port}), which then takes precedence over the
     * {@code port} attribute. This way the port can come from a property:
     * {@code host = "${redis.host}:${redis.port}"}.
     *
     * @param host value of the {@code host} attribute, with placeholders already resolved
     * @param defaultPort value of the {@code port} attribute
     * @return server and port
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
            // A single ':' separates host and port; with several it is an IPv6 address without a port.
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
     * Creates the standalone configuration.
     *
     * @return Redis standalone configuration
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
     * Creates the cluster configuration.
     *
     * @return Redis cluster configuration
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
     * Creates the sentinel configuration.
     *
     * @return sentinel configuration
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

        // userName and pwd authenticate against the master and the replicas, as in the other modes.
        String userName = attributes().getString(ATTRIBUTE_USER_NAME);
        String pwd = attributes().getString("pwd");
        if (StringUtils.hasText(userName)) {
            sentinelConfig.setUsername(userName);
        }
        if (StringUtils.hasText(pwd)) {
            sentinelConfig.setPassword(pwd);
        }

        // The sentinels have their own authentication, independent from that of the data nodes.
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
     * Creates the Redis connection factory.
     *
     * @return Redis connection factory
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

        // connectionTimeout limits connection establishment (a socket option);
        // readTimeout limits the wait for the response to each command.
        // Keep-alive detects connections that a firewall or a load balancer silently dropped.
        // After 30 s without data, the connection is probed every 10 s; after 3 unanswered probes it
        // is considered dead and Lettuce reconnects. Where the system does not support these timings,
        // Lettuce uses basic keep-alive.
        SocketOptions socketOptions = SocketOptions.builder()
                .connectTimeout(Duration.ofMillis(connectionTimeout))
                .keepAlive(SocketOptions.KeepAliveOptions.builder()
                        .enable()
                        .idle(Duration.ofSeconds(30))
                        .interval(Duration.ofSeconds(10))
                        .count(3)
                        .build())
                .build();

        // With degradation enabled it is better to fail immediately while there is no connection,
        // instead of queueing commands until readTimeout expires: the request proceeds without cache.
        ClientOptions.DisconnectedBehavior disconnectedBehavior = attributes().getBoolean("failOpen")
                ? ClientOptions.DisconnectedBehavior.REJECT_COMMANDS
                : ClientOptions.DisconnectedBehavior.DEFAULT;

        ClientOptions clientOptions;
        if (mode == Mode.CLUSTER) {
            // Without topology refresh, after a failover or a slot move
            // the client would keep sending commands to the old nodes.
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
     * Creates the Redis template.
     *
     * @param <T> generic type
     * @param connectionFactory connection factory
     * @return Redis template
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
     * Gets the object mapper configuration.
     *
     * @return object mapper configuration
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
            // Falling back to the default configuration would change the format of what is stored
            // in Redis without anyone noticing: it is better not to start.
            throw new IllegalStateException(
                    "Cannot instantiate the ObjectMapperConfig " + configClass.getName()
                    + " declared in @EnableRedisLibrary(mapperConfig). "
                    + "It must be a concrete class with a public no-args constructor.", ex);
        }
    }

    /**
     * Cache manager.
     * @param connectionFactory connection factory
     * @return cache manager
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
        // Since Spring Data Redis 4, cache writes are asynchronous by default: an immediate
        // read may not see what was just stored and write errors are lost.
        // With immediate writes, put and evict complete before control is returned.
        RedisCacheWriter cacheWriter = RedisCacheWriter.create(
                connectionFactory, RedisCacheWriter.RedisCacheWriterConfigurer::immediateWrites);
        RedisCacheManager redisCacheManager = RedisCacheManager.builder(cacheWriter)
                .cacheDefaults(defaults)
                .withInitialCacheConfigurations(cacheConfiguration)
                .build();
        if (!attributes().getBoolean("failOpen")) {
            return redisCacheManager;
        }
        // Once wrapped, Spring no longer initializes it on its own.
        redisCacheManager.afterPropertiesSet();
        return new FailOpenCacheManager(redisCacheManager, circuitBreaker());
    }

    /**
     * Looks for {@link EnableRedisLibrary} in the superclasses and in composed annotations.
     *
     * @param className name of the configuration class
     * @return annotation attributes, or {@code null} if it is not present
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
     * Receives the import metadata.
     *
     * @param importMetadata metadata of the annotation that imports the configuration
     */
    @Override
    public void setImportMetadata(AnnotationMetadata importMetadata) {
        this.attributes = AnnotationAttributes.fromMap(
                importMetadata.getAnnotationAttributes(EnableRedisLibrary.class.getName()));
        this.importingClassName = importMetadata.getClassName();
        this.resolvedAttributes = null;
        if (this.attributes == null && this.importingClassName != null) {
            // The annotation may be on a superclass of the configuration class
            // or inside another annotation of the application's own.
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
