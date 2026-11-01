package com.juniverse.redis.service;

import com.juniverse.redis.dto.RedisStatusDto;
import io.lettuce.core.RedisFuture;
import io.lettuce.core.api.async.RedisAsyncCommands;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataAccessException;
import org.springframework.data.redis.connection.RedisConnection;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.core.RedisTemplate;


import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * Pruebas unitarias de RedisHealthChecker.
 *
 * @author ajuar
 */
@ExtendWith(MockitoExtension.class)
class RedisHealthCheckerTest {

    /** Fábrica de conexiones simulada. */
    @Mock
    private RedisConnectionFactory connectionFactory;

    /** Conexión simulada. */
    @Mock
    private RedisConnection connection;

    /** Comandos asíncronos de Redis simulados. */
    @Mock
    private RedisAsyncCommands<Object, Object> asyncCommands;

    /** Futuro de Redis. */
    @Mock
    private RedisFuture<String> redisFuture;

    /** Plantilla de Redis. */
    @Mock
    private RedisTemplate<String, Object> redisTemplate;

    /** Verificador de salud bajo prueba. */
    @InjectMocks
    private RedisHealthChecker healthChecker;

    /**
     * Prepara la prueba.
     */
    @BeforeEach
    void setUp() {
        lenient().when(redisTemplate.getConnectionFactory()).thenReturn(connectionFactory);
        lenient().when(connectionFactory.getConnection()).thenReturn(connection);
    }

    /**
     * Crea cadena de información de Redis de simulado.
     *
     * @return cadena de información
     */
    private String createMockRedisInfoString() {
        return """
            # Server
            redis_version:7.0.0
            redis_mode:standalone
            os:Linux 5.10.0-28-amd64 x86_64
            arch_bits:64

            # Clients
            connected_clients:5

            # Memory
            used_memory:1048576
            used_memory_human:1.00M
            used_memory_rss:2097152
            maxmemory:2097152
            maxmemory_human:2.00M
            total_system_memory:2097152

            # Stats
            total_connections_received:100
            total_commands_processed:1000
            """;
    }


    /**
     * Con Redis activo debe indicar que está conectado.
     */
    @Test
    void isRedisActive_WhenRedisIsUp_ShouldReturnConnectedTrue() throws Exception {
        when(connection.ping()).thenReturn("PONG");
        when(connection.getNativeConnection()).thenReturn(asyncCommands);
        when(asyncCommands.info()).thenReturn(redisFuture);
        when(redisFuture.get(anyLong(), any(TimeUnit.class))).thenReturn(createMockRedisInfoString());

        RedisStatusDto result = healthChecker.isRedisActive();

        assertNotNull(result);
        assertTrue(result.isConnected());
        assertEquals(1048576L, result.getUsedMemory());
        assertEquals(2097152L, result.getMaxMemory());
        assertEquals(5, result.getConnectedClients());
        assertEquals("7.0.0", result.getRedisVersion());
        verify(connection).ping();
        verify(connection).getNativeConnection();
    }

    /**
     * Con Redis activo debe medir el tiempo de respuesta.
     */
    @Test
    void isRedisActive_WhenRedisIsUp_ShouldMeasureResponseTime() throws Exception {
        when(connection.ping()).thenReturn("PONG");
        when(connection.getNativeConnection()).thenReturn(asyncCommands);
        when(asyncCommands.info()).thenReturn(redisFuture);
        when(redisFuture.get(anyLong(), any(TimeUnit.class))).thenReturn(createMockRedisInfoString());

        RedisStatusDto result = healthChecker.isRedisActive();

        assertNotNull(result);
        assertTrue(result.isConnected());
        assertTrue(result.getResponseTime() >= 0, "Response time should be non-negative");
    }

    /**
     * Cuando el PING tiene éxito debe devolver un mensaje de éxito.
     */
    @Test
    void isRedisActive_WhenPingSucceeds_ShouldReturnSuccessMessage() throws Exception {
        when(connection.ping()).thenReturn("PONG");
        when(connection.getNativeConnection()).thenReturn(asyncCommands);
        when(asyncCommands.info()).thenReturn(redisFuture);
        when(redisFuture.get(anyLong(), any(TimeUnit.class))).thenReturn(createMockRedisInfoString());

        RedisStatusDto result = healthChecker.isRedisActive();

        assertNotNull(result);
        assertTrue(result.isConnected());
        assertNotNull(result.getErrorMessage());
        assertTrue(result.getErrorMessage().contains("Redis is up and running") ||
                result.getErrorMessage().contains("active") ||
                result.getErrorMessage().contains("connected"),
                "Message should indicate successful connection");
    }

    /**
     * Con la plantilla de Redis nula debe indicar que no está conectado.
     */
    @Test
    void isRedisActive_WhenRedisTemplateIsNull_ShouldReturnNotConnected() {
        RedisHealthChecker checkerWithNullTemplate = new RedisHealthChecker(null);

        RedisStatusDto result = checkerWithNullTemplate.isRedisActive();

        assertNotNull(result);
        assertFalse(result.isConnected());
        assertTrue(result.getErrorMessage().contains("not configured"));
    }

    /**
     * Con la fábrica de conexiones nula debe indicar que no está conectado.
     */
    @Test
    void isRedisActive_WhenConnectionFactoryIsNull_ShouldReturnNotConnected() {
        when(redisTemplate.getConnectionFactory()).thenReturn(null);

        RedisStatusDto result = healthChecker.isRedisActive();

        assertNotNull(result);
        assertFalse(result.isConnected());
        assertTrue(result.getErrorMessage().contains("not available"));
    }

    /**
     * Con Redis caído debe indicar que no está conectado.
     */
    @SuppressWarnings("serial")
	@Test
    void isRedisActive_WhenRedisIsDown_ShouldReturnConnectedFalse() {
        when(connectionFactory.getConnection()).thenThrow(new DataAccessException("Connection refused") {});

        RedisStatusDto result = healthChecker.isRedisActive();

        assertNotNull(result);
        assertFalse(result.isConnected());
    }

    /**
     * Con Redis caído debe incluir el mensaje de error.
     */
    @SuppressWarnings("serial")
	@Test
    void isRedisActive_WhenRedisIsDown_ShouldIncludeErrorMessage() {
        String errorMessage = "Connection refused";
        when(connectionFactory.getConnection()).thenThrow(new DataAccessException(errorMessage) {});

        RedisStatusDto result = healthChecker.isRedisActive();

        assertNotNull(result);
        assertFalse(result.isConnected());
        assertNotNull(result.getErrorMessage());
        assertTrue(result.getErrorMessage().contains("Error checking Redis status"));
    }

    /**
     * Cuando se lanza una excepción debe devolver tiempo de respuesta cero.
     */
    @SuppressWarnings("serial")
	@Test
    void isRedisActive_WhenExceptionThrown_ShouldReturnZeroResponseTime() {
        when(connectionFactory.getConnection()).thenThrow(new DataAccessException("Connection error") {});

        RedisStatusDto result = healthChecker.isRedisActive();

        assertNotNull(result);
        assertFalse(result.isConnected());
        assertEquals(0L, result.getResponseTime());
    }

    /**
     * Cuando el PING devuelve algo distinto de PONG debe indicar que no está conectado.
     */
    @Test
    void isRedisActive_WhenPingReturnsNonPong_ShouldReturnNotConnected() throws Exception {
        when(connection.ping()).thenReturn("INVALID");
        when(connection.getNativeConnection()).thenReturn(asyncCommands);
        when(asyncCommands.info()).thenReturn(redisFuture);
        when(redisFuture.get(anyLong(), any(TimeUnit.class))).thenReturn(createMockRedisInfoString());

        RedisStatusDto result = healthChecker.isRedisActive();

        assertNotNull(result);
        assertFalse(result.isConnected());
        assertTrue(result.getErrorMessage().contains("not responding as expected"));
    }

    /**
     * Cuando el PING devuelve nulo debe manejarlo sin fallar.
     */
    @Test
    void isRedisActive_WhenPingReturnsNull_ShouldHandleGracefully() throws Exception {
        when(connection.ping()).thenReturn(null);
        when(connection.getNativeConnection()).thenReturn(asyncCommands);
        when(asyncCommands.info()).thenReturn(redisFuture);
        when(redisFuture.get(anyLong(), any(TimeUnit.class))).thenReturn(createMockRedisInfoString());

        RedisStatusDto result = healthChecker.isRedisActive();

        assertNotNull(result);
        assertFalse(result.isConnected());
    }

    /**
     * Cuando el PING devuelve una cadena vacía debe manejarlo sin fallar.
     */
    @Test
    void isRedisActive_WhenPingReturnsEmptyString_ShouldHandleGracefully() throws Exception {
        when(connection.ping()).thenReturn("");
        when(connection.getNativeConnection()).thenReturn(asyncCommands);
        when(asyncCommands.info()).thenReturn(redisFuture);
        when(redisFuture.get(anyLong(), any(TimeUnit.class))).thenReturn(createMockRedisInfoString());

        RedisStatusDto result = healthChecker.isRedisActive();

        assertNotNull(result);
        assertFalse(result.isConnected());
    }

    /**
     * Cuando el PING devuelve pong en minúsculas debe indicar que está conectado.
     */
    @Test
    void isRedisActive_WhenPingReturnsPongLowercase_ShouldReturnConnected() throws Exception {
        when(connection.ping()).thenReturn("pong");
        when(connection.getNativeConnection()).thenReturn(asyncCommands);
        when(asyncCommands.info()).thenReturn(redisFuture);
        when(redisFuture.get(anyLong(), any(TimeUnit.class))).thenReturn(createMockRedisInfoString());

        RedisStatusDto result = healthChecker.isRedisActive();

        assertNotNull(result);
        assertTrue(result.isConnected());
    }

    /**
     * Cuando INFO trae valores de memoria inválidos debe manejarlo sin fallar.
     */
    @Test
    void isRedisActive_WhenRedisInfoHasInvalidMemoryValues_ShouldHandleGracefully() throws Exception {
        String infoWithInvalidNumbers = """
            # Server
            redis_version:7.0.0

            # Clients
            connected_clients:invalid_number

            # Memory
            used_memory:not_a_number
            total_system_memory:invalid
            """;

        when(connection.ping()).thenReturn("PONG");
        when(connection.getNativeConnection()).thenReturn(asyncCommands);
        when(asyncCommands.info()).thenReturn(redisFuture);
        when(redisFuture.get(anyLong(), any(TimeUnit.class))).thenReturn(infoWithInvalidNumbers);

        RedisStatusDto result = healthChecker.isRedisActive();

        assertNotNull(result);
        assertTrue(result.isConnected());
        assertEquals(0L, result.getUsedMemory());
        assertEquals(0L, result.getMaxMemory());
        assertEquals(0, result.getConnectedClients());
    }

    /**
     * Cuando INFO trae líneas mal formadas debe manejarlo sin fallar.
     */
    @Test
    void isRedisActive_WhenRedisInfoHasMalformedLines_ShouldHandleGracefully() throws Exception {
        String infoWithMalformedLines = """
            # Server
            redis_version:7.0.0

            # Invalid lines
            line_without_colon
            :value_without_key

            # Clients
            connected_clients:5

            # Memory
            used_memory:1048576
            total_system_memory:2097152
            """;

        when(connection.ping()).thenReturn("PONG");
        when(connection.getNativeConnection()).thenReturn(asyncCommands);
        when(asyncCommands.info()).thenReturn(redisFuture);
        when(redisFuture.get(anyLong(), any(TimeUnit.class))).thenReturn(infoWithMalformedLines);

        RedisStatusDto result = healthChecker.isRedisActive();

        assertNotNull(result);
        assertTrue(result.isConnected());
        assertEquals("7.0.0", result.getRedisVersion());
        assertEquals(5, result.getConnectedClients());
    }

    /**
     * Cuando INFO está vacío debe manejarlo sin fallar.
     */
    @Test
    void isRedisActive_WhenRedisInfoIsEmptyString_ShouldHandleGracefully() throws Exception {
        when(connection.ping()).thenReturn("PONG");
        when(connection.getNativeConnection()).thenReturn(asyncCommands);
        when(asyncCommands.info()).thenReturn(redisFuture);
        when(redisFuture.get(anyLong(), any(TimeUnit.class))).thenReturn("");

        RedisStatusDto result = healthChecker.isRedisActive();

        assertNotNull(result);
        assertTrue(result.isConnected());
        assertEquals(0L, result.getUsedMemory());
        assertEquals(0L, result.getMaxMemory());
        assertEquals(0, result.getConnectedClients());
        assertEquals("", result.getRedisVersion());
    }

    /**
     * Cuando INFO con la conexión nativa agota el tiempo debe manejarlo sin fallar.
     */
    @Test
    void isRedisActive_WhenNativeConnectionInfoTimesOut_ShouldHandleGracefully() throws Exception {
        when(connection.ping()).thenReturn("PONG");
        when(connection.getNativeConnection()).thenReturn(asyncCommands);
        when(asyncCommands.info()).thenReturn(redisFuture);
        when(redisFuture.get(anyLong(), any(TimeUnit.class)))
                .thenThrow(new java.util.concurrent.TimeoutException("Timeout"));

        RedisStatusDto result = healthChecker.isRedisActive();

        assertNotNull(result);
        assertTrue(result.isConnected());
        assertEquals(0L, result.getUsedMemory());
        assertEquals(0L, result.getMaxMemory());
    }

    /**
     * Cuando INFO con la conexión nativa lanza ExecutionException
     * debe manejarlo sin fallar.
     */
    @Test
    void isRedisActive_WhenNativeConnectionInfoThrowsExecutionException_ShouldHandleGracefully()
            throws Exception {
        when(connection.ping()).thenReturn("PONG");
        when(connection.getNativeConnection()).thenReturn(asyncCommands);
        when(asyncCommands.info()).thenReturn(redisFuture);
        when(redisFuture.get(anyLong(), any(TimeUnit.class)))
                .thenThrow(new java.util.concurrent.ExecutionException("Execution failed", new Exception()));

        RedisStatusDto result = healthChecker.isRedisActive();

        assertNotNull(result);
        assertTrue(result.isConnected());
        assertEquals(0L, result.getUsedMemory());
        assertEquals(0L, result.getMaxMemory());
    }
}
