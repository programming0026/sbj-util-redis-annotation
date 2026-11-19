package com.juniverse.redis.example.domain.port.in;

import com.juniverse.redis.example.domain.model.User;
import java.util.List;
import java.util.Optional;

/**
 * Inbound port: use cases offered for users
 *
 * @author Java Universe
 */
public interface UserUseCase {

    /**
     * Gets a user by id
     * @param id user id
     * @param forceRefresh discards the cached entry and reloads it
     * @return the user, or empty if it does not exist
     */
    Optional<User> getUserById(Long id, boolean forceRefresh);

    /**
     * Gets all users
     * @return list of users
     */
    List<User> getUsers();

    /**
     * Deletes a user by id
     * @param id user id
     * @return {@code true} if the user was deleted; {@code false} if it did not exist
     */
    boolean deleteUser(Long id);

    /**
     * Creates a user, or updates it if its id already exists
     * @param user user data
     * @return the saved user
     */
    User saveOrUpdateUser(User user);

    /**
     * Clears the entire users cache
     */
    void clearCache();
}
