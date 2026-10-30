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

import com.juniverse.redis.example.dto.ProductDTO;
import com.juniverse.redis.example.service.ProductService;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

class ProductControllerTest {

    private ProductService service;
    private MockMvc mvc;

    @BeforeEach
    void setUp() {
        service = mock(ProductService.class);
        mvc = MockMvcBuilders.standaloneSetup(new ProductController(service)).build();
    }

    @Test
    void listsProducts() throws Exception {
        when(service.getProducts()).thenReturn(List.of(
                new ProductDTO(1L, "A", 1.5, LocalDate.of(2026, 1, 2)),
                new ProductDTO(2L, "B", 2.5, LocalDate.of(2026, 1, 3))));

        mvc.perform(get("/api/v1/products"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2))
                .andExpect(jsonPath("$[0].name").value("A"))
                .andExpect(jsonPath("$[0].date").value("2026-01-02"));
    }

    @Test
    void returnsOneProduct() throws Exception {
        when(service.getProductById(1L)).thenReturn(new ProductDTO(1L, "A", 1.5, LocalDate.of(2026, 1, 2)));

        mvc.perform(get("/api/v1/products/1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(1))
                .andExpect(jsonPath("$.mount").value(1.5));
    }

    @Test
    void unknownProductIs404() throws Exception {
        mvc.perform(get("/api/v1/products/99")).andExpect(status().isNotFound());
    }

    @Test
    void createsAProduct() throws Exception {
        when(service.saveOrUpdateProduct(any())).thenReturn(new ProductDTO(11L, "Nuevo", 5.5, LocalDate.now()));

        mvc.perform(post("/api/v1/products").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"Nuevo\",\"mount\":5.5}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").value(11))
                .andExpect(jsonPath("$.name").value("Nuevo"));
    }

    @Test
    void deleteReturns204OrTheNotFound() throws Exception {
        when(service.deleteProduct(1L)).thenReturn(true);

        mvc.perform(delete("/api/v1/products/1")).andExpect(status().isNoContent());
        mvc.perform(delete("/api/v1/products/2")).andExpect(status().isNotFound());
    }

    @Test
    void refreshClearsTheCache() throws Exception {
        mvc.perform(get("/api/v1/products/refresh")).andExpect(status().isOk());

        verify(service).clearCache();
    }
}
