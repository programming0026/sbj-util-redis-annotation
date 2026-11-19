package com.juniverse.redis.example.infrastructure.adapter.out.cache;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.juniverse.redis.example.domain.model.User;
import com.juniverse.redis.example.infrastructure.adapter.out.persistence.InMemoryUserRepository;
import io.github.programming0026.juniverse.redis.service.RedisCacheService;
import java.time.Duration;
import java.time.LocalDateTime;
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

/** With a mocked RedisCacheService: checks how the adapter uses the library, without needing a Redis. */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
public class RedisCacheServiceUserRepositoryTest {

    @Mock
    private CacheManager cacheManager;

    @Mock
    private RedisCacheService redisCacheService;

    private RedisCacheServiceUserRepository repository;

    @BeforeEach
    @SuppressWarnings("unchecked")
    public void setUp() {
        repository = new RedisCacheServiceUserRepository(new InMemoryUserRepository(), redisCacheService,
                cacheManager);
        // "empty cache": each cacheable() runs the loader passed in by the adapter
        when(redisCacheService.cacheable(any(), any(), any(Supplier.class), any(Duration.class)))
                .thenAnswer(invocation -> ((Supplier<?>) invocation.getArgument(2)).get());
    }

    @Test
    @SuppressWarnings("unchecked")
    public void cacheMissLoadsTheUserFromTheStore() {
        User user = repository.findById(1L).orElseThrow();

        assertEquals("user1", user.getUsername());
        verify(redisCacheService).cacheable(eq("users"), eq("1"), any(Supplier.class), eq(Duration.ofMinutes(30)));
    }

    @Test
    @SuppressWarnings("unchecked")
    public void cacheHitReturnsWhatTheCacheHolds() {
        User cached = new User(7L, "in-cache", "c@example.com", LocalDateTime.now());
        when(redisCacheService.cacheable(eq("users"), eq("7"), any(Supplier.class), any(Duration.class)))
                .thenReturn(cached);

        assertEquals("in-cache", repository.findById(7L).orElseThrow().getUsername());
    }

    @Test
    @SuppressWarnings("unchecked")
    public void listIsCachedUnderTheAllKey() {
        assertTrue(repository.findAll().size() >= 10);
        verify(redisCacheService).cacheable(eq("users"), eq("all"), any(Supplier.class), eq(Duration.ofMinutes(15)));
    }

    @Test
    @SuppressWarnings("unchecked")
    public void listReturnsEmptyWhenTheCacheFails() {
        when(redisCacheService.cacheable(eq("users"), eq("all"), any(Supplier.class), any(Duration.class)))
                .thenThrow(new IllegalStateException("redis down"));

        assertTrue(repository.findAll().isEmpty());
    }

    @Test
    @SuppressWarnings("unchecked")
    public void savingPutsTheUserInCacheAndEvictsTheList() {
        User saved = repository.save(new User(null, "new", "n@example.com", null));

        assertNotNull(saved.getId());
        verify(redisCacheService).cachePut(eq("users"), eq(String.valueOf(saved.getId())), any(Supplier.class),
                eq(Duration.ofMinutes(30)));
        verify(redisCacheService).cacheEvict("users", "all");
    }

    @Test
    public void deleteEvictsTheEntryOnlyIfTheUserExists() {
        User created = repository.save(new User(null, "delete", "b@example.com", null));

        assertTrue(repository.deleteById(created.getId()));
        verify(redisCacheService).cacheEvict("users", String.valueOf(created.getId()));

        assertFalse(repository.deleteById(created.getId()));
        assertTrue(repository.findById(created.getId()).isEmpty());
    }

    @Test
    public void evictAllClearsTheUsersCache() {
        Cache cache = org.mockito.Mockito.mock(Cache.class);
        when(cacheManager.getCache("users")).thenReturn(cache);

        repository.evictAll();

        verify(cache).clear();
    }
}
