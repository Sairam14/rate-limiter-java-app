package com.example.service;

import com.example.model.*;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.*;
import org.testcontainers.containers.GenericContainer;

import static org.junit.jupiter.api.Assertions.*;

public class RateLimiterServiceIntegrationTest {

    static GenericContainer<?> redis = new GenericContainer<>("redis:7.2.4").withExposedPorts(6379);

    @BeforeAll
    static void setupRedis() {
        redis.start();
        System.setProperty("REDIS_HOST", redis.getHost());
        System.setProperty("REDIS_PORT", redis.getMappedPort(6379).toString());
    }

    @AfterAll
    static void stopRedis() {
        redis.stop();
    }

    @Test
    void tokenBucket_works_with_redis() {
        RateLimiterConfig config = new RateLimiterConfig(2, 1, RateLimiterType.TOKEN_BUCKET);
        RateLimiterService service = new RateLimiterService(
            new SimpleMeterRegistry(),
            System.getProperty("REDIS_HOST"),
            Integer.parseInt(System.getProperty("REDIS_PORT"))
        );
        service.setConfig("integrationUser", config);

        assertTrue(service.acquire("integrationUser"));
        assertTrue(service.acquire("integrationUser"));
        assertFalse(service.acquire("integrationUser"));
    }
}