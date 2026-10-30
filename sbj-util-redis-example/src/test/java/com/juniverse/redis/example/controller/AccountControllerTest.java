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

import com.juniverse.redis.example.dto.AccountDTO;
import com.juniverse.redis.example.service.AccountService;
import java.math.BigDecimal;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

class AccountControllerTest {

    private AccountService service;
    private MockMvc mvc;

    @BeforeEach
    void setUp() {
        service = mock(AccountService.class);
        mvc = MockMvcBuilders.standaloneSetup(new AccountController(service)).build();
    }

    private static AccountDTO account(long id) {
        return new AccountDTO(id, "ACC00000" + id, "Titular " + id, BigDecimal.valueOf(1000.5), "SAVINGS", null);
    }

    @Test
    void listsAccounts() throws Exception {
        when(service.getAccounts()).thenReturn(List.of(account(1), account(2)));

        mvc.perform(get("/api/v1/accounts"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2))
                .andExpect(jsonPath("$[1].accountNumber").value("ACC000002"));
    }

    @Test
    void returnsOneAccountAndPassesForceRefresh() throws Exception {
        when(service.getAccountById(1L, true)).thenReturn(account(1));

        mvc.perform(get("/api/v1/accounts/1?forceRefresh=true"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.balance").value(1000.5))
                .andExpect(jsonPath("$.accountType").value("SAVINGS"));

        verify(service).getAccountById(1L, true);
    }

    @Test
    void unknownAccountIs404() throws Exception {
        mvc.perform(get("/api/v1/accounts/99")).andExpect(status().isNotFound());
    }

    @Test
    void createsAnAccount() throws Exception {
        when(service.saveOrUpdateAccount(any())).thenReturn(account(11));

        mvc.perform(post("/api/v1/accounts").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"accountNumber\":\"ACC000011\",\"accountHolder\":\"Titular\",\"balance\":10}"))
                .andExpect(status().is2xxSuccessful())
                .andExpect(jsonPath("$.id").value(11));
    }

    @Test
    void deleteReturnsNoContentOrNotFound() throws Exception {
        when(service.deleteAccount(1L)).thenReturn(true);

        mvc.perform(delete("/api/v1/accounts/1")).andExpect(status().isNoContent());
        mvc.perform(delete("/api/v1/accounts/2")).andExpect(status().isNotFound());
    }

    @Test
    void refreshClearsTheCache() throws Exception {
        mvc.perform(get("/api/v1/accounts/refresh")).andExpect(status().isOk());

        verify(service).clearCache();
    }
}
