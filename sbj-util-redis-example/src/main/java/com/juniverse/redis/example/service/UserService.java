package com.juniverse.redis.example.service;

import com.juniverse.redis.example.dto.UserDTO;
import java.util.List;

/**
 * Interfaz del servicio de usuarios
 *
 * @author linux
 */
public interface UserService {

    /**
     * Obtiene un usuario por identificador, con actualización forzada opcional
     * @param id identificador de usuario
     * @param forceRefresh fuerza la actualización de la caché
     * @return UserDto con los datos del usuario
     */
    UserDTO getUserById(Long id, boolean forceRefresh);

    /**
     * Obtiene usuarios todos.
     * @return usuarios de de de lista todos
     */
    List<UserDTO> getUsers();

    /**
     * Elimina usuario por identificador.
     * @param id identificador de usuario
     * @return {@code true} si se eliminó el usuario; {@code false} si no existía
     */
    boolean deleteUser(Long id);

    /**
     * Guarda o actualiza un usuario
     * @param user datos de usuario
     * @return UserDto con el usuario guardado o actualizado
     */
    UserDTO saveOrUpdateUser(UserDTO user);

    /**
     * Limpia toda la caché de usuarios
     */
    void clearCache();
}
