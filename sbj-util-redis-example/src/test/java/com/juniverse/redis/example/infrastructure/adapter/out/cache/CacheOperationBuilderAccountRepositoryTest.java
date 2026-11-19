package com.juniverse.redis.example.infrastructure.adapter.out.cache;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.juniverse.redis.example.domain.model.Account;
import com.juniverse.redis.example.infrastructure.adapter.out.persistence.InMemoryAccountRepository;
import io.github.programming0026.juniverse.redis.dto.CacheResult;
import io.github.programming0026.juniverse.redis.service.CacheOperationBuilder;
import io.github.programming0026.juniverse.redis.service.RedisCacheService;
import java.math.BigDecimal;
import java.time.Duration;
import java.util.List;
import java.util.function.Supplier;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.cache.Cache;
import org.springframework.cache.CacheManager;

/**
 * Uses the real CacheOperationBuilder.Factory from the library on top of a mocked RedisCacheService, which also
 * verifies that the adapter builds the operations correctly (cache name, key and TTL).
 */
public class CacheOperationBuilderAccountRepositoryTest {

    private CacheManager cacheManager;
    private RedisCacheService redisCacheService;
    private CacheOperationBuilderAccountRepository repository;

    @BeforeEach
    @SuppressWarnings("unchecked")
    public void setUp() {
        cacheManager = mock(CacheManager.class);
        redisCacheService = mock(RedisCacheService.class);
        repository = new CacheOperationBuilderAccountRepository(new InMemoryAccountRepository(),
                new CacheOperationBuilder.Factory(redisCacheService), cacheManager);
        // "empty cache": each cacheable operation runs the loader
        when(redisCacheService.cacheableWithResult(any(), any(), any(Supplier.class), any(Duration.class)))
                .thenAnswer(invocation -> new CacheResult<>(((Supplier<?>) invocation.getArgument(2)).get(), false));
    }

    @Test
    @SuppressWarnings("unchecked")
    public void loadsAnAccountThroughTheBuilderWithTheConfiguredTtl() {
        Account account = repository.findById(1L).orElseThrow();

        assertEquals("ACC000001", account.getAccountNumber());
        assertEquals(0, BigDecimal.valueOf(1000.0).compareTo(account.getBalance()));
        verify(redisCacheService).cacheableWithResult(eq("accounts"), eq("1"), any(Supplier.class),
                eq(Duration.ofMinutes(20)));
    }

    @Test
    @SuppressWarnings("unchecked")
    public void listIsCachedUnderTheAllKey() {
        List<Account> accounts = repository.findAll();

        assertTrue(accounts.size() >= 10);
        verify(redisCacheService).cacheableWithResult(eq("accounts"), eq("all"), any(Supplier.class),
                eq(Duration.ofMinutes(10)));
    }

    @Test
    public void savingAssignsAnIdAndEvictsTheList() {
        Account saved = repository.save(new Account(null, "ACC-NEW", "Holder", BigDecimal.TEN, "SAVINGS", null));

        assertNotNull(saved.getId());
        verify(redisCacheService).cacheEvict("accounts", "all");
    }

    @Test
    public void deleteEvictsOnlyIfTheAccountExists() {
        Account saved = repository.save(new Account(null, "ACC-DEL", "Holder", BigDecimal.ONE, "CHECKING", null));

        assertTrue(repository.deleteById(saved.getId()));
        verify(redisCacheService).cacheEvict("accounts", String.valueOf(saved.getId()));
        assertFalse(repository.deleteById(saved.getId()));
    }

    @Test
    public void evictDiscardsOneEntry() {
        repository.evict(3L);

        verify(redisCacheService).cacheEvict("accounts", "3");
    }

    @Test
    public void evictAllClearsTheAccountsCache() {
        Cache cache = mock(Cache.class);
        when(cacheManager.getCache("accounts")).thenReturn(cache);

        repository.evictAll();

        verify(cache).clear();
    }
}
