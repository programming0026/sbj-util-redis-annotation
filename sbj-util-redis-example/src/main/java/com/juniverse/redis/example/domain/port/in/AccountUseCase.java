package com.juniverse.redis.example.domain.port.in;

import com.juniverse.redis.example.domain.model.Account;
import java.util.List;
import java.util.Optional;

/**
 * Inbound port: use cases offered for accounts
 *
 * @author Java Universe
 */
public interface AccountUseCase {

    /**
     * Gets a account by id
     * @param id account id
     * @param forceRefresh discards the cached entry and reloads it
     * @return the account, or empty if it does not exist
     */
    Optional<Account> getAccountById(Long id, boolean forceRefresh);

    /**
     * Gets all accounts
     * @return list of accounts
     */
    List<Account> getAccounts();

    /**
     * Deletes a account by id
     * @param id account id
     * @return {@code true} if the account was deleted; {@code false} if it did not exist
     */
    boolean deleteAccount(Long id);

    /**
     * Creates a account, or updates it if its id already exists
     * @param account account data
     * @return the saved account
     */
    Account saveOrUpdateAccount(Account account);

    /**
     * Clears the entire accounts cache
     */
    void clearCache();
}
