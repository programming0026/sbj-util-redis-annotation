package com.juniverse.redis.example.service;

import com.juniverse.redis.example.dto.AccountDTO;
import java.util.List;

/**
 * Account Service Interface
 *
 * @author linux
 */
public interface AccountService {

    /**
     * Get account by ID with optional force refresh
     * @param id the account ID
     * @param forceRefresh force cache refresh
     * @return AccountDto the account data
     */
    AccountDTO getAccountById(Long id, boolean forceRefresh);

    /**
     * Get all accounts
     * @return List of all accounts
     */
    List<AccountDTO> getAccounts();

    /**
     * Delete account by ID
     * @param id the account ID
     * @return true if account was deleted, false if account did not exist
     */
    boolean deleteAccount(Long id);

    /**
     * Save or update account
     * @param account the account data
     * @return AccountDto the saved/updated account
     */
    AccountDTO saveOrUpdateAccount(AccountDTO account);

    /**
     * Clear entire accounts cache
     */
    void clearCache();
}
