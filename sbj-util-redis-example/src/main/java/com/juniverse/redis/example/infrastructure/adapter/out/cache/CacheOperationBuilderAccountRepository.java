package com.juniverse.redis.example.infrastructure.adapter.out.cache;

import com.juniverse.redis.example.domain.model.Account;
import com.juniverse.redis.example.domain.port.out.AccountCachePort;
import com.juniverse.redis.example.domain.port.out.AccountRepositoryPort;
import io.github.programming0026.juniverse.redis.service.CacheOperationBuilder;
import java.math.BigDecimal;
import java.time.Duration;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.CacheManager;

/**
 * Outbound adapter that decorates the accounts storage with a Redis cache built with
 * {@link CacheOperationBuilder.Factory} (fluent builder with hit/miss callbacks).
 *
 * @author Java Universe
 */
@Slf4j
public class CacheOperationBuilderAccountRepository implements AccountRepositoryPort, AccountCachePort {

    static final String CACHE_NAME = "accounts";
    static final String ALL_KEY = "all";

    private final AccountRepositoryPort delegate;
    private final CacheOperationBuilder.Factory builderFactory;
    private final CacheManager cacheManager;

    /**
     * Constructor for dependency injection
     * @param delegate real accounts storage
     * @param builderFactory cache operation builder factory
     * @param cacheManager cache manager
     */
    public CacheOperationBuilderAccountRepository(AccountRepositoryPort delegate,
            CacheOperationBuilder.Factory builderFactory, CacheManager cacheManager) {
        this.delegate = delegate;
        this.builderFactory = builderFactory;
        this.cacheManager = cacheManager;
    }

    @Override
    public Optional<Account> findById(Long id) {
        return Optional.ofNullable(builderFactory.<Account>create()
                .cacheName(CACHE_NAME)
                .key(String.valueOf(id))
                .loader(() -> delegate.findById(id).orElse(null))
                .ttl(Duration.ofMinutes(20))
                .onCacheHit(account -> log.info("Cache hit for account ID: {}, Account#: {}",
                        id, account.getAccountNumber()))
                .onCacheMiss(account -> log.info("Cache miss for account ID: {} - loaded from database", id))
                .cacheable());
    }

    @Override
    public List<Account> findAll() {
        return builderFactory.<List<Account>>create()
                .cacheName(CACHE_NAME)
                .key(ALL_KEY)
                .loader(delegate::findAll)
                .ttl(Duration.ofMinutes(10))
                .onCacheHit(list -> log.info("Cache hit for all accounts - {} accounts, Total balance: ${}",
                        list.size(), totalBalance(list)))
                .onCacheMiss(list -> log.info("Cache miss - loaded {} accounts from database, Total balance: ${}",
                        list.size(), totalBalance(list)))
                .cacheable();
    }

    @Override
    public boolean existsById(Long id) {
        return delegate.existsById(id);
    }

    @Override
    public Account save(Account account) {
        Account saved = delegate.save(account);

        builderFactory.<Account>create()
                .cacheName(CACHE_NAME)
                .key(String.valueOf(saved.getId()))
                .loader(() -> saved)
                .ttl(Duration.ofMinutes(20))
                .onCacheHit(acc -> log.info("Cache updated for account: {} with balance: {}",
                        acc.getAccountNumber(), acc.getBalance()))
                .cachePut();
        evictKey(ALL_KEY);
        return saved;
    }

    @Override
    public boolean deleteById(Long id) {
        boolean deleted = delegate.deleteById(id);
        if (deleted) {
            evict(id);
            evictKey(ALL_KEY);
            log.info("Cache evicted for account ID: {}", id);
        }
        return deleted;
    }

    @Override
    public void evict(Long id) {
        evictKey(String.valueOf(id));
    }

    @Override
    public void evictAll() {
        log.info("Clearing entire accounts cache.");
        Objects.requireNonNull(cacheManager.getCache(CACHE_NAME)).clear();
    }

    private void evictKey(String key) {
        builderFactory.<Account>create()
                .cacheName(CACHE_NAME)
                .key(key)
                .cacheEvict();
    }

    private static BigDecimal totalBalance(List<Account> accounts) {
        return accounts.stream().map(Account::getBalance).reduce(BigDecimal.ZERO, BigDecimal::add);
    }
}
