package com.juniverse.redis.example.infrastructure.adapter.in.web;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.juniverse.redis.example.domain.model.User;
import com.juniverse.redis.example.domain.port.in.UserUseCase;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

public class UserControllerTest {

    private UserUseCase service;
    private MockMvc mvc;

    @BeforeEach
    public void setUp() {
        service = mock(UserUseCase.class);
        mvc = MockMvcBuilders.standaloneSetup(new UserController(service)).build();
    }

    @Test
    public void listsUsers() throws Exception {
        when(service.getUsers()).thenReturn(List.of(new User(1L, "ana", "ana@example.com",
                LocalDateTime.of(2026, 1, 2, 3, 4, 5))));

        mvc.perform(get("/api/v1/users"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].username").value("ana"))
                .andExpect(jsonPath("$[0].createdAt").value("2026-01-02 03:04:05"));
    }

    @Test
    public void returnsOneUserAndPassesForceRefresh() throws Exception {
        when(service.getUserById(1L, true)).thenReturn(Optional.of(new User(1L, "ana", "ana@example.com", null)));

        mvc.perform(get("/api/v1/users/1?forceRefresh=true"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.email").value("ana@example.com"));

        verify(service).getUserById(1L, true);
    }

    @Test
    public void forceRefreshDefaultsToFalse() throws Exception {
        when(service.getUserById(2L, false)).thenReturn(Optional.of(new User(2L, "luis", "l@example.com", null)));

        mvc.perform(get("/api/v1/users/2")).andExpect(status().isOk());

        verify(service).getUserById(2L, false);
    }

    @Test
    public void unknownUserIs404() throws Exception {
        mvc.perform(get("/api/v1/users/99")).andExpect(status().isNotFound());
    }

    @Test
    public void createsAUser() throws Exception {
        when(service.saveOrUpdateUser(any())).thenReturn(new User(11L, "new", "n@example.com", null));

        mvc.perform(post("/api/v1/users").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"new\",\"email\":\"n@example.com\"}"))
                .andExpect(status().is2xxSuccessful())
                .andExpect(jsonPath("$.id").value(11));
    }

    @Test
    public void deleteReturnsNoContentOrNotFound() throws Exception {
        when(service.deleteUser(1L)).thenReturn(true);

        mvc.perform(delete("/api/v1/users/1")).andExpect(status().isNoContent());
        mvc.perform(delete("/api/v1/users/2")).andExpect(status().isNotFound());
    }

    @Test
    public void refreshClearsTheCache() throws Exception {
        mvc.perform(get("/api/v1/users/refresh")).andExpect(status().isOk());

        verify(service).clearCache();
    }
}
