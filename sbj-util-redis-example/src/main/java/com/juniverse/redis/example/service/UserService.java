package com.juniverse.redis.example.service;

import com.juniverse.redis.example.dto.UserDTO;
import java.util.List;

/**
 * Users service interface
 *
 * @author Java Universe
 */
public interface UserService {

    /**
     * Gets a user by id, with optional forced refresh
     * @param id user id
     * @param forceRefresh forces a cache refresh
     * @return UserDto with the user data
     */
    UserDTO getUserById(Long id, boolean forceRefresh);

    /**
     * Gets all users.
     * @return list of all users
     */
    List<UserDTO> getUsers();

    /**
     * Deletes a user by id
     * @param id user id
     * @return {@code true} if the user was deleted; {@code false} if it did not exist
     */
    boolean deleteUser(Long id);

    /**
     * Saves or updates a user
     * @param user user data
     * @return UserDto with the saved or updated user
     */
    UserDTO saveOrUpdateUser(UserDTO user);

    /**
     * Clears the entire users cache
     */
    void clearCache();
}
