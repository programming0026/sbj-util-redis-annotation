package com.juniverse.redis.example.domain.port.out;

import com.juniverse.redis.example.domain.model.User;
import java.util.List;
import java.util.Optional;

/**
 * Outbound port: storage of users
 *
 * @author Java Universe
 */
public interface UserRepositoryPort {

    /**
     * Finds a user by id
     * @param id user id
     * @return the user, or empty if it does not exist
     */
    Optional<User> findById(Long id);

    /**
     * Finds all users
     * @return list of users
     */
    List<User> findAll();

    /**
     * Checks whether a user exists
     * @param id user id
     * @return {@code true} if it exists
     */
    boolean existsById(Long id);

    /**
     * Saves a user; when its id is {@code null} a new one is assigned
     * @param user user to save
     * @return the saved user
     */
    User save(User user);

    /**
     * Deletes a user by id
     * @param id user id
     * @return {@code true} if the user was deleted; {@code false} if it did not exist
     */
    boolean deleteById(Long id);
}
