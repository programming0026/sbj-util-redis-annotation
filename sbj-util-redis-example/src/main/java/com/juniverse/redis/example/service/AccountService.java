package com.juniverse.redis.example.service;

import com.juniverse.redis.example.dto.AccountDTO;
import java.util.List;

/**
 * Interfaz del servicio de cuentas
 *
 * @author Java Universe
 */
public interface AccountService {

    /**
     * Obtiene una cuenta por identificador, con actualización forzada opcional
     * @param id identificador de la cuenta
     * @param forceRefresh fuerza la actualización de la caché
     * @return AccountDto con los datos de la cuenta
     */
    AccountDTO getAccountById(Long id, boolean forceRefresh);

    /**
     * Obtiene todas las cuentas
     * @return lista de todas las cuentas
     */
    List<AccountDTO> getAccounts();

    /**
     * Elimina una cuenta por su identificador
     * @param id identificador de la cuenta
     * @return {@code true} si se eliminó la cuenta; {@code false} si no existía
     */
    boolean deleteAccount(Long id);

    /**
     * Guarda o actualiza una cuenta
     * @param account datos de la cuenta
     * @return AccountDto con la cuenta guardada o actualizada
     */
    AccountDTO saveOrUpdateAccount(AccountDTO account);

    /**
     * Limpia toda la caché de cuentas
     */
    void clearCache();
}
