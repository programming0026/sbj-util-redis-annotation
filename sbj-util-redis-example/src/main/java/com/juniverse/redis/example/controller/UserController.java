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
 * Controlador de usuarios que muestra el uso de CacheOperationBuilder
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
     * Constructor para la inyección de dependencias
     * @param userService servicio de usuario
     */
    public UserController(UserService userService) {
        this.userService = userService;
    }

    /**
     * Obtiene usuarios todos.
     * @return ResponseEntity con la lista de usuarios
     */
    @GetMapping(produces = {MediaType.APPLICATION_JSON_VALUE})
    public ResponseEntity<List<UserDTO>> getUsers() {
        List<UserDTO> response = userService.getUsers();
        return new ResponseEntity<>(response, HttpStatus.OK);
    }

    /**
     * Obtiene usuario por identificador.
     * @param id identificador de usuario
     * @param forceRefresh fuerza la actualización de la caché
     * @return ResponseEntity con el usuario o NOT_FOUND
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
     * Limpia toda la caché de usuarios
     * @return ResponseEntity con estado OK
     */
    @GetMapping(path = "/refresh", produces = {MediaType.APPLICATION_JSON_VALUE})
    public ResponseEntity<Void> refreshAll() {
        userService.clearCache();
        return new ResponseEntity<>(HttpStatus.OK);
    }

    /**
     * Elimina usuario por identificador.
     * @param id identificador de usuario
     * @return ResponseEntity con NO_CONTENT o NOT_FOUND
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
     * Crea o actualiza un usuario
     * @param user datos de usuario
     * @return ResponseEntity con el usuario creado o actualizado
     */
    @PostMapping(produces = {MediaType.APPLICATION_JSON_VALUE})
    public ResponseEntity<UserDTO> save(@RequestBody UserDTO user) {
        UserDTO response = userService.saveOrUpdateUser(user);
        return new ResponseEntity<>(response, HttpStatus.CREATED);
    }
}
