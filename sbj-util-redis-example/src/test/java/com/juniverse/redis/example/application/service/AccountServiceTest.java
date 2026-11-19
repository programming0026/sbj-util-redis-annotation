package com.juniverse.redis.example.application.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.juniverse.redis.example.domain.model.Account;
import com.juniverse.redis.example.domain.port.out.AccountCachePort;
import com.juniverse.redis.example.domain.port.out.AccountRepositoryPort;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

/** Application logic tested in isolation: the outbound ports are mocks. */
public class AccountServiceTest {

    private AccountRepositoryPort repository;
    private AccountCachePort cache;
    private AccountService service;

    @BeforeEach
    public void setUp() {
        repository = mock(AccountRepositoryPort.class);
        cache = mock(AccountCachePort.class);
        service = new AccountService(repository, cache);
        when(repository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
    }

    private static Account account(Long id) {
        return new Account(id, "ACC", "Holder", BigDecimal.TEN, "SAVINGS", null);
    }

    @Test
    public void readsWithoutTouchingTheCacheWhenNoRefreshIsRequested() {
        when(repository.findById(1L)).thenReturn(Optional.of(account(1L)));

        assertEquals(1L, service.getAccountById(1L, false).orElseThrow().getId());
        verify(cache, never()).evict(any());
    }

    @Test
    public void forceRefreshEvictsTheEntryBeforeReading() {
        service.getAccountById(2L, true);

        verify(cache).evict(2L);
        verify(repository).findById(2L);
    }

    @Test
    public void listsFromTheRepository() {
        when(repository.findAll()).thenReturn(List.of(account(1L), account(2L)));

        assertEquals(2, service.getAccounts().size());
    }

    @Test
    public void newAccountGetsACreationDateAndNoId() {
        Account saved = service.saveOrUpdateAccount(account(null));

        assertNull(saved.getId(), "the repository assigns the id");
        assertNotNull(saved.getCreatedAt());
    }

    @Test
    public void unknownIdIsTreatedAsANewAccount() {
        when(repository.existsById(99L)).thenReturn(false);

        Account saved = service.saveOrUpdateAccount(account(99L));

        assertNull(saved.getId());
        assertNotNull(saved.getCreatedAt());
    }

    @Test
    public void existingAccountIsUpdatedKeepingItsData() {
        LocalDateTime created = LocalDateTime.of(2020, 1, 1, 0, 0);
        Account existing = account(3L);
        existing.setCreatedAt(created);
        when(repository.existsById(3L)).thenReturn(true);

        service.saveOrUpdateAccount(existing);

        ArgumentCaptor<Account> captor = ArgumentCaptor.forClass(Account.class);
        verify(repository).save(captor.capture());
        assertSame(existing, captor.getValue());
        assertEquals(3L, captor.getValue().getId());
        assertEquals(created, captor.getValue().getCreatedAt());
    }

    @Test
    public void deleteDelegatesToTheRepository() {
        when(repository.deleteById(4L)).thenReturn(true);

        assertTrue(service.deleteAccount(4L));
    }

    @Test
    public void clearCacheEvictsEverything() {
        service.clearCache();

        verify(cache).evictAll();
    }
}
