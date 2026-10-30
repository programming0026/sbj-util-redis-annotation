package com.juniverse.redis.example.controller;

import com.juniverse.redis.dto.RedisStatusDto;
import com.juniverse.redis.service.RedisHealthChecker;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Redis Health Controller
 * Provides endpoints to check Redis connection status
 *
 * @author linux
 */
@RestController
@CrossOrigin(origins = "*", allowedHeaders = "*")
@RequestMapping("/api")
public class RedisHealthController {

    private final RedisHealthChecker healthChecker;

    /**
     * Constructor for dependency injection
     * @param healthChecker the Redis health checker service
     */
    public RedisHealthController(RedisHealthChecker healthChecker) {
        this.healthChecker = healthChecker;
    }

    /**
     * Check Redis connection health
     * @return RedisStatusDto with connection status information
     */
    @GetMapping("/health/redis")
    public RedisStatusDto checkRedisHealth() {
        return healthChecker.isRedisActive();
    }
}
