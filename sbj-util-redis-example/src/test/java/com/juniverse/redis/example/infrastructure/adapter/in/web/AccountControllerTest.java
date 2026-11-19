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

import com.juniverse.redis.example.domain.model.Account;
import com.juniverse.redis.example.domain.port.in.AccountUseCase;
import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

public class AccountControllerTest {

    private AccountUseCase service;
    private MockMvc mvc;

    @BeforeEach
    public void setUp() {
        service = mock(AccountUseCase.class);
        mvc = MockMvcBuilders.standaloneSetup(new AccountController(service)).build();
    }

    private static Account account(long id) {
        return new Account(id, "ACC00000" + id, "Holder " + id, BigDecimal.valueOf(1000.5), "SAVINGS", null);
    }

    @Test
    public void listsAccounts() throws Exception {
        when(service.getAccounts()).thenReturn(List.of(account(1), account(2)));

        mvc.perform(get("/api/v1/accounts"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2))
                .andExpect(jsonPath("$[1].accountNumber").value("ACC000002"));
    }

    @Test
    public void returnsOneAccountAndPassesForceRefresh() throws Exception {
        when(service.getAccountById(1L, true)).thenReturn(Optional.of(account(1)));

        mvc.perform(get("/api/v1/accounts/1?forceRefresh=true"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.balance").value(1000.5))
                .andExpect(jsonPath("$.accountType").value("SAVINGS"));

        verify(service).getAccountById(1L, true);
    }

    @Test
    public void unknownAccountIs404() throws Exception {
        mvc.perform(get("/api/v1/accounts/99")).andExpect(status().isNotFound());
    }

    @Test
    public void createsAnAccount() throws Exception {
        when(service.saveOrUpdateAccount(any())).thenReturn(account(11));

        mvc.perform(post("/api/v1/accounts").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"accountNumber\":\"ACC000011\",\"accountHolder\":\"Holder\",\"balance\":10}"))
                .andExpect(status().is2xxSuccessful())
                .andExpect(jsonPath("$.id").value(11));
    }

    @Test
    public void deleteReturnsNoContentOrNotFound() throws Exception {
        when(service.deleteAccount(1L)).thenReturn(true);

        mvc.perform(delete("/api/v1/accounts/1")).andExpect(status().isNoContent());
        mvc.perform(delete("/api/v1/accounts/2")).andExpect(status().isNotFound());
    }

    @Test
    public void refreshClearsTheCache() throws Exception {
        mvc.perform(get("/api/v1/accounts/refresh")).andExpect(status().isOk());

        verify(service).clearCache();
    }
}
