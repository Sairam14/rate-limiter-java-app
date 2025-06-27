package com.example.service;

import com.example.model.*;
import org.junit.jupiter.api.Test;
import org.testcontainers.containers.GenericContainer;

import static org.junit.jupiter.api.Assertions.*;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;

public class RateLimiterServiceUnitTest {

    static GenericContainer<?> redis = new GenericContainer<>("redis:7.2.4").withExposedPorts(6379);

    @Test
    void tokenBucket_allows_within_capacity() {
        RateLimiterConfig config = new RateLimiterConfig(3, 1, RateLimiterType.TOKEN_BUCKET);
        RateLimiterService service = new RateLimiterService(new SimpleMeterRegistry());
        long now = System.currentTimeMillis();

        // Use in-memory fallback directly
        assertTrue(service.acquireInMemory("user1", config, now));
        assertTrue(service.acquireInMemory("user1", config, now));
        assertTrue(service.acquireInMemory("user1", config, now));
        assertFalse(service.acquireInMemory("user1", config, now));
    }

    @Test
    void leakyBucket_allows_within_capacity() {
        RateLimiterConfig config = new RateLimiterConfig(2, 1, RateLimiterType.LEAKY_BUCKET);
        RateLimiterService service = new RateLimiterService(new SimpleMeterRegistry());
        long now = System.currentTimeMillis();

        assertTrue(service.acquireInMemory("user2", config, now));
        assertTrue(service.acquireInMemory("user2", config, now));
        assertFalse(service.acquireInMemory("user2", config, now));
    }

    @Test
    void tokenBucket_refills() throws InterruptedException {
        RateLimiterConfig config = new RateLimiterConfig(1, 1, RateLimiterType.TOKEN_BUCKET);
        RateLimiterService service = new RateLimiterService(new SimpleMeterRegistry());
        long now = System.currentTimeMillis();

        assertTrue(service.acquireInMemory("user3", config, now));
        assertFalse(service.acquireInMemory("user3", config, now));
        // Simulate 1 second later
        assertTrue(service.acquireInMemory("user3", config, now + 1000));
    }
}