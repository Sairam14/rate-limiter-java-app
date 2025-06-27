package com.example.service;

import com.example.model.*;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.*;
import org.testcontainers.containers.GenericContainer;

import static org.junit.jupiter.api.Assertions.*;

public class RateLimiterServiceFullTest {

    static GenericContainer<?> redis = new GenericContainer<>("redis:7.2.4").withExposedPorts(6379);

    @BeforeAll
    static void startRedis() {
        redis.start();
    }

    @AfterAll
    static void stopRedis() {
        redis.stop();
    }

    private RateLimiterService service;

    @BeforeEach
    void setUp() {
        service = new RateLimiterService(
            new SimpleMeterRegistry(),
            redis.getHost(),
            redis.getMappedPort(6379)
        );
    }

    @Test
    void testSetAndGetConfig() {
        RateLimiterConfig config = new RateLimiterConfig(5, 2, RateLimiterType.TOKEN_BUCKET);
        service.setConfig("testKey", config);
        // Use getStatus to verify config is applied
        RateLimiterStatus status = service.getStatus("testKey");
        assertEquals(config.getCapacity(), status.getCapacity());
        assertEquals(config.getRefillRate(), status.getRefillRate());
        assertEquals(config.getType(), status.getType());
    }

    @Test
    void testAcquireTokenBucketSuccessAndFail() {
        RateLimiterConfig config = new RateLimiterConfig(2, 1, RateLimiterType.TOKEN_BUCKET);
        service.setConfig("user", config);
        assertTrue(service.acquire("user"));
        assertTrue(service.acquire("user"));
        assertFalse(service.acquire("user"));
    }

    @Test
    void testAcquireLeakyBucketSuccessAndFail() {
        RateLimiterConfig config = new RateLimiterConfig(1, 1, RateLimiterType.LEAKY_BUCKET);
        service.setConfig("user2", config);
        assertTrue(service.acquire("user2"));
        assertFalse(service.acquire("user2"));
    }

    @Test
    void testGetStatusTokenBucket() {
        RateLimiterConfig config = new RateLimiterConfig(2, 1, RateLimiterType.TOKEN_BUCKET);
        service.setConfig("user3", config);
        service.acquire("user3");
        RateLimiterStatus status = service.getStatus("user3");
        assertNotNull(status);
        assertTrue(status.getTokensLeft() <= config.getCapacity());
    }

    @Test
    void testGetStatusLeakyBucket() {
        RateLimiterConfig config = new RateLimiterConfig(2, 1, RateLimiterType.LEAKY_BUCKET);
        service.setConfig("user4", config);
        service.acquire("user4");
        RateLimiterStatus status = service.getStatus("user4");
        assertNotNull(status);
        assertTrue(status.getTokensLeft() <= config.getCapacity());
    }

    @Test
    void testGetAllStatuses() {
        RateLimiterConfig config = new RateLimiterConfig(2, 1, RateLimiterType.TOKEN_BUCKET);
        service.setConfig("user5", config);
        service.acquire("user5");
        assertTrue(service.getAllStatuses().containsKey("user5"));
    }
}