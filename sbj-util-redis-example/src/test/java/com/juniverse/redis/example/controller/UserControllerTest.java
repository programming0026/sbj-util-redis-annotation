package com.juniverse.redis.example.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.juniverse.redis.example.dto.UserDTO;
import com.juniverse.redis.example.service.UserService;
import java.time.LocalDateTime;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

class UserControllerTest {

    private UserService service;
    private MockMvc mvc;

    @BeforeEach
    void setUp() {
        service = mock(UserService.class);
        mvc = MockMvcBuilders.standaloneSetup(new UserController(service)).build();
    }

    @Test
    void listsUsers() throws Exception {
        when(service.getUsers()).thenReturn(List.of(new UserDTO(1L, "ana", "ana@example.com",
                LocalDateTime.of(2026, 1, 2, 3, 4, 5))));

        mvc.perform(get("/api/v1/users"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].username").value("ana"))
                .andExpect(jsonPath("$[0].createdAt").value("2026-01-02 03:04:05"));
    }

    @Test
    void returnsOneUserAndPassesForceRefresh() throws Exception {
        when(service.getUserById(1L, true)).thenReturn(new UserDTO(1L, "ana", "ana@example.com", null));

        mvc.perform(get("/api/v1/users/1?forceRefresh=true"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.email").value("ana@example.com"));

        verify(service).getUserById(1L, true);
    }

    @Test
    void forceRefreshDefaultsToFalse() throws Exception {
        when(service.getUserById(2L, false)).thenReturn(new UserDTO(2L, "luis", "l@example.com", null));

        mvc.perform(get("/api/v1/users/2")).andExpect(status().isOk());

        verify(service).getUserById(2L, false);
    }

    @Test
    void unknownUserIs404() throws Exception {
        mvc.perform(get("/api/v1/users/99")).andExpect(status().isNotFound());
    }

    @Test
    void createsAUser() throws Exception {
        when(service.saveOrUpdateUser(any())).thenReturn(new UserDTO(11L, "nuevo", "n@example.com", null));

        mvc.perform(post("/api/v1/users").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"nuevo\",\"email\":\"n@example.com\"}"))
                .andExpect(status().is2xxSuccessful())
                .andExpect(jsonPath("$.id").value(11));
    }

    @Test
    void deleteReturnsNoContentOrNotFound() throws Exception {
        when(service.deleteUser(1L)).thenReturn(true);

        mvc.perform(delete("/api/v1/users/1")).andExpect(status().isNoContent());
        mvc.perform(delete("/api/v1/users/2")).andExpect(status().isNotFound());
    }

    @Test
    void refreshClearsTheCache() throws Exception {
        mvc.perform(get("/api/v1/users/refresh")).andExpect(status().isOk());

        verify(service).clearCache();
    }
}
