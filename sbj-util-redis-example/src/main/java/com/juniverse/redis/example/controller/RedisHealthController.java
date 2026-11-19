package com.juniverse.redis.example.controller;

import io.github.programming0026.juniverse.redis.dto.RedisStatusDto;
import io.github.programming0026.juniverse.redis.service.RedisHealthChecker;
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
    @GetMapping("/health/redis")
    public RedisStatusDto checkRedisHealth() {
        return healthChecker.isRedisActive();
    }
}
