package com.juniverse.redis.config;

import com.juniverse.redis.annotation.EnableRedisLibrary;
import com.juniverse.redis.service.CacheOperationBuilder;
import com.juniverse.redis.service.RedisCacheService;
import com.juniverse.redis.service.RedisHealthChecker;
import com.juniverse.redis.util.Mode;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.cache.CacheManager;
import org.springframework.core.annotation.AnnotationAttributes;
import org.springframework.core.type.AnnotationMetadata;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.core.RedisTemplate;

import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;


/**
 * Pruebas unitarias de CacheConfig.
 *
 * @author Java Universe
 */
@ExtendWith(MockitoExtension.class)
class CacheConfigTest {

    /** Metadatos de la anotación. */
    @Mock
    private AnnotationMetadata annotationMetadata;

    /** Configuración de caché. */
    private CacheConfig cacheConfig;

    /**
     * Prepara la prueba.
     */
    @BeforeEach
    void setUp() {
        cacheConfig = new CacheConfig();
    }

    /**
     * El verificador de salud de Redis debe crear su instancia.
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
     * El servicio de caché de Redis debe crear su instancia.
     */
    @SuppressWarnings("unchecked")
	@Test
    void redisCacheService_ShouldCreateInstance() {
        RedisTemplate<String, Object> mockRedisTemplate = mock(RedisTemplate.class);

        RedisCacheService result = cacheConfig.redisCacheService(mockRedisTemplate);

        assertNotNull(result);
        assertInstanceOf(RedisCacheService.class, result);
    }

    /**
     * La fábrica del constructor de operaciones debe crear su instancia.
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
     * Recibir los metadatos de importación debe asignar los atributos.
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
     * Crear la fábrica con modo standalone debe crear una fábrica standalone.
     */
    @Test
    void createRedisConnectionFactory_WithStandaloneMode_ShouldCreateStandaloneFactory() {
        setupStandaloneConfiguration();

        RedisConnectionFactory factory = cacheConfig.createRedisConnectionFactory();

        assertNotNull(factory);
    }

    /**
     * Crear la fábrica con modo clúster debe crear una fábrica de clúster.
     */
    @Test
    void createRedisConnectionFactory_WithClusterMode_ShouldCreateClusterFactory() {
        setupClusterConfiguration();

        RedisConnectionFactory factory = cacheConfig.createRedisConnectionFactory();

        assertNotNull(factory);
    }

    /**
     * Crear la fábrica con credenciales debe configurar la autenticación.
     */
    @Test
    void createRedisConnectionFactory_WithCredentials_ShouldConfigureAuth() {
        setupStandaloneConfigurationWithCredentials();

        RedisConnectionFactory factory = cacheConfig.createRedisConnectionFactory();

        assertNotNull(factory);
    }

    /**
     * Crear la configuración de clúster con credenciales debe configurar la autenticación.
     */
    @Test
    void createClusterConfig_WithCredentials_ShouldConfigureAuth() {
        setupClusterConfigurationWithCredentials();

        RedisConnectionFactory factory = cacheConfig.createRedisConnectionFactory();

        assertNotNull(factory);
    }

    

    /**
     * Crear la plantilla de Redis debe crear una plantilla configurada.
     */
    @Test
    void createRedisTemplate_ShouldCreateConfiguredTemplate() {
        setupStandaloneConfiguration();

        RedisTemplate<String, ?> template = cacheConfig.createRedisTemplate(cacheConfig.createRedisConnectionFactory());

        assertNotNull(template);
        assertNotNull(template.getConnectionFactory());
    }



    /**
     * El administrador de caché debe crearse correctamente.
     */
    @Test
    void cacheManager_ShouldCreateCacheManager() {
        setupStandaloneConfigurationWithTTL();

        CacheManager cacheManager = cacheConfig.cacheManager(cacheConfig.createRedisConnectionFactory());

        assertNotNull(cacheManager);
    }

   

    /**
     * Validar servidores con lista nula debe lanzar una excepción.
     */
    @Test
    void validateHosts_WithNullList_ShouldThrowException() {
        setupConfigurationWithNullHosts();

        Exception exception = assertThrows(Exception.class,
                () -> cacheConfig.createRedisConnectionFactory());
        assertNotNull(exception);
    }

    /**
     * Validar servidores con lista vacía debe lanzar una excepción.
     */
    @Test
    void validateHosts_WithEmptyList_ShouldThrowException() {
        setupConfigurationWithEmptyHosts();

        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class,
                () -> cacheConfig.createRedisConnectionFactory());
        assertTrue(exception.getMessage().contains("At least one host must be configured"));
    }

    /**
     * Validar servidores con nombre de host vacío debe lanzar una excepción.
     */
    @Test
    void validateHosts_WithEmptyHostName_ShouldThrowException() {
        setupConfigurationWithEmptyHostName();

        
        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class,
                () -> cacheConfig.createRedisConnectionFactory());
        assertTrue(exception.getMessage().contains("Host name cannot be empty"));
    }

    /**
     * Validar servidores con puerto inválido (muy bajo) debe lanzar una excepción.
     */
    @Test
    void validateHosts_WithInvalidPortLow_ShouldThrowException() {
        setupConfigurationWithInvalidPort(0);

        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class,
                () -> cacheConfig.createRedisConnectionFactory());
        assertTrue(exception.getMessage().contains("Invalid port"));
    }

    /**
     * Validar servidores con puerto inválido (muy alto) debe lanzar una excepción.
     */
    @Test
    void validateHosts_WithInvalidPortHigh_ShouldThrowException() {
        setupConfigurationWithInvalidPort(65536);

        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class,
                () -> cacheConfig.createRedisConnectionFactory());
        assertTrue(exception.getMessage().contains("Invalid port"));
    }

    /**
     * Validar el modo standalone con varios servidores debe lanzar una excepción.
     */
    @Test
    void validateStandaloneHosts_WithMultipleHosts_ShouldThrowException() {
        setupConfigurationWithMultipleHostsStandalone();

        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class,
                () -> cacheConfig.createRedisConnectionFactory());
        assertTrue(exception.getMessage().contains("Standalone mode requires exactly one host entry"));
    }

    /**
     * Validar el modo clúster con un solo servidor debe lanzar una excepción.
     */
    @Test
    void validateClusterHosts_WithSingleHost_ShouldThrowException() {
        setupConfigurationWithSingleHostCluster();

        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class,
                () -> cacheConfig.createRedisConnectionFactory());
        assertTrue(exception.getMessage().contains("Cluster mode requires host entries for proper redundancy"));
    }

    

    /**
     * Crea el mapa de atributos básicos.
     *
     * @return mapa
     */
    private Map<String, Object> createBasicAttributesMap() {
        Map<String, Object> map = new HashMap<>();
        map.put("mode", Mode.STANDALONE);
        map.put("useSsl", Boolean.FALSE);
        map.put("connectionTimeout", 5000L);
        map.put("readTimeout", 3000L);
        map.put("userName", "");
        map.put("pwd", "");
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
     * Prepara la configuración standalone.
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
     * Prepara la configuración de clúster.
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
     * Prepara la configuración standalone con TTL.
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
     * Prepara la configuración con servidores nulos.
     */
    private void setupConfigurationWithNullHosts() {
        Map<String, Object> attributesMap = createBasicAttributesMap();
        attributesMap.put("hostEntries", null);

        when(annotationMetadata.getAnnotationAttributes(EnableRedisLibrary.class.getName()))
                .thenReturn(attributesMap);

        cacheConfig.setImportMetadata(annotationMetadata);
    }

    /**
     * Prepara la configuración con servidores vacíos.
     */
    private void setupConfigurationWithEmptyHosts() {
        Map<String, Object> attributesMap = createBasicAttributesMap();

        attributesMap.put("hostEntries", new AnnotationAttributes[0]);

        when(annotationMetadata.getAnnotationAttributes(EnableRedisLibrary.class.getName()))
                .thenReturn(attributesMap);

        cacheConfig.setImportMetadata(annotationMetadata);
    }

    /**
     * Prepara la configuración con nombre de host vacío.
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
     * Prepara la configuración con un puerto inválido.
     *
     * @param port puerto inválido que se usa
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
     * Prepara la configuración standalone con varios servidores.
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
     * Prepara la configuración de clúster con un solo servidor.
     */
    private void setupConfigurationWithSingleHostCluster() {
        Map<String, Object> attributesMap = createBasicAttributesMap();
        attributesMap.put("mode", Mode.CLUSTER);

        when(annotationMetadata.getAnnotationAttributes(EnableRedisLibrary.class.getName()))
                .thenReturn(attributesMap);

        cacheConfig.setImportMetadata(annotationMetadata);
    }

    /**
     * Prepara la configuración standalone con credenciales.
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
     * Prepara la configuración de clúster con credenciales.
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
     * Crear la fábrica con modo sentinel debe crear la configuración de sentinel.
     */
    @Test
    void createRedisConnectionFactory_WithSentinelMode_ShouldCreateSentinelConfiguration() {
        setupSentinelConfiguration();

        RedisConnectionFactory result = cacheConfig.createRedisConnectionFactory();

        assertNotNull(result);
    }

    /**
     * Crear la fábrica con modo sentinel y credenciales debe configurar las credenciales.
     */
    @Test
    void createRedisConnectionFactory_WithSentinelModeAndCredentials_ShouldConfigureCredentials() {
        setupSentinelConfigurationWithCredentials();

        RedisConnectionFactory result = cacheConfig.createRedisConnectionFactory();

        assertNotNull(result);
    }

    /**
     * Crear la configuración de sentinel sin maestro debe lanzar una excepción.
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
     * Crear la configuración de sentinel con maestro vacío debe lanzar una excepción.
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
     * Prepara la configuración de sentinel.
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
     * Prepara la configuración de sentinel con credenciales.
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
     * Prepara la configuración de sentinel sin maestro.
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
     * Prepara la configuración de sentinel con maestro vacío.
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
}
