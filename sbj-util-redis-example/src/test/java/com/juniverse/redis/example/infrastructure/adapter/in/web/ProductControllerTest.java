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

import com.juniverse.redis.example.domain.model.Product;
import com.juniverse.redis.example.domain.port.in.ProductUseCase;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

public class ProductControllerTest {

    private ProductUseCase service;
    private MockMvc mvc;

    @BeforeEach
    public void setUp() {
        service = mock(ProductUseCase.class);
        mvc = MockMvcBuilders.standaloneSetup(new ProductController(service)).build();
    }

    @Test
    public void listsProducts() throws Exception {
        when(service.getProducts()).thenReturn(List.of(
                new Product(1L, "A", 1.5, LocalDate.of(2026, 1, 2)),
                new Product(2L, "B", 2.5, LocalDate.of(2026, 1, 3))));

        mvc.perform(get("/api/v1/products"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2))
                .andExpect(jsonPath("$[0].name").value("A"))
                .andExpect(jsonPath("$[0].date").value("2026-01-02"));
    }

    @Test
    public void returnsOneProduct() throws Exception {
        when(service.getProductById(1L)).thenReturn(Optional.of(new Product(1L, "A", 1.5, LocalDate.of(2026, 1, 2))));

        mvc.perform(get("/api/v1/products/1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(1))
                .andExpect(jsonPath("$.mount").value(1.5));
    }

    @Test
    public void unknownProductIs404() throws Exception {
        mvc.perform(get("/api/v1/products/99")).andExpect(status().isNotFound());
    }

    @Test
    public void createsAProduct() throws Exception {
        when(service.saveOrUpdateProduct(any())).thenReturn(new Product(11L, "New", 5.5, LocalDate.now()));

        mvc.perform(post("/api/v1/products").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"New\",\"mount\":5.5}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").value(11))
                .andExpect(jsonPath("$.name").value("New"));
    }

    @Test
    public void deleteReturns204OrTheNotFound() throws Exception {
        when(service.deleteProduct(1L)).thenReturn(true);

        mvc.perform(delete("/api/v1/products/1")).andExpect(status().isNoContent());
        mvc.perform(delete("/api/v1/products/2")).andExpect(status().isNotFound());
    }

    @Test
    public void refreshClearsTheCache() throws Exception {
        mvc.perform(get("/api/v1/products/refresh")).andExpect(status().isOk());

        verify(service).clearCache();
    }
}
