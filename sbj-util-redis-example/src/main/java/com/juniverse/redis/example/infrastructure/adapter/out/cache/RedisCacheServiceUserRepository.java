package com.juniverse.redis.example.infrastructure.adapter.out.cache;

import com.juniverse.redis.example.domain.model.User;
import com.juniverse.redis.example.domain.port.out.UserCachePort;
import com.juniverse.redis.example.domain.port.out.UserRepositoryPort;
import io.github.programming0026.juniverse.redis.service.RedisCacheService;
import java.time.Duration;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.CacheManager;

/**
 * Outbound adapter that decorates the users storage with a Redis cache using
 * {@link RedisCacheService} (programmatic cache control).
 *
 * @author Java Universe
 */
@Slf4j
public class RedisCacheServiceUserRepository implements UserRepositoryPort, UserCachePort {

    static final String CACHE_NAME = "users";
    static final String ALL_KEY = "all";

    private final UserRepositoryPort delegate;
    private final RedisCacheService redisCacheService;
    private final CacheManager cacheManager;

    /**
     * Constructor for dependency injection
     * @param delegate real users storage
     * @param redisCacheService Redis cache service
     * @param cacheManager cache manager
     */
    public RedisCacheServiceUserRepository(UserRepositoryPort delegate, RedisCacheService redisCacheService,
            CacheManager cacheManager) {
        this.delegate = delegate;
        this.redisCacheService = redisCacheService;
        this.cacheManager = cacheManager;
    }

    @Override
    public Optional<User> findById(Long id) {
        return Optional.ofNullable(redisCacheService.cacheable(
                CACHE_NAME,
                String.valueOf(id),
                () -> {
                    log.info("Cache miss for user ID: {} - loading from database", id);
                    return delegate.findById(id).orElse(null);
                },
                Duration.ofMinutes(30)));
    }

    @Override
    public List<User> findAll() {
        try {
            List<User> users = redisCacheService.cacheable(
                    CACHE_NAME,
                    ALL_KEY,
                    () -> {
                        log.info("Cache miss - loading all users from database");
                        return delegate.findAll();
                    },
                    Duration.ofMinutes(15));
            if (users != null) {
                log.info("Retrieved {} users (from cache or database)", users.size());
            }
            return users;
        } catch (Exception ex) {
            log.error("Error loading users: ", ex);
            return Collections.emptyList();
        }
    }

    @Override
    public boolean existsById(Long id) {
        return delegate.existsById(id);
    }

    @Override
    public User save(User user) {
        User saved = delegate.save(user);
        redisCacheService.cachePut(CACHE_NAME, String.valueOf(saved.getId()), () -> saved, Duration.ofMinutes(30));
        redisCacheService.cacheEvict(CACHE_NAME, ALL_KEY);
        log.info("User saved and cached with ID: {}", saved.getId());
        return saved;
    }

    @Override
    public boolean deleteById(Long id) {
        boolean deleted = delegate.deleteById(id);
        if (deleted) {
            evict(id);
            redisCacheService.cacheEvict(CACHE_NAME, ALL_KEY);
            log.info("Cache evicted for user ID: {}", id);
        }
        return deleted;
    }

    @Override
    public void evict(Long id) {
        log.info("Evicting cached user ID: {}", id);
        redisCacheService.cacheEvict(CACHE_NAME, String.valueOf(id));
    }

    @Override
    public void evictAll() {
        log.info("Clearing entire users cache.");
        Objects.requireNonNull(cacheManager.getCache(CACHE_NAME)).clear();
    }
}
