package com.juniverse.redis.config;

import com.juniverse.redis.annotation.EnableRedisLibrary;
import com.juniverse.redis.aspect.CoalesceCachingAspect;
import com.juniverse.redis.dto.HostsDto;
import com.juniverse.redis.service.CacheOperationBuilder;
import com.juniverse.redis.service.CoalesceCacheManager;
import com.juniverse.redis.service.RedisCacheService;
import com.juniverse.redis.service.RedisHealthChecker;
import com.juniverse.redis.util.Mode;
import com.juniverse.redis.util.Validator;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.cache.CacheManager;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.EnableAspectJAutoProxy;
import org.springframework.context.annotation.ImportAware;
import org.springframework.core.annotation.AnnotationAttributes;
import org.springframework.core.type.AnnotationMetadata;
import org.springframework.data.redis.cache.RedisCacheConfiguration;
import org.springframework.data.redis.cache.RedisCacheManager;
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
import org.springframework.context.annotation.ComponentScan;
import org.springframework.data.redis.listener.RedisMessageListenerContainer;


/**
 * @author Java Universe
 */
@Configuration
@EnableCaching
@EnableAspectJAutoProxy
@Slf4j
@ComponentScan(basePackages = {"com.juniverse.redis.config"})
public class CacheConfig implements ImportAware {

    /**
     * Atributo del nombre de usuario.
     */
    private static final String ATTRIBUTE_USER_NAME = "userName";

    /**
     * Atributos.
     */
    private AnnotationAttributes attributes;

    /**
     * Administrador de caché con coalescencia.
     * 
     * @param redisTemplate plantilla de Redis
     * @param redisMessageListener escucha de mensaje
     * @return administrador CoalesceCacheManager
     */
    @Bean
    CoalesceCacheManager coalesceCacheManager(RedisTemplate<String, Object> redisTemplate,
            RedisMessageListenerContainer redisMessageListener) {
        return new CoalesceCacheManager(redisTemplate, redisMessageListener);
    }

    /**
     * Aspecto de coalescencia.
     *
     * @param cacheManager administrador de caché
     * @return aspecto CoalesceCachingAspect
     */
    @Bean
    CoalesceCachingAspect coalesceCachingAspect(CoalesceCacheManager cacheManager) {
        return new CoalesceCachingAspect(cacheManager);
    }

    /**
     * Escucha de mensajes de Redis.
     * 
     * @param connectionFactory fábrica de conexiones
     * @return escucha de mensaje de Redis
     */
    @Bean
    RedisMessageListenerContainer redisMessageListenerContainer(
            RedisConnectionFactory connectionFactory) {
        RedisMessageListenerContainer container = new RedisMessageListenerContainer();
        container.setConnectionFactory(connectionFactory);
        return container;
    }
    
    /**
     * Verificador de salud de Redis.
     *
     * @param redisTemplate plantilla de Redis
     * @return verificador de salud de Redis
     */
    @Bean
    RedisHealthChecker redisHealthChecker(RedisTemplate<String, Object> redisTemplate) {
        return new RedisHealthChecker(redisTemplate);
    }

    /**
     * Servicio de caché sobre Redis.
     *
     * @param redisTemplate plantilla de Redis
     * @return servicio de caché de Redis
     */
    @Bean
    RedisCacheService redisCacheService(RedisTemplate<String, Object> redisTemplate) {
        return new RedisCacheService(redisTemplate);
    }

    /**
     * Fábrica del constructor de operaciones de caché.
     *
     * @param redisCacheService servicio de caché de Redis
     * @return fábrica de constructor de operación de caché
     */
    @Bean
    CacheOperationBuilder.Factory cacheOperationBuilderFactory(RedisCacheService redisCacheService) {
        return new CacheOperationBuilder.Factory(redisCacheService);
    }


    private List<HostsDto> getHostEntries() {
        List<HostsDto> list = new ArrayList<>();
        AnnotationAttributes[] hostArray = attributes.getAnnotationArray("hostEntries");
        for (AnnotationAttributes entry : hostArray) {
            if (entry != null) {
                String hostName = entry.getString("host");
                int port = entry.getNumber("port");
                list.add(HostsDto.builder().hostName(hostName).port(port).build());
            }
        }
        return list;
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
        String userName = attributes.getString(ATTRIBUTE_USER_NAME);
        String pwd = attributes.getString("pwd");
        config.setDatabase(attributes.getNumber("database"));
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
        String userName = attributes.getString(ATTRIBUTE_USER_NAME);
        String pwd = attributes.getString("pwd");
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

        String sentinelMaster = attributes.getString("sentinelMaster");
        List<HostsDto> hosts = getHostEntries();
        Validator.validateSentinelHosts(hosts, sentinelMaster);

        RedisSentinelConfiguration sentinelConfig =
                new RedisSentinelConfiguration()
                        .master(sentinelMaster);

        for (HostsDto host : hosts) {
            sentinelConfig.sentinel(host.getHostName(), host.getPort());
        }
        String userName = attributes.getString(ATTRIBUTE_USER_NAME);
        String pwd = attributes.getString("pwd");
        sentinelConfig.setDatabase(attributes.getNumber("database"));
        if (StringUtils.hasText(userName)) {
            sentinelConfig.setSentinelUsername(userName);
        }
        if (StringUtils.hasText(pwd)) {
            sentinelConfig.setSentinelPassword(pwd);
        }
        return sentinelConfig;
    }

    /**
     * Crea la fábrica de conexiones de Redis.
     *
     * @return fábrica de conexiones de Redis
     */
    @Bean
    @ConditionalOnMissingBean
    RedisConnectionFactory createRedisConnectionFactory() {
        LettuceClientConfiguration clientConfiguration;
        boolean useSsl = attributes.getBoolean("useSsl");
        Long connectionTimeout = attributes.getNumber("connectionTimeout");
        Long readTimeout = attributes.getNumber("readTimeout");

        Validator.validateTimeout(connectionTimeout, readTimeout);

        LettuceClientConfiguration.LettuceClientConfigurationBuilder clientConfigurationBuilder =
                LettuceClientConfiguration
                        .builder()
                        .commandTimeout(Duration.ofMillis(connectionTimeout))
                        .shutdownTimeout(Duration.ofMillis(readTimeout));
        clientConfiguration = useSsl
                ? clientConfigurationBuilder.useSsl().and().build()
                : clientConfigurationBuilder.build();

        Mode mode = attributes.getEnum("mode");
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
    @Bean
    @ConditionalOnMissingBean
    @SuppressWarnings("unchecked")
    <T> RedisTemplate<String, T> createRedisTemplate(RedisConnectionFactory connectionFactory) {
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
        var configClass = attributes.getClass("mapperConfig");
        if (configClass == DefaultObjectMapperConfig.class) {
            log.debug("Using default ObjectMapperConfig");
            return new DefaultObjectMapperConfig();
        }
        try {

            log.debug("Attempting to instantiate custom ObjectMapperConfig: {}",
                    configClass.getName());
            ObjectMapperConfig config = (ObjectMapperConfig) configClass
                    .getDeclaredConstructor()
                    .newInstance();
            log.info("Successfully instantiated custom ObjectMapperConfig: {}",
                    configClass.getName());
            return config;
        } catch (ReflectiveOperationException ex) {
            log.error("""
                              Failed to instantiate custom ObjectMapperConfig: {}.
                              Ensure the class has a public no-args constructor.
                              Falling back to default configuration. {}
                            """,
                    configClass.getName(), ex.getMessage());
            return new DefaultObjectMapperConfig();
        }
    }

    /**
     * Administrador de caché.
     * @param connectionFactory fábrica de conexiones
     * @return administrador de caché
     */
    @Bean
    CacheManager cacheManager(RedisConnectionFactory connectionFactory) {
        ObjectMapperConfig mapperConfig = getObjectMapperConfig();

        RedisCacheConfiguration redisCacheConfiguration = RedisCacheConfiguration
                .defaultCacheConfig()
                .serializeKeysWith(
                        RedisSerializationContext.SerializationPair.fromSerializer(new StringRedisSerializer()))
                .serializeValuesWith(RedisSerializationContext.SerializationPair
                        .fromSerializer(new CustomJacksonJsonRedisSerializer<>(mapperConfig.configure(),
                                Object.class)));

        AnnotationAttributes[] ttlArray = attributes.getAnnotationArray("ttlEntries");

        Map<String, Long> ttlsMap = new HashMap<>();
        for (AnnotationAttributes entry : ttlArray) {
            if (entry != null) {
                String key = entry.getString("name");
                Long value = entry.getNumber("ttl");
                Validator.validateTTL(key, value);
                ttlsMap.put(key, value);
            }
        }

        Map<String, RedisCacheConfiguration> cacheConfiguration = new HashMap<>();
        ttlsMap
                .forEach((cacheName, ttl) -> cacheConfiguration.put(cacheName, redisCacheConfiguration
                        .entryTtl(Objects.requireNonNull(Duration.ofMinutes(ttl), "ttl is required"))));
        return RedisCacheManager.builder(connectionFactory)
                .cacheDefaults(redisCacheConfiguration)
                .withInitialCacheConfigurations(cacheConfiguration)
                .build();
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
        if (this.attributes == null) {
            throw new IllegalStateException(
                    """
                    @EnableRedisLibrary annotation not found.
                    Please ensure your configuration class is annotated with @EnableRedisLibrary
                    """);
        }
    }
}
