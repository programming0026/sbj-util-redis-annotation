package com.juniverse.redis.example.service;

import com.juniverse.redis.example.dto.UserDTO;
import java.util.List;

/**
 * User Service Interface
 *
 * @author linux
 */
public interface UserService {

    /**
     * Get user by ID with optional force refresh
     * @param id the user ID
     * @param forceRefresh force cache refresh
     * @return UserDto the user data
     */
    UserDTO getUserById(Long id, boolean forceRefresh);

    /**
     * Get all users
     * @return List of all users
     */
    List<UserDTO> getUsers();

    /**
     * Delete user by ID
     * @param id the user ID
     * @return true if user was deleted, false if user did not exist
     */
    boolean deleteUser(Long id);

    /**
     * Save or update user
     * @param user the user data
     * @return UserDto the saved/updated user
     */
    UserDTO saveOrUpdateUser(UserDTO user);

    /**
     * Clear entire users cache
     */
    void clearCache();
}
