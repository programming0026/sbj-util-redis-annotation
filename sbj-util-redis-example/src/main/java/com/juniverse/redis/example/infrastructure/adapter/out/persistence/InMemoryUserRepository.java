package com.juniverse.redis.example.infrastructure.adapter.out.persistence;

import com.juniverse.redis.example.domain.model.User;
import com.juniverse.redis.example.domain.port.out.UserRepositoryPort;
import java.time.LocalDateTime;

/**
 * Outbound adapter: in-memory storage of users, initialized with 10 sample users
 *
 * @author Java Universe
 */
public class InMemoryUserRepository extends InMemoryRepository<User> implements UserRepositoryPort {

    /**
     * Creates the storage with the sample users
     */
    public InMemoryUserRepository() {
        super(User::getId, User::setId);
        for (long i = 1; i <= 10; i++) {
            seed(new User(i, "user" + i, "user" + i + "@example.com", LocalDateTime.now()));
        }
    }
}
