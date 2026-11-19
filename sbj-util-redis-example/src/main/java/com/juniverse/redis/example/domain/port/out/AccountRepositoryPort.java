package com.juniverse.redis.example.domain.port.out;

import com.juniverse.redis.example.domain.model.Account;
import java.util.List;
import java.util.Optional;

/**
 * Outbound port: storage of accounts
 *
 * @author Java Universe
 */
public interface AccountRepositoryPort {

    /**
     * Finds a account by id
     * @param id account id
     * @return the account, or empty if it does not exist
     */
    Optional<Account> findById(Long id);

    /**
     * Finds all accounts
     * @return list of accounts
     */
    List<Account> findAll();

    /**
     * Checks whether a account exists
     * @param id account id
     * @return {@code true} if it exists
     */
    boolean existsById(Long id);

    /**
     * Saves a account; when its id is {@code null} a new one is assigned
     * @param account account to save
     * @return the saved account
     */
    Account save(Account account);

    /**
     * Deletes a account by id
     * @param id account id
     * @return {@code true} if the account was deleted; {@code false} if it did not exist
     */
    boolean deleteById(Long id);
}
