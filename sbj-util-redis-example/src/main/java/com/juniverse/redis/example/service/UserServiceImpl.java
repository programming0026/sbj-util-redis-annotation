package com.juniverse.redis.example.service;

import io.github.programming0026.juniverse.redis.service.RedisCacheService;
import com.juniverse.redis.example.dto.UserDTO;
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
 * Implementación del servicio de usuarios que muestra el uso de RedisCacheService
 *
 * @author Java Universe
 */
@Service
@Slf4j
public class UserServiceImpl implements UserService {

    private final CacheManager cacheManager;
    private final RedisCacheService redisCacheService;
    private static final String CACHE_NAME = "users";

    private static final Map<Long, UserDTO> users = new ConcurrentHashMap<>();
    private static final AtomicLong idGenerator = new AtomicLong(10);

    /**
     * Constructor para la inyección de dependencias
     * @param cacheManager administrador de caché
     * @param redisCacheService servicio de caché de Redis
     */
    public UserServiceImpl(CacheManager cacheManager, RedisCacheService redisCacheService) {
        this.cacheManager = cacheManager;
        this.redisCacheService = redisCacheService;
        createUsers();
    }

    /**
     * Inicializa usuarios de ejemplo en memoria
     */
    private void createUsers() {
        IntStream.rangeClosed(1, 10)
                .forEach(i -> users.put((long) i,
                    new UserDTO((long) i, "user" + i, "user" + i + "@example.com", LocalDateTime.now())));
    }

    /**
     * Obtiene un usuario por identificador, con actualización forzada opcional
     * Muestra RedisCacheService con caché condicional
     */
    @Override
    public UserDTO getUserById(Long id, boolean forceRefresh) {

        if (forceRefresh) {
            log.info("Force refresh requested for user ID: {}", id);
            redisCacheService.cacheEvict(CACHE_NAME, String.valueOf(id));
        }

        return redisCacheService.cacheable(
                CACHE_NAME,
                String.valueOf(id),
                () -> {
                    log.info("Cache miss for user ID: {} - loading from database", id);
                    Utility.simulateSlowService();
                    return users.get(id);
                },
                Duration.ofMinutes(30)
        );

    }

    /**
     * Guarda o actualiza un usuario
     * Muestra RedisCacheService con la operación cachePut
     */
    @Override
    public UserDTO saveOrUpdateUser(UserDTO user) {
        log.info("Saving/updating user in database...");
        Utility.simulateSlowService();

        if (user.getId() != null && users.containsKey(user.getId())) {
            users.put(user.getId(), user);
            log.info("Updated existing user: {}", user.getUsername());
        } else {
            Long id = idGenerator.incrementAndGet();
            user.setId(id);
            user.setCreatedAt(LocalDateTime.now());
            users.put(id, user);
            log.info("Created new user with ID: {}, username: {}", id, user.getUsername());
        }

        redisCacheService.cachePut(CACHE_NAME, String.valueOf(user.getId()), () -> user, Duration.ofMinutes(30));
        redisCacheService.cacheEvict(CACHE_NAME, "all");
        log.info("User saved and cached with ID: {}", user.getId());

        return user;
    }

    /**
     * Elimina usuario.
     * Muestra RedisCacheService con la operación cacheEvict
     * @return {@code true} si se eliminó el usuario; {@code false} si no existía
     */
    @Override
    public boolean deleteUser(Long id) {
        if (users.containsKey(id)) {
            log.info("Deleting user {}", id);
            users.remove(id);
            redisCacheService.cacheEvict(CACHE_NAME, String.valueOf(id));
            redisCacheService.cacheEvict(CACHE_NAME, "all");
            log.info("Cache evicted for user ID: {}", id);
            return true;
        }
        return false;
    }

    /**
     * Limpia caché de usuarios todos.
     */
    @Override
    public void clearCache() {
        log.info("Clearing entire users cache.");
        Objects.requireNonNull(cacheManager.getCache(CACHE_NAME)).clear();
    }

    /**
     * Obtiene usuarios todos.
     * Muestra RedisCacheService en operaciones de lista
     */
    @Override
    public List<UserDTO> getUsers() {
        try {
            List<UserDTO> userList = redisCacheService.cacheable(
                    CACHE_NAME,
                    "all",
                    () -> {
                        log.info("Cache miss - loading all users from database");
                        Utility.simulateSlowService();
                        List<UserDTO> list = new ArrayList<>(users.values());
                        log.info("Loaded {} users from database", list.size());
                        return list;
                    },
                    Duration.ofMinutes(15)
            );

            if (userList != null) {
                log.info("Retrieved {} users (from cache or database)", userList.size());
            }

            return userList;
        } catch (Exception ex) {
            log.error("Error loading users: ", ex);
            return Collections.emptyList();
        }
    }

}
