package com.juniverse.redis.example.service;

import com.juniverse.redis.example.dto.AccountDTO;
import java.util.List;

/**
 * Accounts service interface
 *
 * @author Java Universe
 */
public interface AccountService {

    /**
     * Gets an account by id, with optional forced refresh
     * @param id account id
     * @param forceRefresh forces a cache refresh
     * @return AccountDto with the account data
     */
    AccountDTO getAccountById(Long id, boolean forceRefresh);

    /**
     * Gets all accounts
     * @return list of all accounts
     */
    List<AccountDTO> getAccounts();

    /**
     * Deletes an account by its id
     * @param id account id
     * @return {@code true} if the account was deleted; {@code false} if it did not exist
     */
    boolean deleteAccount(Long id);

    /**
     * Saves or updates an account
     * @param account account data
     * @return AccountDto with the saved or updated account
     */
    AccountDTO saveOrUpdateAccount(AccountDTO account);

    /**
     * Clears the entire accounts cache
     */
    void clearCache();
}
