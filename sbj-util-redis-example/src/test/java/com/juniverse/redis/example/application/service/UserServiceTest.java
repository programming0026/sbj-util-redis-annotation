package com.juniverse.redis.example.application.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.juniverse.redis.example.domain.model.User;
import com.juniverse.redis.example.domain.port.out.UserCachePort;
import com.juniverse.redis.example.domain.port.out.UserRepositoryPort;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/** Application logic tested in isolation: the outbound ports are mocks. */
public class UserServiceTest {

    private UserRepositoryPort repository;
    private UserCachePort cache;
    private UserService service;

    @BeforeEach
    public void setUp() {
        repository = mock(UserRepositoryPort.class);
        cache = mock(UserCachePort.class);
        service = new UserService(repository, cache);
        when(repository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
    }

    @Test
    public void readsWithoutTouchingTheCacheWhenNoRefreshIsRequested() {
        when(repository.findById(1L)).thenReturn(Optional.of(new User(1L, "ana", "a@example.com", null)));

        assertEquals("ana", service.getUserById(1L, false).orElseThrow().getUsername());
        verify(cache, never()).evict(any());
    }

    @Test
    public void forceRefreshEvictsTheEntryBeforeReading() {
        assertTrue(service.getUserById(2L, true).isEmpty());

        verify(cache).evict(2L);
    }

    @Test
    public void listsFromTheRepository() {
        when(repository.findAll()).thenReturn(List.of(new User(1L, "ana", "a@example.com", null)));

        assertEquals(1, service.getUsers().size());
    }

    @Test
    public void newUserGetsACreationDate() {
        User saved = service.saveOrUpdateUser(new User(null, "new", "n@example.com", null));

        assertNull(saved.getId());
        assertNotNull(saved.getCreatedAt());
    }

    @Test
    public void existingUserKeepsItsCreationDate() {
        LocalDateTime created = LocalDateTime.of(2020, 1, 1, 0, 0);
        when(repository.existsById(5L)).thenReturn(true);

        User saved = service.saveOrUpdateUser(new User(5L, "after", "d@example.com", created));

        assertEquals(5L, saved.getId());
        assertEquals(created, saved.getCreatedAt());
    }

    @Test
    public void deleteDelegatesToTheRepository() {
        assertFalse(service.deleteUser(7L));
        verify(repository).deleteById(7L);
    }

    @Test
    public void clearCacheEvictsEverything() {
        service.clearCache();

        verify(cache).evictAll();
    }
}
