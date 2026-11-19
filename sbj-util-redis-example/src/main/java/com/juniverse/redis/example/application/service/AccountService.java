package com.juniverse.redis.example.application.service;

import com.juniverse.redis.example.domain.model.Account;
import com.juniverse.redis.example.domain.port.in.AccountUseCase;
import com.juniverse.redis.example.domain.port.out.AccountCachePort;
import com.juniverse.redis.example.domain.port.out.AccountRepositoryPort;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

/**
 * Application service that implements the account use cases.
 * It only knows the domain ports; the cache is applied by the outbound adapter.
 *
 * @author Java Universe
 */
public class AccountService implements AccountUseCase {

    private final AccountRepositoryPort repository;
    private final AccountCachePort cache;

    /**
     * Constructor for dependency injection
     * @param repository accounts storage port
     * @param cache accounts cache port
     */
    public AccountService(AccountRepositoryPort repository, AccountCachePort cache) {
        this.repository = repository;
        this.cache = cache;
    }

    @Override
    public Optional<Account> getAccountById(Long id, boolean forceRefresh) {
        if (forceRefresh) {
            cache.evict(id);
        }
        return repository.findById(id);
    }

    @Override
    public List<Account> getAccounts() {
        return repository.findAll();
    }

    @Override
    public boolean deleteAccount(Long id) {
        return repository.deleteById(id);
    }

    @Override
    public Account saveOrUpdateAccount(Account account) {
        if (account.getId() == null || !repository.existsById(account.getId())) {
            account.setId(null);
            account.setCreatedAt(LocalDateTime.now());
        }
        return repository.save(account);
    }

    @Override
    public void clearCache() {
        cache.evictAll();
    }
}
