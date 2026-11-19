package com.juniverse.redis.example.infrastructure.adapter.in.web;

import io.github.programming0026.juniverse.redis.dto.RedisStatusDto;
import io.github.programming0026.juniverse.redis.service.RedisHealthChecker;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Redis health controller
 * Provides endpoints to check the status of the Redis connection
 *
 * @author Java Universe
 */
@RestController
@Tag(name = "Redis Health", description = "Redis connection health check")
@CrossOrigin(origins = "*", allowedHeaders = "*")
@RequestMapping("/api")
public class RedisHealthController {

    private final RedisHealthChecker healthChecker;

    /**
     * Constructor for dependency injection
     * @param healthChecker Redis health checker service
     */
    public RedisHealthController(RedisHealthChecker healthChecker) {
        this.healthChecker = healthChecker;
    }

    /**
     * Checks the health of the Redis connection
     * @return RedisStatusDto with the connection status
     */
    @Operation(summary = "Check the Redis connection status")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Redis connection status")
    })
    @GetMapping("/health/redis")
    public RedisStatusDto checkRedisHealth() {
        return healthChecker.isRedisActive();
    }
}
