package com.juniverse.redis.example.controller;

import com.juniverse.redis.example.dto.UserDTO;
import com.juniverse.redis.example.service.UserService;
import java.util.List;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * User Controller demonstrating CacheOperationBuilder usage
 *
 * @author linux
 */
@RestController
@CrossOrigin(origins = "*", allowedHeaders = "*")
@RequestMapping("/api/v1/users")
@Slf4j
public class UserController {

    private final UserService userService;

    /**
     * Constructor for dependency injection
     * @param userService the user service
     */
    public UserController(UserService userService) {
        this.userService = userService;
    }

    /**
     * Get all users
     * @return ResponseEntity with list of users
     */
    @GetMapping(produces = {MediaType.APPLICATION_JSON_VALUE})
    public ResponseEntity<List<UserDTO>> getUsers() {
        List<UserDTO> response = userService.getUsers();
        return new ResponseEntity<>(response, HttpStatus.OK);
    }

    /**
     * Get user by ID
     * @param id the user ID
     * @param forceRefresh force cache refresh
     * @return ResponseEntity with user data or NOT_FOUND
     */
    @GetMapping(path = "/{id}", produces = {MediaType.APPLICATION_JSON_VALUE})
    public ResponseEntity<UserDTO> getUser(
            @PathVariable("id") Long id,
            @RequestParam(value = "forceRefresh", defaultValue = "false") boolean forceRefresh) {
        UserDTO response = userService.getUserById(id, forceRefresh);
        if (response == null) {
            return new ResponseEntity<>(HttpStatus.NOT_FOUND);
        }
        return new ResponseEntity<>(response, HttpStatus.OK);
    }

    /**
     * Clear entire users cache
     * @return ResponseEntity with OK status
     */
    @GetMapping(path = "/refresh", produces = {MediaType.APPLICATION_JSON_VALUE})
    public ResponseEntity<Void> refreshAll() {
        userService.clearCache();
        return new ResponseEntity<>(HttpStatus.OK);
    }

    /**
     * Delete user by ID
     * @param id the user ID
     * @return ResponseEntity with NO_CONTENT or NOT_FOUND
     */
    @DeleteMapping(path = "/{id}", produces = {MediaType.APPLICATION_JSON_VALUE})
    public ResponseEntity<Void> delete(@PathVariable("id") Long id) {
        boolean deleted = userService.deleteUser(id);
        if (!deleted) {
            return new ResponseEntity<>(HttpStatus.NOT_FOUND);
        }
        return new ResponseEntity<>(HttpStatus.NO_CONTENT);
    }

    /**
     * Create or update user
     * @param user the user data
     * @return ResponseEntity with created/updated user
     */
    @PostMapping(produces = {MediaType.APPLICATION_JSON_VALUE})
    public ResponseEntity<UserDTO> save(@RequestBody UserDTO user) {
        UserDTO response = userService.saveOrUpdateUser(user);
        return new ResponseEntity<>(response, HttpStatus.CREATED);
    }
}
