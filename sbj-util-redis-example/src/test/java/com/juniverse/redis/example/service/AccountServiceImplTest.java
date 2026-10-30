package com.juniverse.redis.example.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.juniverse.redis.example.dto.AccountDTO;
import com.juniverse.redis.service.CacheOperationBuilder;
import com.juniverse.redis.service.RedisCacheService;
import java.math.BigDecimal;
import java.time.Duration;
import java.util.List;
import java.util.function.Supplier;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.cache.Cache;
import org.springframework.cache.CacheManager;

/**
 * Usa el CacheOperationBuilder.Factory real de la librería sobre un RedisCacheService simulado: así se comprueba
 * también que el servicio construye bien las operaciones (nombre de caché, clave, TTL y condición).
 */
class AccountServiceImplTest {

    private CacheManager cacheManager;
    private RedisCacheService redisCacheService;
    private AccountServiceImpl service;

    @BeforeEach
    @SuppressWarnings("unchecked")
    void setUp() {
        cacheManager = mock(CacheManager.class);
        redisCacheService = mock(RedisCacheService.class);
        service = new AccountServiceImpl(cacheManager, new CacheOperationBuilder.Factory(redisCacheService));
        when(redisCacheService.cacheableWithResult(any(), any(), any(Supplier.class), any(Duration.class)))
                .thenAnswer(invocation -> new com.juniverse.redis.dto.CacheResult<>(
                        ((Supplier<?>) invocation.getArgument(2)).get(), false));
    }

    @Test
    @SuppressWarnings("unchecked")
    void loadsAnAccountThroughTheBuilderWithTheConfiguredTtl() {
        AccountDTO account = service.getAccountById(1L, false);

        assertEquals("ACC000001", account.getAccountNumber());
        assertEquals(0, BigDecimal.valueOf(1000.0).compareTo(account.getBalance()));
        verify(redisCacheService).cacheableWithResult(eq("accounts"), eq("1"), any(Supplier.class),
                eq(Duration.ofMinutes(20)));
    }

    @Test
    @SuppressWarnings("unchecked")
    void forceRefreshSkipsTheCache() {
        service.getAccountById(2L, true);

        verify(redisCacheService, never()).cacheableWithResult(any(), any(), any(Supplier.class), any(Duration.class));
    }

    @Test
    void listAggregatesAllAccounts() {
        List<AccountDTO> accounts = service.getAccounts();

        assertTrue(accounts.size() >= 10);
    }

    @Test
    void savingAssignsAnIdAndUpdatesTheCache() {
        AccountDTO saved = service.saveOrUpdateAccount(
                new AccountDTO(null, "ACC-NEW", "Titular", BigDecimal.TEN, "SAVINGS", null));

        assertNotNull(saved.getId());
        assertNotNull(saved.getCreatedAt());
        verify(redisCacheService).cacheEvict("accounts", "all");
    }

    @Test
    void deleteEvictsOnlyIfTheAccountExists() {
        AccountDTO saved = service.saveOrUpdateAccount(
                new AccountDTO(null, "ACC-DEL", "Titular", BigDecimal.ONE, "CHECKING", null));

        assertTrue(service.deleteAccount(saved.getId()));
        verify(redisCacheService).cacheEvict("accounts", String.valueOf(saved.getId()));
        assertFalse(service.deleteAccount(saved.getId()));
    }

    @Test
    void clearCacheClearsTheAccountsCache() {
        Cache cache = mock(Cache.class);
        when(cacheManager.getCache("accounts")).thenReturn(cache);

        service.clearCache();

        verify(cache).clear();
    }
}
