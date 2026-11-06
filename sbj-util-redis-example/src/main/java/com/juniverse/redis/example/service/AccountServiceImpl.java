package com.juniverse.redis.example.service;

import com.juniverse.redis.service.CacheOperationBuilder;
import com.juniverse.redis.example.dto.AccountDTO;
import java.math.BigDecimal;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;
import java.util.stream.IntStream;

import com.juniverse.redis.example.util.Utility;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.CacheManager;
import org.springframework.stereotype.Service;

/**
 * Implementación del servicio de cuentas que muestra el uso de CacheOperationBuilder<T> de la librería sbj-util-redis
 * Este servicio muestra el patrón constructor fluido con fábrica para las operaciones de caché en Redis
 *
 * @author Java Universe
 */
@Service
@Slf4j
public class AccountServiceImpl implements AccountService {

    private final CacheManager cacheManager;
    private final CacheOperationBuilder.Factory builderFactory;
    private static final String CACHE_NAME = "accounts";
    private static final Map<Long, AccountDTO> accounts = new ConcurrentHashMap<>();
    private static final AtomicLong idGenerator = new AtomicLong(10);

    /**
     * Constructor para la inyección de dependencias
     * @param cacheManager administrador de caché
     * @param builderFactory fábrica de constructor de operación de caché
     */
    public AccountServiceImpl(CacheManager cacheManager, CacheOperationBuilder.Factory builderFactory) {
        this.cacheManager = cacheManager;
        this.builderFactory = builderFactory;
        createAccounts();
    }

    /**
     * Inicializa cuentas de ejemplo en memoria
     */
    private void createAccounts() {
        String[] accountTypes = {"SAVINGS", "CHECKING", "INVESTMENT"};
        IntStream.rangeClosed(1, 10)
                .forEach(i -> accounts.put((long) i,
                        new AccountDTO(
                                (long) i,
                                "ACC" + String.format("%06d", i),
                                "Account Holder " + i,
                                BigDecimal.valueOf(1000.00 * i),
                                accountTypes[i % 3],
                                LocalDateTime.now()
                        )));
    }

    /**
     * Obtiene una cuenta por identificador, con actualización forzada opcional
     * Muestra CacheOperationBuilder.Factory con caché condicional y retrollamadas
     *
     * Ejemplo de uso de la librería sbj-util-redis:
     * return builderFactory.create()
     *   .cacheName("accounts")
     *   .key(accountId)
     *   .loader(() -> accountRepository.findById(accountId))
     *   .ttl(Duration.ofMinutes(30))
     *   .condition(!forceRefresh)
     *   .onCacheHit(account -> log.info("Cache hit"))
     *   .onCacheMiss(account -> log.info("Cache miss"))
     *   .cacheable();
     */
    @Override
    public AccountDTO getAccountById(Long id, boolean forceRefresh) {
        return builderFactory.<AccountDTO>create()
                .cacheName(CACHE_NAME)
                .key(String.valueOf(id))
                .loader(() -> {
                    Utility.simulateSlowService();
                    return accounts.get(id);
                })
                .ttl(Duration.ofMinutes(20))
                .condition(!forceRefresh)
                .onCacheHit(account -> log.info("Cache hit for account ID: {}, Account#: {}",
                        id, account.getAccountNumber()))
                .onCacheMiss(account -> log.info("Cache miss for account ID: {} - loaded from database", id))
                .cacheable();
    }

    /**
     * Guarda o actualiza una cuenta
     * Muestra CacheOperationBuilder.Factory con la operación cachePut y efectos secundarios
     */
    @Override
    public AccountDTO saveOrUpdateAccount(AccountDTO account) {
        log.info("Saving/updating account in database...");

        Utility.simulateSlowService();

        if (account.getId() != null && accounts.containsKey(account.getId())) {
            accounts.put(account.getId(), account);
            log.info("Updated existing account: {}", account.getAccountNumber());
        } else {
            Long id = idGenerator.incrementAndGet();
            account.setId(id);
            account.setCreatedAt(LocalDateTime.now());
            accounts.put(id, account);
            log.info("Created new account with ID: {}, account#: {}", id, account.getAccountNumber());
        }

        builderFactory.<AccountDTO>create()
                .cacheName(CACHE_NAME)
                .key(String.valueOf(account.getId()))
                .loader(() -> account)
                .ttl(Duration.ofMinutes(20))
                .onCacheHit(acc -> log.info("Cache updated for account: {} with balance: {}",
                        acc.getAccountNumber(), acc.getBalance()))
                .cachePut();

        builderFactory.<AccountDTO>create()
                .cacheName(CACHE_NAME)
                .key("all")
                .cacheEvict();

        return account;
    }

    /**
     * Elimina una cuenta
     * Muestra CacheOperationBuilder.Factory con la operación cacheEvict
     * @return {@code true} si se eliminó la cuenta; {@code false} si no existía
     */
    @Override
    public boolean deleteAccount(Long id) {
        if (accounts.containsKey(id)) {
            String accountNumber = accounts.get(id).getAccountNumber();
            log.info("Deleting account {} with ID: {}", accountNumber, id);
            accounts.remove(id);

            builderFactory.<AccountDTO>create()
                    .cacheName(CACHE_NAME)
                    .key(String.valueOf(id))
                    .cacheEvict();

            builderFactory.<AccountDTO>create()
                    .cacheName(CACHE_NAME)
                    .key("all")
                    .cacheEvict();

            log.info("Cache evicted for account ID: {}", id);
            return true;
        }
        return false;
    }

    /**
     * Limpia toda la caché de cuentas con CacheManager
     */
    @Override
    public void clearCache() {
        log.info("Clearing entire accounts cache.");
        Objects.requireNonNull(cacheManager.getCache(CACHE_NAME)).clear();
    }

    /**
     * Obtiene todas las cuentas
     * Muestra CacheOperationBuilder.Factory en operaciones de lista con varias retrollamadas
     */
    @Override
    public List<AccountDTO> getAccounts() {
        return builderFactory.<List<AccountDTO>>create()
                .cacheName(CACHE_NAME)
                .key("all")
                .loader(() -> {
                    try {
                        Utility.simulateSlowService();
                        List<AccountDTO> list = new ArrayList<>(accounts.values());
                        log.info("Loaded {} accounts from database", list.size());
                        return list;
                    } catch (Exception ex) {
                        log.error("Error loading accounts: ", ex);
                        return Collections.emptyList();
                    }
                })
                .ttl(Duration.ofMinutes(10))
                .onCacheHit(list -> {
                    BigDecimal totalBalance = list.stream()
                            .map(AccountDTO::getBalance)
                            .reduce(BigDecimal.ZERO, BigDecimal::add);
                    log.info("Cache hit for all accounts - {} accounts, Total balance: ${}",
                            list.size(), totalBalance);
                })
                .onCacheMiss(list -> {
                    BigDecimal totalBalance = list.stream()
                            .map(AccountDTO::getBalance)
                            .reduce(BigDecimal.ZERO, BigDecimal::add);
                    log.info("Cache miss - loaded {} accounts from database, Total balance: ${}",
                            list.size(), totalBalance);
                })
                .cacheable();
    }

}
