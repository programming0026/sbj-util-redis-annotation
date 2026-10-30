package com.juniverse.redis.example.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.juniverse.redis.example.dto.UserDTO;
import com.juniverse.redis.service.RedisCacheService;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;
import java.util.function.Supplier;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.cache.Cache;
import org.springframework.cache.CacheManager;

/** Con RedisCacheService simulado: comprueba cómo el servicio usa la librería, sin necesitar un Redis. */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class UserServiceImplTest {

    @Mock
    private CacheManager cacheManager;

    @Mock
    private RedisCacheService redisCacheService;

    private UserServiceImpl service;

    @BeforeEach
    @SuppressWarnings("unchecked")
    void setUp() {
        service = new UserServiceImpl(cacheManager, redisCacheService);
        // "cache vacía": cada cacheable() ejecuta el cargador que le pasa el servicio
        when(redisCacheService.cacheable(any(), any(), any(Supplier.class), any(Duration.class)))
                .thenAnswer(invocation -> ((Supplier<?>) invocation.getArgument(2)).get());
    }

    @Test
    void cacheMissLoadsTheUserFromTheStore() {
        UserDTO user = service.getUserById(1L, false);

        assertEquals("user1", user.getUsername());
        verify(redisCacheService).cacheable(eq("users"), eq("1"), any(Supplier.class), eq(Duration.ofMinutes(30)));
        verify(redisCacheService, never()).cacheEvict(any(), any());
    }

    @Test
    void forceRefreshEvictsTheEntryFirst() {
        service.getUserById(2L, true);

        verify(redisCacheService).cacheEvict("users", "2");
        verify(redisCacheService).cacheable(eq("users"), eq("2"), any(Supplier.class), any(Duration.class));
    }

    @Test
    void cacheHitReturnsWhatTheCacheHolds() {
        UserDTO cached = new UserDTO(7L, "en-caché", "c@example.com", LocalDateTime.now());
        when(redisCacheService.cacheable(eq("users"), eq("7"), any(Supplier.class), any(Duration.class)))
                .thenReturn(cached);

        assertEquals("en-caché", service.getUserById(7L, false).getUsername());
    }

    @Test
    void listIsCachedUnderTheAllKey() {
        List<UserDTO> users = service.getUsers();

        assertTrue(users.size() >= 10);
        verify(redisCacheService).cacheable(eq("users"), eq("all"), any(Supplier.class), eq(Duration.ofMinutes(15)));
    }

    @Test
    void listReturnsEmptyWhenTheCacheFails() {
        when(redisCacheService.cacheable(eq("users"), eq("all"), any(Supplier.class), any(Duration.class)))
                .thenThrow(new IllegalStateException("redis caído"));

        assertTrue(service.getUsers().isEmpty());
    }

    @Test
    void savingCreatesTheUserPutsItInCacheAndEvictsTheList() {
        UserDTO saved = service.saveOrUpdateUser(new UserDTO(null, "nuevo", "n@example.com", null));

        assertNotNull(saved.getId());
        assertNotNull(saved.getCreatedAt());
        verify(redisCacheService).cachePut(eq("users"), eq(String.valueOf(saved.getId())), any(Supplier.class),
                eq(Duration.ofMinutes(30)));
        verify(redisCacheService).cacheEvict("users", "all");
    }

    @Test
    void savingWithExistingIdUpdatesIt() {
        UserDTO created = service.saveOrUpdateUser(new UserDTO(null, "antes", "a@example.com", null));

        service.saveOrUpdateUser(new UserDTO(created.getId(), "después", "d@example.com", created.getCreatedAt()));

        assertEquals("después", service.getUserById(created.getId(), false).getUsername());
    }

    @Test
    void deleteEvictsTheEntryAndTheListOnlyIfTheUserExists() {
        UserDTO created = service.saveOrUpdateUser(new UserDTO(null, "borrar", "b@example.com", null));

        assertTrue(service.deleteUser(created.getId()));
        verify(redisCacheService).cacheEvict("users", String.valueOf(created.getId()));

        assertFalse(service.deleteUser(created.getId()));
        assertNull(service.getUserById(created.getId(), false));
    }

    @Test
    void clearCacheClearsTheUsersCache() {
        Cache cache = org.mockito.Mockito.mock(Cache.class);
        when(cacheManager.getCache("users")).thenReturn(cache);

        service.clearCache();

        verify(cache).clear();
    }
}
