package com.juniverse.redis.example.controller;

import com.juniverse.redis.example.dto.UserDTO;
import com.juniverse.redis.example.service.UserService;
import java.util.List;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
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
 * Users controller showing the use of CacheOperationBuilder
 *
 * @author Java Universe
 */
@RestController
@Tag(name = "Users", description = "Users cached with the CacheOperationBuilder pattern")
@CrossOrigin(origins = "*", allowedHeaders = "*")
@RequestMapping("/api/v1/users")
@Slf4j
public class UserController {

    private final UserService userService;

    /**
     * Constructor for dependency injection
     * @param userService user service
     */
    public UserController(UserService userService) {
        this.userService = userService;
    }

    /**
     * Gets all users.
     * @return ResponseEntity with the list of users
     */
    @Operation(summary = "Get all users")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "List of users")
    })
    @GetMapping(produces = {MediaType.APPLICATION_JSON_VALUE})
    public ResponseEntity<List<UserDTO>> getUsers() {
        List<UserDTO> response = userService.getUsers();
        return new ResponseEntity<>(response, HttpStatus.OK);
    }

    /**
     * Gets a user by id
     * @param id user id
     * @param forceRefresh forces a cache refresh
     * @return ResponseEntity with the user or NOT_FOUND
     */
    @Operation(summary = "Get a user by id")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "User found"),
            @ApiResponse(responseCode = "404", description = "User not found")
    })
    @GetMapping(path = "/{id}", produces = {MediaType.APPLICATION_JSON_VALUE})
    public ResponseEntity<UserDTO> getUser(
            @Parameter(description = "Resource id") @PathVariable("id") Long id,
            @Parameter(description = "Forces a cache refresh") @RequestParam(value = "forceRefresh", defaultValue = "false") boolean forceRefresh) {
        UserDTO response = userService.getUserById(id, forceRefresh);
        if (response == null) {
            return new ResponseEntity<>(HttpStatus.NOT_FOUND);
        }
        return new ResponseEntity<>(response, HttpStatus.OK);
    }

    /**
     * Clears the entire users cache
     * @return ResponseEntity with OK status
     */
    @Operation(summary = "Clear the users cache")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Cache cleared")
    })
    @GetMapping(path = "/refresh", produces = {MediaType.APPLICATION_JSON_VALUE})
    public ResponseEntity<Void> refreshAll() {
        userService.clearCache();
        return new ResponseEntity<>(HttpStatus.OK);
    }

    /**
     * Deletes a user by id
     * @param id user id
     * @return ResponseEntity with NO_CONTENT or NOT_FOUND
     */
    @Operation(summary = "Delete a user by id")
    @ApiResponses({
            @ApiResponse(responseCode = "204", description = "User deleted"),
            @ApiResponse(responseCode = "404", description = "User not found")
    })
    @DeleteMapping(path = "/{id}", produces = {MediaType.APPLICATION_JSON_VALUE})
    public ResponseEntity<Void> delete(@Parameter(description = "Resource id") @PathVariable("id") Long id) {
        boolean deleted = userService.deleteUser(id);
        if (!deleted) {
            return new ResponseEntity<>(HttpStatus.NOT_FOUND);
        }
        return new ResponseEntity<>(HttpStatus.NO_CONTENT);
    }

    /**
     * Creates or updates a user
     * @param user user data
     * @return ResponseEntity with the created or updated user
     */
    @Operation(summary = "Create or update a user")
    @ApiResponses({
            @ApiResponse(responseCode = "201", description = "User created or updated")
    })
    @PostMapping(produces = {MediaType.APPLICATION_JSON_VALUE})
    public ResponseEntity<UserDTO> save(@RequestBody UserDTO user) {
        UserDTO response = userService.saveOrUpdateUser(user);
        return new ResponseEntity<>(response, HttpStatus.CREATED);
    }
}
