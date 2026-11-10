package com.juniverse.redis.example.controller;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import io.github.programming0026.juniverse.redis.dto.RedisStatusDto;
import io.github.programming0026.juniverse.redis.service.RedisHealthChecker;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

class RedisHealthControllerTest {

    @Test
    void reportsTheStatusReturnedByTheLibraryChecker() throws Exception {
        RedisHealthChecker checker = mock(RedisHealthChecker.class);
        RedisStatusDto dto = RedisStatusDto.builder().connected(true).responseTime(3L).redisVersion("7.4.5").build();
        when(checker.isRedisActive()).thenReturn(dto);
        MockMvc mvc = MockMvcBuilders.standaloneSetup(new RedisHealthController(checker)).build();

        mvc.perform(get("/api/health/redis"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.connected").value(true))
                .andExpect(jsonPath("$.redisVersion").value("7.4.5"));
    }
}
