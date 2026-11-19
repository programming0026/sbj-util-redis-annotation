package com.juniverse.redis.example.infrastructure.adapter.out.persistence;

import com.juniverse.redis.example.domain.model.Account;
import com.juniverse.redis.example.domain.port.out.AccountRepositoryPort;
import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * Outbound adapter: in-memory storage of accounts, initialized with 10 sample accounts
 *
 * @author Java Universe
 */
public class InMemoryAccountRepository extends InMemoryRepository<Account> implements AccountRepositoryPort {

    private static final String[] ACCOUNT_TYPES = {"SAVINGS", "CHECKING", "INVESTMENT"};

    /**
     * Creates the storage with the sample accounts
     */
    public InMemoryAccountRepository() {
        super(Account::getId, Account::setId);
        for (long i = 1; i <= 10; i++) {
            seed(new Account(i, "ACC" + String.format("%06d", i), "Account Holder " + i,
                    BigDecimal.valueOf(1000.00 * i), ACCOUNT_TYPES[(int) (i % 3)], LocalDateTime.now()));
        }
    }
}
