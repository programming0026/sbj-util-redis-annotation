package io.github.programming0026.juniverse.redis.config;

import io.github.programming0026.juniverse.redis.annotation.EnableRedisLibrary;
import io.github.programming0026.juniverse.redis.annotation.HostEntry;
import io.github.programming0026.juniverse.redis.service.CacheOperationBuilder;
import io.github.programming0026.juniverse.redis.service.CoalesceCacheManager;
import io.github.programming0026.juniverse.redis.service.RedisCacheService;
import io.github.programming0026.juniverse.redis.service.RedisHealthChecker;
import io.github.programming0026.juniverse.redis.enums.Mode;
import io.github.programming0026.juniverse.redis.service.CacheMetrics;
import io.lettuce.core.ClientOptions;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.dao.QueryTimeoutException;
import io.lettuce.core.ReadFrom;
import io.lettuce.core.cluster.ClusterClientOptions;
import io.lettuce.core.cluster.ClusterTopologyRefreshOptions;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.cache.CacheManager;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.AnnotationAttributes;
import org.springframework.core.type.AnnotationMetadata;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.connection.RedisPassword;
import org.springframework.data.redis.connection.RedisSentinelConfiguration;
import org.springframework.data.redis.connection.lettuce.LettuceClientConfiguration;
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory;
import org.springframework.data.redis.cache.RedisCacheConfiguration;
import org.springframework.data.redis.cache.RedisCacheManager;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.serializer.RedisSerializer;
import org.springframework.data.redis.serializer.SerializationException;
import org.springframework.data.redis.listener.ChannelTopic;
import org.springframework.data.redis.listener.RedisMessageListenerContainer;
import org.springframework.mock.env.MockEnvironment;

import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;


/**
 * Unit tests for CacheConfig.
 *
 * @author Java Universe
 */
@ExtendWith(MockitoExtension.class)
class CacheConfigTest {

    /** Annotation metadata. */
    @Mock
    private AnnotationMetadata annotationMetadata;

    /** Cache configuration. */
    private CacheConfig cacheConfig;

    /**
     * Sets up the test.
     */
    @BeforeEach
     void setUp() {
        cacheConfig = new CacheConfig();
    }

    /**
     * The Redis health checker must create its instance.
     */
    @SuppressWarnings("unchecked")
	@Test
     void redisHealthChecker_ShouldCreateInstance() {
        RedisTemplate<String, Object> mockRedisTemplate = mock(RedisTemplate.class);

        RedisHealthChecker result = cacheConfig.redisHealthChecker(mockRedisTemplate);

        assertNotNull(result);
        assertInstanceOf(RedisHealthChecker.class, result);
    }

    /**
     * The Redis cache service must create its instance.
     */
    @SuppressWarnings("unchecked")
	@Test
     void redisCacheService_ShouldCreateInstance() {
        setAttributes(createBasicAttributesMap());
        RedisTemplate<String, Object> mockRedisTemplate = mock(RedisTemplate.class);

        RedisCacheService result = cacheConfig.redisCacheService(mockRedisTemplate);

        assertNotNull(result);
        assertInstanceOf(RedisCacheService.class, result);
    }

    /**
     * The put and eviction aspects must be registered as beans.
     */
    @Test
     void putAndEvictAspects_ShouldCreateInstances() {
        setAttributes(createBasicAttributesMap());
        CoalesceCacheManager mockCacheManager = mock(CoalesceCacheManager.class);

        assertNotNull(cacheConfig.coalescePutAspect(mockCacheManager));
        assertNotNull(cacheConfig.coalesceEvictAspect(mockCacheManager));
    }

    /**
     * The operation builder factory must create its instance.
     */
    @Test
     void cacheOperationBuilderFactory_ShouldCreateInstance() {
        RedisCacheService mockCacheService = mock(RedisCacheService.class);

        CacheOperationBuilder.Factory result = cacheConfig.cacheOperationBuilderFactory(mockCacheService);

        assertNotNull(result);
        assertInstanceOf(CacheOperationBuilder.Factory.class, result);
    }

    /**
     * Cache operation builder factory should create new builder instances.
     */
    @Test
     void cacheOperationBuilderFactory_ShouldCreateNewBuilderInstances() {
        RedisCacheService mockCacheService = mock(RedisCacheService.class);
        CacheOperationBuilder.Factory factory = cacheConfig.cacheOperationBuilderFactory(mockCacheService);

        CacheOperationBuilder<String> builder1 = factory.create();
        CacheOperationBuilder<String> builder2 = factory.create();

        assertNotNull(builder1);
        assertNotNull(builder2);
        assertNotSame(builder1, builder2, "Factory should create new instances");
    }



    /**
     * Receiving the import metadata must set the attributes.
     */
    @Test
     void setImportMetadata_ShouldSetAttributes() {
        Map<String, Object> attributesMap = createBasicAttributesMap();
        when(annotationMetadata.getAnnotationAttributes(EnableRedisLibrary.class.getName()))
                .thenReturn(attributesMap);

        assertDoesNotThrow(() -> cacheConfig.setImportMetadata(annotationMetadata));
        verify(annotationMetadata).getAnnotationAttributes(EnableRedisLibrary.class.getName());
    }

    

    /**
     * The annotation's placeholders must be resolved with the environment.
     */
    @Test
     void setImportMetadata_WithEnvironment_ShouldResolvePlaceholders() {
        Map<String, Object> attributesMap = createBasicAttributesMap();
        AnnotationAttributes hostEntry = new AnnotationAttributes();
        hostEntry.put("host", "${redis.host}");
        hostEntry.put("port", 6379);
        attributesMap.put("hostEntries", new AnnotationAttributes[]{hostEntry});
        attributesMap.put("pwd", "${redis.pwd}");
        when(annotationMetadata.getAnnotationAttributes(EnableRedisLibrary.class.getName()))
                .thenReturn(attributesMap);

        cacheConfig.setEnvironment(new MockEnvironment()
                .withProperty("redis.host", "redis.internal")
                .withProperty("redis.pwd", "secret"));
        cacheConfig.setImportMetadata(annotationMetadata);

        LettuceConnectionFactory factory = (LettuceConnectionFactory) cacheConfig.createRedisConnectionFactory();
        assertEquals("redis.internal", factory.getStandaloneConfiguration().getHostName());
        assertArrayEquals("secret".toCharArray(), factory.getStandaloneConfiguration().getPassword().get());
    }

    /**
     * A placeholder without a property in the annotation must throw an exception.
     */
    @Test
     void setImportMetadata_WithMissingProperty_ShouldThrowException() {
        Map<String, Object> attributesMap = createBasicAttributesMap();
        attributesMap.put("pwd", "${redis.missing}");
        when(annotationMetadata.getAnnotationAttributes(EnableRedisLibrary.class.getName()))
                .thenReturn(attributesMap);

        cacheConfig.setEnvironment(new MockEnvironment());
        cacheConfig.setImportMetadata(annotationMetadata);

        assertThrows(IllegalArgumentException.class, () -> cacheConfig.createRedisConnectionFactory());
    }

    /**
     * Creating the factory in standalone mode must create a standalone factory.
     */
    @Test
     void createRedisConnectionFactory_WithStandaloneMode_ShouldCreateStandaloneFactory() {
        setupStandaloneConfiguration();

        RedisConnectionFactory factory = cacheConfig.createRedisConnectionFactory();

        assertNotNull(factory);
    }

    /**
     * Creating the factory in cluster mode must create a cluster factory.
     */
    @Test
     void createRedisConnectionFactory_WithClusterMode_ShouldCreateClusterFactory() {
        setupClusterConfiguration();

        RedisConnectionFactory factory = cacheConfig.createRedisConnectionFactory();

        assertNotNull(factory);
    }

    /**
     * Creating the factory with credentials must configure authentication.
     */
    @Test
     void createRedisConnectionFactory_WithCredentials_ShouldConfigureAuth() {
        setupStandaloneConfigurationWithCredentials();

        RedisConnectionFactory factory = cacheConfig.createRedisConnectionFactory();

        assertNotNull(factory);
    }

    /**
     * Creating the cluster configuration with credentials must configure authentication.
     */
    @Test
     void createClusterConfig_WithCredentials_ShouldConfigureAuth() {
        setupClusterConfigurationWithCredentials();

        RedisConnectionFactory factory = cacheConfig.createRedisConnectionFactory();

        assertNotNull(factory);
    }

    

    /**
     * Creating the Redis template must create a configured template.
     */
    @Test
     void createRedisTemplate_ShouldCreateConfiguredTemplate() {
        setupStandaloneConfiguration();

        RedisTemplate<String, ?> template = cacheConfig.createRedisTemplate(cacheConfig.createRedisConnectionFactory());

        assertNotNull(template);
        assertNotNull(template.getConnectionFactory());
    }



    /**
     * The cache manager must be created correctly.
     */
    @Test
     void cacheManager_ShouldCreateCacheManager() {
        setupStandaloneConfigurationWithTTL();

        CacheManager cacheManager = cacheConfig.cacheManager(cacheConfig.createRedisConnectionFactory());

        assertNotNull(cacheManager);
    }

   

    /**
     * Validating servers with a null list must throw an exception.
     */
    @Test
     void validateHosts_WithNullList_ShouldThrowException() {
        setupConfigurationWithNullHosts();

        Exception exception = assertThrows(Exception.class,
                () -> cacheConfig.createRedisConnectionFactory());
        assertNotNull(exception);
    }

    /**
     * Validating servers with an empty list must throw an exception.
     */
    @Test
     void validateHosts_WithEmptyList_ShouldThrowException() {
        setupConfigurationWithEmptyHosts();

        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class,
                () -> cacheConfig.createRedisConnectionFactory());
        assertTrue(exception.getMessage().contains("At least one host must be configured"));
    }

    /**
     * Validating servers with an empty host name must throw an exception.
     */
    @Test
     void validateHosts_WithEmptyHostName_ShouldThrowException() {
        setupConfigurationWithEmptyHostName();

        
        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class,
                () -> cacheConfig.createRedisConnectionFactory());
        assertTrue(exception.getMessage().contains("Host name cannot be empty"));
    }

    /**
     * Validating servers with an invalid port (too low) must throw an exception.
     */
    @Test
     void validateHosts_WithInvalidPortLow_ShouldThrowException() {
        setupConfigurationWithInvalidPort(0);

        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class,
                () -> cacheConfig.createRedisConnectionFactory());
        assertTrue(exception.getMessage().contains("Invalid port"));
    }

    /**
     * Validating servers with an invalid port (too high) must throw an exception.
     */
    @Test
     void validateHosts_WithInvalidPortHigh_ShouldThrowException() {
        setupConfigurationWithInvalidPort(65536);

        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class,
                () -> cacheConfig.createRedisConnectionFactory());
        assertTrue(exception.getMessage().contains("Invalid port"));
    }

    /**
     * Validating standalone mode with several servers must throw an exception.
     */
    @Test
     void validateStandaloneHosts_WithMultipleHosts_ShouldThrowException() {
        setupConfigurationWithMultipleHostsStandalone();

        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class,
                () -> cacheConfig.createRedisConnectionFactory());
        assertTrue(exception.getMessage().contains("Standalone mode requires exactly one host entry"));
    }

    /**
     * Cluster mode with a single server is valid: one node is enough to discover the rest.
     */
    @Test
     void createRedisConnectionFactory_WithSingleHostCluster_ShouldCreateClusterConfiguration() {
        setupConfigurationWithSingleHostCluster();

        LettuceConnectionFactory result = (LettuceConnectionFactory) cacheConfig.createRedisConnectionFactory();

        assertNotNull(result.getClusterConfiguration());
        assertEquals(1, result.getClusterConfiguration().getClusterNodes().size());
    }

    /**
     * connectionTimeout must limit the connection and readTimeout the response to each command.
     */
    @Test
     void createRedisConnectionFactory_ShouldMapTimeoutsToConnectAndCommand() {
        Map<String, Object> attributesMap = createBasicAttributesMap();
        attributesMap.put("connectionTimeout", 1500L);
        attributesMap.put("readTimeout", 700L);
        when(annotationMetadata.getAnnotationAttributes(EnableRedisLibrary.class.getName()))
                .thenReturn(attributesMap);
        cacheConfig.setImportMetadata(annotationMetadata);

        LettuceConnectionFactory result = (LettuceConnectionFactory) cacheConfig.createRedisConnectionFactory();

        LettuceClientConfiguration client = result.getClientConfiguration();
        assertEquals(Duration.ofMillis(700), client.getCommandTimeout());
        assertEquals(Duration.ofMillis(1500),
                client.getClientOptions().orElseThrow().getSocketOptions().getConnectTimeout());
    }

    /**
     * In cluster mode the client options must be the cluster ones, with the same connection timeout.
     */
    @Test
     void createRedisConnectionFactory_WithClusterMode_ShouldUseClusterClientOptions() {
        setupConfigurationWithSingleHostCluster();

        LettuceConnectionFactory result = (LettuceConnectionFactory) cacheConfig.createRedisConnectionFactory();

        ClientOptions options = result.getClientConfiguration().getClientOptions().orElseThrow();
        assertInstanceOf(ClusterClientOptions.class, options);
        assertEquals(Duration.ofMillis(5000), options.getSocketOptions().getConnectTimeout());
    }

    /**
     * In sentinel mode, userName and pwd are the credentials of the data nodes;
     * the sentinels are left without authentication if their own is not given.
     */
    @Test
     void createRedisConnectionFactory_WithSentinelMode_ShouldApplyCredentialsToDataNodes() {
        setupSentinelConfigurationWithCredentials();

        LettuceConnectionFactory result = (LettuceConnectionFactory) cacheConfig.createRedisConnectionFactory();

        RedisSentinelConfiguration sentinel = result.getSentinelConfiguration();
        assertNotNull(sentinel);
        assertEquals("testuser", sentinel.getUsername());
        assertEquals(RedisPassword.of("testpassword"), sentinel.getPassword());
        assertNull(sentinel.getSentinelUsername());
        assertFalse(sentinel.getSentinelPassword().isPresent());
    }

    /**
     * In sentinel mode, sentinelUserName and sentinelPwd are the credentials of the sentinels.
     */
    @Test
     void createRedisConnectionFactory_WithSentinelCredentials_ShouldApplyThemToSentinels() {
        Map<String, Object> attributesMap = createBasicAttributesMap();
        attributesMap.put("mode", Mode.SENTINEL);
        attributesMap.put("sentinelMaster", "mymaster");
        attributesMap.put("sentinelUserName", "guardian");
        attributesMap.put("sentinelPwd", "secret");
        AnnotationAttributes hostEntry = new AnnotationAttributes();
        hostEntry.put("host", "sentinel1");
        hostEntry.put("port", 26379);
        attributesMap.put("hostEntries", new AnnotationAttributes[] {hostEntry});
        when(annotationMetadata.getAnnotationAttributes(EnableRedisLibrary.class.getName()))
                .thenReturn(attributesMap);
        cacheConfig.setImportMetadata(annotationMetadata);

        LettuceConnectionFactory result = (LettuceConnectionFactory) cacheConfig.createRedisConnectionFactory();

        RedisSentinelConfiguration sentinel = result.getSentinelConfiguration();
        assertNotNull(sentinel);
        assertEquals("guardian", sentinel.getSentinelUsername());
        assertEquals(RedisPassword.of("secret"), sentinel.getSentinelPassword());
        assertNull(sentinel.getUsername());
        assertFalse(sentinel.getPassword().isPresent());
    }

    

    /**
     * Creates the map of basic attributes.
     *
     * @return map
     */
    private Map<String, Object> createBasicAttributesMap() {
        Map<String, Object> map = new HashMap<>();
        map.put("mode", Mode.STANDALONE);
        map.put("useSsl", Boolean.FALSE);
        map.put("connectionTimeout", 5000L);
        map.put("readTimeout", 3000L);
        map.put("userName", "");
        map.put("pwd", "");
        map.put("sentinelUserName", "");
        map.put("sentinelPwd", "");
        map.put("failOpen", Boolean.TRUE);
        map.put("keyPrefix", "");
        map.put("readFrom", "");
        map.put("trustedPackages", new String[0]);
        map.put("coalesceLockTimeout", 30000L);
        map.put("coalesceWaitTimeout", 30000L);
        map.put("database", 0);
        map.put("mapperConfig", DefaultObjectMapperConfig.class);

        AnnotationAttributes[] hostEntries = new AnnotationAttributes[1];
        AnnotationAttributes hostEntry = new AnnotationAttributes();
        hostEntry.put("host", "localhost");
        hostEntry.put("port", 6379);
        hostEntries[0] = hostEntry;
        map.put("hostEntries", hostEntries);

        map.put("ttlEntries", new AnnotationAttributes[0]);

        return map;
    }

    /**
     * Prepares the standalone configuration.
     */
    private void setupStandaloneConfiguration() {
        Map<String, Object> attributesMap = createBasicAttributesMap();

        Map<String, Object> metadataMap = new HashMap<>();
        metadataMap.putAll(attributesMap);

        when(annotationMetadata.getAnnotationAttributes(EnableRedisLibrary.class.getName()))
                .thenReturn(metadataMap);

        cacheConfig.setImportMetadata(annotationMetadata);
    }

    /**
     * Prepares the cluster configuration.
     */
    private void setupClusterConfiguration() {
        Map<String, Object> attributesMap = createBasicAttributesMap();
        attributesMap.put("mode", Mode.CLUSTER);

        AnnotationAttributes[] hostEntries = new AnnotationAttributes[2];
        AnnotationAttributes hostEntry1 = new AnnotationAttributes();
        hostEntry1.put("host", "localhost");
        hostEntry1.put("port", 6379);
        hostEntries[0] = hostEntry1;

        AnnotationAttributes hostEntry2 = new AnnotationAttributes();
        hostEntry2.put("host", "localhost");
        hostEntry2.put("port", 6380);
        hostEntries[1] = hostEntry2;

        attributesMap.put("hostEntries", hostEntries);

        when(annotationMetadata.getAnnotationAttributes(EnableRedisLibrary.class.getName()))
                .thenReturn(attributesMap);

        cacheConfig.setImportMetadata(annotationMetadata);
    }

    /**
     * Prepares the standalone configuration with TTL.
     */
    private void setupStandaloneConfigurationWithTTL() {
        Map<String, Object> attributesMap = createBasicAttributesMap();

        AnnotationAttributes[] ttlEntries = new AnnotationAttributes[1];
        AnnotationAttributes ttlEntry = new AnnotationAttributes();
        ttlEntry.put("name", "testCache");
        ttlEntry.put("ttl", 60000L);
        ttlEntries[0] = ttlEntry;
        attributesMap.put("ttlEntries", ttlEntries);

        when(annotationMetadata.getAnnotationAttributes(EnableRedisLibrary.class.getName()))
                .thenReturn(attributesMap);

        cacheConfig.setImportMetadata(annotationMetadata);
    }

    /**
     * Prepares the configuration with null servers.
     */
    private void setupConfigurationWithNullHosts() {
        Map<String, Object> attributesMap = createBasicAttributesMap();
        attributesMap.put("hostEntries", null);

        when(annotationMetadata.getAnnotationAttributes(EnableRedisLibrary.class.getName()))
                .thenReturn(attributesMap);

        cacheConfig.setImportMetadata(annotationMetadata);
    }

    /**
     * Prepares the configuration with empty servers.
     */
    private void setupConfigurationWithEmptyHosts() {
        Map<String, Object> attributesMap = createBasicAttributesMap();

        attributesMap.put("hostEntries", new AnnotationAttributes[0]);

        when(annotationMetadata.getAnnotationAttributes(EnableRedisLibrary.class.getName()))
                .thenReturn(attributesMap);

        cacheConfig.setImportMetadata(annotationMetadata);
    }

    /**
     * Prepares the configuration with an empty host name.
     */
    private void setupConfigurationWithEmptyHostName() {
        Map<String, Object> attributesMap = createBasicAttributesMap();

        AnnotationAttributes[] hostEntries = new AnnotationAttributes[1];
        AnnotationAttributes hostEntry = new AnnotationAttributes();
        hostEntry.put("host", "");
        hostEntry.put("port", 6379);
        hostEntries[0] = hostEntry;
        attributesMap.put("hostEntries", hostEntries);

        when(annotationMetadata.getAnnotationAttributes(EnableRedisLibrary.class.getName()))
                .thenReturn(attributesMap);

        cacheConfig.setImportMetadata(annotationMetadata);
    }

    /**
     * Prepares the configuration with an invalid port.
     *
     * @param port invalid port to use
     */
    private void setupConfigurationWithInvalidPort(int port) {
        Map<String, Object> attributesMap = createBasicAttributesMap();

        AnnotationAttributes[] hostEntries = new AnnotationAttributes[1];
        AnnotationAttributes hostEntry = new AnnotationAttributes();
        hostEntry.put("host", "localhost");
        hostEntry.put("port", port);

        hostEntries[0] = hostEntry;
        attributesMap.put("hostEntries", hostEntries);

        when(annotationMetadata.getAnnotationAttributes(EnableRedisLibrary.class.getName()))
                .thenReturn(attributesMap);

        cacheConfig.setImportMetadata(annotationMetadata);
    }

    /**
     * Prepares the standalone configuration with several servers.
     */
    private void setupConfigurationWithMultipleHostsStandalone() {
        Map<String, Object> attributesMap = createBasicAttributesMap();
        attributesMap.put("mode", Mode.STANDALONE);

        AnnotationAttributes[] hostEntries = new AnnotationAttributes[2];
        AnnotationAttributes hostEntry1 = new AnnotationAttributes();
        hostEntry1.put("host", "localhost");
        hostEntry1.put("port", 6379);
        hostEntries[0] = hostEntry1;

        AnnotationAttributes hostEntry2 = new AnnotationAttributes();
        hostEntry2.put("host", "localhost");
        hostEntry2.put("port", 6380);
        hostEntries[1] = hostEntry2;

        attributesMap.put("hostEntries", hostEntries);

        when(annotationMetadata.getAnnotationAttributes(EnableRedisLibrary.class.getName()))
                .thenReturn(attributesMap);

        cacheConfig.setImportMetadata(annotationMetadata);
    }

    /**
     * Prepares the cluster configuration with a single server.
     */
    private void setupConfigurationWithSingleHostCluster() {
        Map<String, Object> attributesMap = createBasicAttributesMap();
        attributesMap.put("mode", Mode.CLUSTER);

        when(annotationMetadata.getAnnotationAttributes(EnableRedisLibrary.class.getName()))
                .thenReturn(attributesMap);

        cacheConfig.setImportMetadata(annotationMetadata);
    }

    /**
     * Prepares the standalone configuration with credentials.
     */
    private void setupStandaloneConfigurationWithCredentials() {
        Map<String, Object> attributesMap = createBasicAttributesMap();

        attributesMap.put("userName", "testuser");
        attributesMap.put("pwd", "testpassword");

        when(annotationMetadata.getAnnotationAttributes(EnableRedisLibrary.class.getName()))
                .thenReturn(attributesMap);

        cacheConfig.setImportMetadata(annotationMetadata);
    }

    /**
     * Prepares the cluster configuration with credentials.
     */
    private void setupClusterConfigurationWithCredentials() {
        Map<String, Object> attributesMap = createBasicAttributesMap();
        attributesMap.put("mode", Mode.CLUSTER);
        attributesMap.put("userName", "testuser");
        attributesMap.put("pwd", "testpassword");

        AnnotationAttributes[] hostEntries = new AnnotationAttributes[2];
        AnnotationAttributes hostEntry1 = new AnnotationAttributes();
        hostEntry1.put("host", "localhost");
        hostEntry1.put("port", 6379);
        hostEntries[0] = hostEntry1;

        AnnotationAttributes hostEntry2 = new AnnotationAttributes();
        hostEntry2.put("host", "localhost");
        hostEntry2.put("port", 6380);
        hostEntries[1] = hostEntry2;

        attributesMap.put("hostEntries", hostEntries);

        when(annotationMetadata.getAnnotationAttributes(EnableRedisLibrary.class.getName()))
                .thenReturn(attributesMap);

        cacheConfig.setImportMetadata(annotationMetadata);
    }

    

    /**
     * Creating the factory in sentinel mode must create the sentinel configuration.
     */
    @Test
     void createRedisConnectionFactory_WithSentinelMode_ShouldCreateSentinelConfiguration() {
        setupSentinelConfiguration();

        RedisConnectionFactory result = cacheConfig.createRedisConnectionFactory();

        assertNotNull(result);
    }

    /**
     * Creating the factory in sentinel mode with credentials must configure the credentials.
     */
    @Test
     void createRedisConnectionFactory_WithSentinelModeAndCredentials_ShouldConfigureCredentials() {
        setupSentinelConfigurationWithCredentials();

        RedisConnectionFactory result = cacheConfig.createRedisConnectionFactory();

        assertNotNull(result);
    }

    /**
     * Creating the sentinel configuration without a master must throw an exception.
     */
    @Test
     void createSentinelConfig_WithoutSentinelMaster_ShouldThrowException() {
        setupSentinelConfigurationWithoutMaster();

        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class,
                () -> cacheConfig.createRedisConnectionFactory());

        assertNotNull(exception.getMessage());
        assertTrue(exception.getMessage().contains("sentinelMaster"));
    }

    /**
     * Creating the sentinel configuration with an empty master must throw an exception.
     */
    @Test
     void createSentinelConfig_WithEmptySentinelMaster_ShouldThrowException() {
        setupSentinelConfigurationWithEmptyMaster();

        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class,
                () -> cacheConfig.createRedisConnectionFactory());

        assertEquals("sentinelMaster must be configured when using SENTINEL mode",
                exception.getMessage());
    }



    /**
     * Prepares the sentinel configuration.
     */
    private void setupSentinelConfiguration() {
        Map<String, Object> attributesMap = createBasicAttributesMap();
        attributesMap.put("mode", Mode.SENTINEL);
        attributesMap.put("sentinelMaster", "mymaster");

        AnnotationAttributes[] hostEntries = new AnnotationAttributes[3];
        AnnotationAttributes hostEntry1 = new AnnotationAttributes();
        hostEntry1.put("host", "sentinel1");
        hostEntry1.put("port", 26379);
        hostEntries[0] = hostEntry1;

        AnnotationAttributes hostEntry2 = new AnnotationAttributes();
        hostEntry2.put("host", "sentinel2");
        hostEntry2.put("port", 26379);
        hostEntries[1] = hostEntry2;

        AnnotationAttributes hostEntry3 = new AnnotationAttributes();
        hostEntry3.put("host", "sentinel3");
        hostEntry3.put("port", 26379);
        hostEntries[2] = hostEntry3;

        attributesMap.put("hostEntries", hostEntries);

        when(annotationMetadata.getAnnotationAttributes(EnableRedisLibrary.class.getName()))
                .thenReturn(attributesMap);

        cacheConfig.setImportMetadata(annotationMetadata);
    }

    /**
     * Prepares the sentinel configuration with credentials.
     */
    private void setupSentinelConfigurationWithCredentials() {
        Map<String, Object> attributesMap = createBasicAttributesMap();
        attributesMap.put("mode", Mode.SENTINEL);
        attributesMap.put("sentinelMaster", "mymaster");
        attributesMap.put("userName", "testuser");
        attributesMap.put("pwd", "testpassword");

        AnnotationAttributes[] hostEntries = new AnnotationAttributes[3];
        AnnotationAttributes hostEntry1 = new AnnotationAttributes();
        hostEntry1.put("host", "sentinel1");
        hostEntry1.put("port", 26379);
        hostEntries[0] = hostEntry1;

        AnnotationAttributes hostEntry2 = new AnnotationAttributes();
        hostEntry2.put("host", "sentinel2");
        hostEntry2.put("port", 26379);
        hostEntries[1] = hostEntry2;

        AnnotationAttributes hostEntry3 = new AnnotationAttributes();
        hostEntry3.put("host", "sentinel3");
        hostEntry3.put("port", 26379);
        hostEntries[2] = hostEntry3;

        attributesMap.put("hostEntries", hostEntries);

        when(annotationMetadata.getAnnotationAttributes(EnableRedisLibrary.class.getName()))
                .thenReturn(attributesMap);

        cacheConfig.setImportMetadata(annotationMetadata);
    }

    /**
     * Prepares the sentinel configuration without a master.
     */
    private void setupSentinelConfigurationWithoutMaster() {
        Map<String, Object> attributesMap = createBasicAttributesMap();
        attributesMap.put("mode", Mode.SENTINEL);
        attributesMap.put("sentinelMaster", null);  // Explicitly set to null

        AnnotationAttributes[] hostEntries = new AnnotationAttributes[2];
        AnnotationAttributes hostEntry1 = new AnnotationAttributes();
        hostEntry1.put("host", "sentinel1");
        hostEntry1.put("port", 26379);
        hostEntries[0] = hostEntry1;

        AnnotationAttributes hostEntry2 = new AnnotationAttributes();
        hostEntry2.put("host", "sentinel2");
        hostEntry2.put("port", 26379);
        hostEntries[1] = hostEntry2;

        attributesMap.put("hostEntries", hostEntries);

        when(annotationMetadata.getAnnotationAttributes(EnableRedisLibrary.class.getName()))
                .thenReturn(attributesMap);

        cacheConfig.setImportMetadata(annotationMetadata);
    }

    /**
     * Prepares the sentinel configuration with an empty master.
     */
    private void setupSentinelConfigurationWithEmptyMaster() {
        Map<String, Object> attributesMap = createBasicAttributesMap();
        attributesMap.put("mode", Mode.SENTINEL);
        attributesMap.put("sentinelMaster", "");

        AnnotationAttributes[] hostEntries = new AnnotationAttributes[2];
        AnnotationAttributes hostEntry1 = new AnnotationAttributes();
        hostEntry1.put("host", "sentinel1");
        hostEntry1.put("port", 26379);
        hostEntries[0] = hostEntry1;

        AnnotationAttributes hostEntry2 = new AnnotationAttributes();
        hostEntry2.put("host", "sentinel2");
        hostEntry2.put("port", 26379);
        hostEntries[1] = hostEntry2;

        attributesMap.put("hostEntries", hostEntries);

        when(annotationMetadata.getAnnotationAttributes(EnableRedisLibrary.class.getName()))
                .thenReturn(attributesMap);

        cacheConfig.setImportMetadata(annotationMetadata);
    }

    /**
     * The coalescing cache manager is created with the annotation's settings.
     */
    @Test
     void coalesceCacheManager_ShouldBeCreatedFromAnnotationSettings() {
        setAttributes(createBasicAttributesMap());
        RedisMessageListenerContainer container = mock(RedisMessageListenerContainer.class);

        CoalesceCacheManager result = cacheConfig.coalesceCacheManager(mock(RedisConnectionFactory.class), container);

        assertNotNull(result);
        verify(container).addMessageListener(any(), any(ChannelTopic.class));
    }

    /**
     * A lock timeout that is not positive must be rejected at startup.
     */
    @Test
     void coalesceCacheManager_WithInvalidLockTimeout_ShouldThrowException() {
        Map<String, Object> attributesMap = createBasicAttributesMap();
        attributesMap.put("coalesceLockTimeout", 0L);
        setAttributes(attributesMap);

        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class,
                () -> cacheConfig.coalesceCacheManager(mock(RedisConnectionFactory.class),
                        mock(RedisMessageListenerContainer.class)));

        assertEquals("Invalid coalesceLockTimeout 0.", exception.getMessage());
    }

    /**
     * The message listener is created on top of the received connection factory.
     */
    @Test
     void redisMessageListenerContainer_ShouldUseGivenConnectionFactory() {
        setAttributes(createBasicAttributesMap());
        RedisConnectionFactory connectionFactory = mock(RedisConnectionFactory.class);

        RedisMessageListenerContainer result = cacheConfig.redisMessageListenerContainer(connectionFactory);

        assertSame(connectionFactory, result.getConnectionFactory());
        assertInstanceOf(ResilientRedisMessageListenerContainer.class, result);
    }

    /**
     * Without degradation Spring's listener is used, which fails at startup if Redis does not respond.
     */
    @Test
     void redisMessageListenerContainer_WithoutFailOpen_ShouldBeThePlainOne() {
        Map<String, Object> attributesMap = createBasicAttributesMap();
        attributesMap.put("failOpen", Boolean.FALSE);
        setAttributes(attributesMap);

        RedisMessageListenerContainer result =
                cacheConfig.redisMessageListenerContainer(mock(RedisConnectionFactory.class));

        assertEquals(RedisMessageListenerContainer.class, result.getClass());
    }

    /**
     * If Redis does not respond at startup, the listener does not throw and retries on its own.
     *
     * @throws Exception if the test is interrupted
     */
    @Test
     void resilientListenerContainer_WhenRedisIsDown_ShouldNotFailAndShouldRetry() throws Exception {
        RedisConnectionFactory connectionFactory = mock(RedisConnectionFactory.class);
        when(connectionFactory.getConnection())
                .thenThrow(new org.springframework.data.redis.RedisConnectionFailureException("no connection"));
        ResilientRedisMessageListenerContainer container =
                new ResilientRedisMessageListenerContainer(Duration.ofMillis(50));
        container.setConnectionFactory(connectionFactory);
        container.addMessageListener((message, pattern) -> { }, new ChannelTopic("canal"));
        container.afterPropertiesSet();

        assertDoesNotThrow(container::start);
        Thread.sleep(400);
        container.stop();
        container.destroy();

        verify(connectionFactory, atLeast(2)).getConnection();
    }

    private void setAttributes(Map<String, Object> attributesMap) {
        when(annotationMetadata.getAnnotationAttributes(EnableRedisLibrary.class.getName()))
                .thenReturn(attributesMap);
        cacheConfig.setImportMetadata(annotationMetadata);
    }

    /**
     * Without keyPrefix in the annotation, spring.application.name is used; with keyPrefix, that one prevails.
     */
    @Test
     void keyPrefix_ShouldComeFromAnnotationOrApplicationName() {
        cacheConfig.setEnvironment(new MockEnvironment().withProperty("spring.application.name", "shop"));
        setAttributes(createBasicAttributesMap());
        RedisMessageListenerContainer fromName = mock(RedisMessageListenerContainer.class);
        cacheConfig.coalesceCacheManager(mock(RedisConnectionFactory.class), fromName);

        Map<String, Object> explicit = createBasicAttributesMap();
        explicit.put("keyPrefix", "pedidos");
        setAttributes(explicit);
        RedisMessageListenerContainer fromAnnotation = mock(RedisMessageListenerContainer.class);
        cacheConfig.coalesceCacheManager(mock(RedisConnectionFactory.class), fromAnnotation);

        verify(fromName).addMessageListener(any(), eq(new ChannelTopic("shop:coalesce:signal")));
        verify(fromAnnotation).addMessageListener(any(), eq(new ChannelTopic("pedidos:coalesce:signal")));
    }

    /**
     * A prefix with pattern characters must be rejected at startup.
     */
    @Test
     void keyPrefix_WithGlobCharacters_ShouldThrowException() {
        Map<String, Object> attributesMap = createBasicAttributesMap();
        attributesMap.put("keyPrefix", "shop*");
        setAttributes(attributesMap);

        assertThrows(IllegalArgumentException.class,
                () -> cacheConfig.redisCacheService(mock(RedisTemplate.class)));
    }

    /**
     * TTL entries accept a unit; without a unit they are minutes. The three cache paths receive them.
     */
    @Test
     void ttlEntries_ShouldHonourTheirUnit() {
        Map<String, Object> attributesMap = createBasicAttributesMap();
        AnnotationAttributes seconds = new AnnotationAttributes();
        seconds.put("name", "tokens");
        seconds.put("ttl", 90L);
        seconds.put("unit", TimeUnit.SECONDS);
        AnnotationAttributes minutes = new AnnotationAttributes();
        minutes.put("name", "users");
        minutes.put("ttl", 30L);
        attributesMap.put("ttlEntries", new AnnotationAttributes[] {seconds, minutes, null});
        setAttributes(attributesMap);

        RedisCacheManager manager = redisCacheManagerBehind(
                cacheConfig.cacheManager(cacheConfig.createRedisConnectionFactory()));

        Map<String, RedisCacheConfiguration> configurations = manager.getCacheConfigurations();
        assertEquals(Duration.ofSeconds(90), configurations.get("tokens").getTtlFunction().getTimeToLive("k", "v"));
        assertEquals(Duration.ofMinutes(30), configurations.get("users").getTtlFunction().getTimeToLive("k", "v"));
        assertNotNull(cacheConfig.coalesceCachingAspect(mock(CoalesceCacheManager.class)));
        assertNotNull(cacheConfig.redisCacheService(mock(RedisTemplate.class)));
    }

    /**
     * With an application prefix, Spring's cache manager prepends the prefix to its keys.
     */
    @Test
     void cacheManager_WithKeyPrefix_ShouldPrefixCacheNames() {
        Map<String, Object> attributesMap = createBasicAttributesMap();
        attributesMap.put("keyPrefix", "shop");
        setAttributes(attributesMap);

        RedisCacheManager manager = redisCacheManagerBehind(
                cacheConfig.cacheManager(cacheConfig.createRedisConnectionFactory()));

        assertEquals("shop:users::", manager.getCacheConfigurations().getOrDefault("users", ((org.springframework.data.redis.cache.RedisCache) manager.getCache("users")).getCacheConfiguration()).getKeyPrefixFor("users"));
    }

    /**
     * The trusted packages are those in the annotation plus the package of the class carrying it.
     */
    @Test
     void trustedPackages_ShouldIncludeImportingClassPackage() {
        Map<String, Object> attributesMap = createBasicAttributesMap();
        attributesMap.put("trustedPackages", new String[] {"com.acme.shared"});
        when(annotationMetadata.getClassName()).thenReturn("com.acme.app.config.AppConfig");
        cacheConfig.setBeanFactory(mock(org.springframework.beans.factory.BeanFactory.class));
        setAttributes(attributesMap);

        RedisTemplate<String, Object> template =
                cacheConfig.createRedisTemplate(cacheConfig.createRedisConnectionFactory());
        @SuppressWarnings("unchecked")
        RedisSerializer<Object> serializer = (RedisSerializer<Object>) template.getValueSerializer();

        assertThrows(SerializationException.class, () -> serializer.deserialize(
                "{\"@class\":\"com.other.Type\"}".getBytes(StandardCharsets.UTF_8)));
        assertEquals(List.of("a"), serializer.deserialize(
                "[\"java.util.ArrayList\",[\"a\"]]".getBytes(StandardCharsets.UTF_8)));
    }

    /**
     * The client keeps connections alive and, with degradation enabled, rejects commands
     * while disconnected instead of queueing them.
     */
    @Test
     void createRedisConnectionFactory_ShouldEnableKeepAliveAndRejectCommandsWhileDisconnected() {
        setAttributes(createBasicAttributesMap());

        ClientOptions options = clientOptionsOfNewFactory();

        assertTrue(options.getSocketOptions().isKeepAlive());
        assertEquals(ClientOptions.DisconnectedBehavior.REJECT_COMMANDS, options.getDisconnectedBehavior());
    }

    /**
     * Without degradation Lettuce's behavior is kept: commands wait for the reconnection.
     */
    @Test
     void createRedisConnectionFactory_WithoutFailOpen_ShouldKeepDefaultDisconnectedBehavior() {
        Map<String, Object> attributesMap = createBasicAttributesMap();
        attributesMap.put("failOpen", Boolean.FALSE);
        setAttributes(attributesMap);

        assertEquals(ClientOptions.DisconnectedBehavior.DEFAULT,
                clientOptionsOfNewFactory().getDisconnectedBehavior());
    }

    /**
     * In cluster mode the client refreshes the topology, periodically and on redirection errors.
     */
    @Test
     void createRedisConnectionFactory_WithClusterMode_ShouldRefreshTopology() {
        setupConfigurationWithSingleHostCluster();

        ClusterClientOptions options = assertInstanceOf(ClusterClientOptions.class, clientOptionsOfNewFactory());

        ClusterTopologyRefreshOptions refresh = options.getTopologyRefreshOptions();
        assertTrue(refresh.isPeriodicRefreshEnabled());
        assertEquals(Duration.ofSeconds(60), refresh.getRefreshPeriod());
        assertFalse(refresh.getAdaptiveRefreshTriggers().isEmpty());
        assertTrue(options.getSocketOptions().isKeepAlive());
    }

    /**
     * readFrom is passed to the client; a name Lettuce does not know is rejected at startup.
     */
    @Test
     void createRedisConnectionFactory_ShouldApplyReadFrom() {
        Map<String, Object> attributesMap = createBasicAttributesMap();
        attributesMap.put("readFrom", " replicaPreferred ");
        setAttributes(attributesMap);
        LettuceConnectionFactory withReplicas = (LettuceConnectionFactory) cacheConfig.createRedisConnectionFactory();

        setAttributes(createBasicAttributesMap());
        LettuceConnectionFactory withoutReplicas =
                (LettuceConnectionFactory) cacheConfig.createRedisConnectionFactory();

        Map<String, Object> invalid = createBasicAttributesMap();
        invalid.put("readFrom", "does-not-exist");
        setAttributes(invalid);

        assertEquals(ReadFrom.REPLICA_PREFERRED, withReplicas.getClientConfiguration().getReadFrom().orElseThrow());
        assertTrue(withoutReplicas.getClientConfiguration().getReadFrom().isEmpty());
        assertThrows(IllegalArgumentException.class, () -> cacheConfig.createRedisConnectionFactory());
    }

    /**
     * A mapperConfig that cannot be instantiated prevents startup, instead of being silently
     * replaced by the default one.
     */
    @Test
     void createRedisTemplate_WithMapperConfigThatCannotBeInstantiated_ShouldFail() {
        Map<String, Object> attributesMap = createBasicAttributesMap();
        attributesMap.put("mapperConfig", ObjectMapperConfig.class);
        setAttributes(attributesMap);
        RedisConnectionFactory connectionFactory = mock(RedisConnectionFactory.class);

        IllegalStateException exception = assertThrows(IllegalStateException.class,
                () -> cacheConfig.createRedisTemplate(connectionFactory));

        assertTrue(exception.getMessage().contains(ObjectMapperConfig.class.getName()));
    }

    /**
     * A custom mapperConfig with a no-argument constructor is used as is.
     */
    @Test
     void createRedisTemplate_WithCustomMapperConfig_ShouldUseIt() {
        Map<String, Object> attributesMap = createBasicAttributesMap();
        attributesMap.put("mapperConfig", PlainMapperConfig.class);
        setAttributes(attributesMap);

        RedisTemplate<String, Object> template =
                cacheConfig.createRedisTemplate(mock(RedisConnectionFactory.class));
        @SuppressWarnings("unchecked")
        RedisSerializer<Object> serializer = (RedisSerializer<Object>) template.getValueSerializer();

        // Without default typing, a list is written without a class name.
        assertEquals("[\"a\"]", new String(serializer.serialize(List.of("a")), StandardCharsets.UTF_8));
    }

    /**
     * The selector imports the library's configuration before Spring Boot's autoconfiguration.
     */
    @Test
     void importSelector_ShouldImportCacheConfigBeforeAutoConfiguration() {
        RedisLibraryImportSelector selector = new RedisLibraryImportSelector();

        assertArrayEquals(
                new String[] {CacheMetricsConfiguration.class.getName(), CacheConfig.class.getName()},
                selector.selectImports(annotationMetadata));
        assertTrue(selector.getOrder() < Ordered.LOWEST_PRECEDENCE - 1);
    }

    /** Custom mapper configuration, without default typing. */
     public static class PlainMapperConfig implements ObjectMapperConfig {

        @Override
        public tools.jackson.databind.ObjectMapper configure() {
            return tools.jackson.databind.json.JsonMapper.builder().build();
        }
    }

    private ClientOptions clientOptionsOfNewFactory() {
        LettuceConnectionFactory factory = (LettuceConnectionFactory) cacheConfig.createRedisConnectionFactory();
        return factory.getClientConfiguration().getClientOptions().orElseThrow();
    }

    /**
     * With a Micrometer registry the counters are published; without a registry, or with several,
     * the no-op implementation is used.
     */
    @Test
    @SuppressWarnings("unchecked")
     void cacheMetrics_ShouldUseMicrometerOnlyWhenThereIsOneRegistry() {
        CacheMetricsConfiguration configuration = new CacheMetricsConfiguration();
        ObjectProvider<MeterRegistry> withRegistry = mock(ObjectProvider.class);
        when(withRegistry.getIfUnique()).thenReturn(new SimpleMeterRegistry());
        ObjectProvider<MeterRegistry> withoutRegistry = mock(ObjectProvider.class);

        assertInstanceOf(MicrometerCacheMetrics.class, configuration.cacheMetrics(withRegistry));
        assertSame(CacheMetrics.NOOP, configuration.cacheMetrics(withoutRegistry));
    }

    /**
     * The cache manager receives the counters registered in the context.
     */
    @Test
    @SuppressWarnings("unchecked")
     void coalesceCacheManager_ShouldUseMetricsBeanFromContext() {
        CacheMetrics metrics = mock(CacheMetrics.class);
        ObjectProvider<CacheMetrics> provider = mock(ObjectProvider.class);
        when(provider.getIfAvailable(any(java.util.function.Supplier.class))).thenReturn(metrics);
        org.springframework.beans.factory.BeanFactory beanFactory =
                mock(org.springframework.beans.factory.BeanFactory.class);
        when(beanFactory.getBeanProvider(CacheMetrics.class)).thenReturn(provider);
        cacheConfig.setBeanFactory(beanFactory);
        setAttributes(createBasicAttributesMap());
        RedisConnectionFactory connectionFactory = mock(RedisConnectionFactory.class);
        when(connectionFactory.getConnection()).thenThrow(new QueryTimeoutException("no response"));

        CoalesceCacheManager manager = cacheConfig.coalesceCacheManager(
                connectionFactory, mock(RedisMessageListenerContainer.class));
        manager.get("users:1");

        verify(metrics).redisError("read");
    }

    private static RedisCacheManager redisCacheManagerBehind(CacheManager cacheManager) {
        FailOpenCacheManager failOpen = assertInstanceOf(FailOpenCacheManager.class, cacheManager);
        return assertInstanceOf(RedisCacheManager.class, failOpen.getDelegate());
    }

    /**
     * Without degradation, Spring's cache manager is the unwrapped Redis one.
     */
    @Test
     void cacheManager_WithoutFailOpen_ShouldNotBeWrapped() {
        Map<String, Object> attributesMap = createBasicAttributesMap();
        attributesMap.put("failOpen", Boolean.FALSE);
        setAttributes(attributesMap);

        assertInstanceOf(RedisCacheManager.class,
                cacheConfig.cacheManager(cacheConfig.createRedisConnectionFactory()));
    }

    /**
     * The server accepts the port inside host, which then takes precedence over the port attribute.
     */
    @Test
     void hostEntry_ShouldAcceptPortInsideHost() {
        assertHostAndPort("redis.internal:6380", 6379, "redis.internal", 6380);
        assertHostAndPort(" redis.internal : 6381 ", 6379, "redis.internal", 6381);
        assertHostAndPort("redis.internal", 7000, "redis.internal", 7000);
        assertHostAndPort("[2001:db8::1]:6382", 6379, "2001:db8::1", 6382);
        assertHostAndPort("[2001:db8::1]", 6379, "2001:db8::1", 6379);
        assertHostAndPort("2001:db8::1", 6379, "2001:db8::1", 6379);
    }

    @Test
     void hostEntry_WithInvalidPortInsideHost_ShouldThrowException() {
        setAttributes(attributesWithHost("redis.internal:port", 6379));

        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class,
                () -> cacheConfig.createRedisConnectionFactory());

        assertTrue(exception.getMessage().contains("redis.internal:port"));
    }

    private void assertHostAndPort(String host, int port, String expectedHost, int expectedPort) {
        setAttributes(attributesWithHost(host, port));

        LettuceConnectionFactory factory = (LettuceConnectionFactory) cacheConfig.createRedisConnectionFactory();

        assertEquals(expectedHost, factory.getStandaloneConfiguration().getHostName());
        assertEquals(expectedPort, factory.getStandaloneConfiguration().getPort());
    }

    private Map<String, Object> attributesWithHost(String host, int port) {
        Map<String, Object> attributesMap = createBasicAttributesMap();
        AnnotationAttributes hostEntry = new AnnotationAttributes();
        hostEntry.put("host", host);
        hostEntry.put("port", port);
        attributesMap.put("hostEntries", new AnnotationAttributes[] {hostEntry});
        return attributesMap;
    }

    /** Base configuration with the library's annotation. */
    @EnableRedisLibrary(hostEntries = @HostEntry(host = "inherited.internal:6390"), keyPrefix = "inherited")
     static class BaseConfiguration {
    }

    /** Configuration that inherits the annotation from its superclass. */
     static class InheritingConfiguration extends BaseConfiguration {
    }

    /**
     * The annotation may be on a superclass of the configuration class.
     */
    @Test
     void setImportMetadata_ShouldFindAnnotationOnSuperclass() {
        when(annotationMetadata.getAnnotationAttributes(EnableRedisLibrary.class.getName())).thenReturn(null);
        when(annotationMetadata.getClassName()).thenReturn(InheritingConfiguration.class.getName());

        cacheConfig.setImportMetadata(annotationMetadata);
        LettuceConnectionFactory factory = (LettuceConnectionFactory) cacheConfig.createRedisConnectionFactory();

        assertEquals("inherited.internal", factory.getStandaloneConfiguration().getHostName());
        assertEquals(6390, factory.getStandaloneConfiguration().getPort());
    }

    /**
     * If the annotation is neither on the class nor on its superclasses, startup fails with a clear message.
     */
    @Test
     void setImportMetadata_WithoutAnnotationAnywhere_ShouldThrowException() {
        when(annotationMetadata.getAnnotationAttributes(EnableRedisLibrary.class.getName())).thenReturn(null);
        when(annotationMetadata.getClassName()).thenReturn(String.class.getName(), "class.that.does.not.Exist");

        assertThrows(IllegalStateException.class, () -> cacheConfig.setImportMetadata(annotationMetadata));
        assertThrows(IllegalStateException.class, () -> cacheConfig.setImportMetadata(annotationMetadata));
    }
}
