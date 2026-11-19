package com.juniverse.redis.example.application.service;

import com.juniverse.redis.example.domain.model.User;
import com.juniverse.redis.example.domain.port.in.UserUseCase;
import com.juniverse.redis.example.domain.port.out.UserCachePort;
import com.juniverse.redis.example.domain.port.out.UserRepositoryPort;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

/**
 * Application service that implements the user use cases.
 * It only knows the domain ports; the cache is applied by the outbound adapter.
 *
 * @author Java Universe
 */
public class UserService implements UserUseCase {

    private final UserRepositoryPort repository;
    private final UserCachePort cache;

    /**
     * Constructor for dependency injection
     * @param repository users storage port
     * @param cache users cache port
     */
    public UserService(UserRepositoryPort repository, UserCachePort cache) {
        this.repository = repository;
        this.cache = cache;
    }

    @Override
    public Optional<User> getUserById(Long id, boolean forceRefresh) {
        if (forceRefresh) {
            cache.evict(id);
        }
        return repository.findById(id);
    }

    @Override
    public List<User> getUsers() {
        return repository.findAll();
    }

    @Override
    public boolean deleteUser(Long id) {
        return repository.deleteById(id);
    }

    @Override
    public User saveOrUpdateUser(User user) {
        if (user.getId() == null || !repository.existsById(user.getId())) {
            user.setId(null);
            user.setCreatedAt(LocalDateTime.now());
        }
        return repository.save(user);
    }

    @Override
    public void clearCache() {
        cache.evictAll();
    }
}
