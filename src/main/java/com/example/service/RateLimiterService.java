package com.example.service;

import com.example.model.*;
import org.springframework.stereotype.Service;
import redis.clients.jedis.JedisPool;
import redis.clients.jedis.Jedis;

import java.util.Map;
import java.util.concurrent.*;
import java.util.HashMap;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.Timer;
import org.springframework.beans.factory.annotation.Autowired;

@Service
public class RateLimiterService {
    private final JedisPool jedisPool; // Do NOT assign here
    private final Map<String, RateLimiterConfig> configMap = new ConcurrentHashMap<>();

    // In-memory fallback state
    private final Map<String, InMemoryBucket> inMemoryBuckets = new ConcurrentHashMap<>();

    private static final int REDIS_RETRY_COUNT = 3;
    private static final long REDIS_RETRY_DELAY_MS = 100;

    private static final String TOKEN_BUCKET_LUA =
            "local tokens_key = KEYS[1] " +
            "local last_refill_key = KEYS[2] " +
            "local capacity = tonumber(ARGV[1]) " +
            "local refill_rate = tonumber(ARGV[2]) " +
            "local now = tonumber(ARGV[3]) " +
            "local tokens = tonumber(redis.call('get', tokens_key) or capacity) " +
            "local last_refill = tonumber(redis.call('get', last_refill_key) or now) " +
            "local elapsed = math.floor((now - last_refill) / 1000) " +
            "local refill = elapsed * refill_rate " +
            "tokens = math.min(capacity, tokens + refill) " +
            "if tokens > 0 then " +
            "  tokens = tokens - 1 " +
            "  redis.call('set', tokens_key, tokens) " +
            "  redis.call('set', last_refill_key, now) " +
            "  return 1 " +
            "else " +
            "  redis.call('set', last_refill_key, now) " +
            "  return 0 " +
            "end";

    private static final String LEAKY_BUCKET_LUA =
            "local water_key = KEYS[1] " +
            "local last_leak_key = KEYS[2] " +
            "local capacity = tonumber(ARGV[1]) " +
            "local leak_rate = tonumber(ARGV[2]) " +
            "local now = tonumber(ARGV[3]) " +
            "local water = tonumber(redis.call('get', water_key) or 0) " +
            "local last_leak = tonumber(redis.call('get', last_leak_key) or now) " +
            "local elapsed = math.floor((now - last_leak) / 1000) " +
            "local leaked = elapsed * leak_rate " +
            "water = math.max(0, water - leaked) " +
            "if water < capacity then " +
            "  water = water + 1 " +
            "  redis.call('set', water_key, water) " +
            "  redis.call('set', last_leak_key, now) " +
            "  return 1 " +
            "else " +
            "  redis.call('set', last_leak_key, now) " +
            "  return 0 " +
            "end";

    private final Counter successfulAcquireCounter;
    private final Counter failedAcquireCounter;
    private final Timer redisLatencyTimer;
    private final MeterRegistry meterRegistry;

    // Add an @Autowired constructor for Spring
    @Autowired
    public RateLimiterService(MeterRegistry meterRegistry) {
        this(meterRegistry, "localhost", 6379);
    }

    public RateLimiterService(MeterRegistry meterRegistry, String redisHost, int redisPort) {
        this.meterRegistry = meterRegistry;
        this.jedisPool = new JedisPool(redisHost, redisPort);
        this.successfulAcquireCounter = meterRegistry.counter("ratelimiter_acquire_success");
        this.failedAcquireCounter = meterRegistry.counter("ratelimiter_acquire_failed");
        this.redisLatencyTimer = meterRegistry.timer("ratelimiter_redis_latency");
        configMap.put("global", new RateLimiterConfig(10, 1, RateLimiterType.TOKEN_BUCKET));
    }

    public void setConfig(String key, RateLimiterConfig config) {
        configMap.put(key, config);
    }

    public boolean acquire(String key) {
        RateLimiterConfig config = configMap.getOrDefault(key, configMap.get("global"));
        String redisKey = buildRedisKey(config.getType().name().toLowerCase(), key);
        long now = System.currentTimeMillis();
        boolean result;
        try {
            result = redisLatencyTimer.record(() -> {
                // Try Redis with retry
                for (int i = 0; i < REDIS_RETRY_COUNT; i++) {
                    try (Jedis jedis = jedisPool.getResource()) {
                        if (config.getType() == RateLimiterType.TOKEN_BUCKET) {
                            Object luaResult = jedis.eval(TOKEN_BUCKET_LUA,
                                    java.util.Arrays.asList(redisKey + ":tokens", redisKey + ":lastRefill"),
                                    java.util.Arrays.asList(
                                            String.valueOf(config.getCapacity()),
                                            String.valueOf(config.getRefillRate()),
                                            String.valueOf(now)
                                    ));
                            return Long.valueOf(1).equals(luaResult);
                        } else {
                            Object luaResult = jedis.eval(LEAKY_BUCKET_LUA,
                                    java.util.Arrays.asList(redisKey + ":water", redisKey + ":lastLeak"),
                                    java.util.Arrays.asList(
                                            String.valueOf(config.getCapacity()),
                                            String.valueOf(config.getRefillRate()),
                                            String.valueOf(now)
                                    ));
                            return Long.valueOf(1).equals(luaResult);
                        }
                    } catch (Exception ex) {
                        // Retry on transient Redis errors
                        try { Thread.sleep(REDIS_RETRY_DELAY_MS); } catch (InterruptedException ignored) {}
                    }
                }
                return false;
            });
        } catch (Exception ex) {
            // Fallback to in-memory
            return acquireInMemory(key, config, now);
        }
        if (result) {
            successfulAcquireCounter.increment();
        } else {
            failedAcquireCounter.increment();
        }
        return result;
    }

    public RateLimiterStatus getStatus(String key) {
        RateLimiterConfig config = configMap.getOrDefault(key, configMap.get("global"));
        String redisKey = buildRedisKey(config.getType().name().toLowerCase(), key);
        for (int i = 0; i < REDIS_RETRY_COUNT; i++) {
            try (Jedis jedis = jedisPool.getResource()) {
                int tokensLeft;
                if (config.getType() == RateLimiterType.TOKEN_BUCKET) {
                    String val = jedis.get(redisKey + ":tokens");
                    tokensLeft = val == null ? config.getCapacity() : Integer.parseInt(val);
                } else {
                    String val = jedis.get(redisKey + ":water");
                    int water = val == null ? 0 : Integer.parseInt(val);
                    tokensLeft = config.getCapacity() - water;
                }
                return new RateLimiterStatus(tokensLeft, config.getCapacity(), config.getRefillRate(), config.getType());
            } catch (Exception ex) {
                try { Thread.sleep(REDIS_RETRY_DELAY_MS); } catch (InterruptedException ignored) {}
            }
        }
        // Fallback to in-memory
        return getStatusInMemory(key, config);
    }

    // Add this method
    public Map<String, RateLimiterStatus> getAllStatuses() {
        Map<String, RateLimiterStatus> map = new HashMap<>();
        for (String key : configMap.keySet()) {
            map.put(key, getStatus(key));
        }
        return map;
    }

    // In-memory fallback implementation
    boolean acquireInMemory(String key, RateLimiterConfig config, long now) {
        InMemoryBucket bucket = inMemoryBuckets.computeIfAbsent(key, k -> new InMemoryBucket(config, now));
        synchronized (bucket) {
            if (config.getType() == RateLimiterType.TOKEN_BUCKET) {
                long elapsed = (now - bucket.lastRefill) / 1000;
                int refill = (int) (elapsed * config.getRefillRate());
                bucket.tokens = Math.min(config.getCapacity(), bucket.tokens + refill);
                bucket.lastRefill = now;
                if (bucket.tokens > 0) {
                    bucket.tokens--;
                    return true;
                } else {
                    return false;
                }
            } else {
                long elapsed = (now - bucket.lastLeak) / 1000;
                int leaked = (int) (elapsed * config.getRefillRate());
                bucket.water = Math.max(0, bucket.water - leaked);
                bucket.lastLeak = now;
                if (bucket.water < config.getCapacity()) {
                    bucket.water++;
                    return true;
                } else {
                    return false;
                }
            }
        }
    }

    private RateLimiterStatus getStatusInMemory(String key, RateLimiterConfig config) {
        InMemoryBucket bucket = inMemoryBuckets.get(key);
        if (bucket == null) {
            return new RateLimiterStatus(config.getCapacity(), config.getCapacity(), config.getRefillRate(), config.getType());
        }
        synchronized (bucket) {
            int tokensLeft = config.getType() == RateLimiterType.TOKEN_BUCKET
                    ? bucket.tokens
                    : config.getCapacity() - bucket.water;
            return new RateLimiterStatus(tokensLeft, config.getCapacity(), config.getRefillRate(), config.getType());
        }
    }

    private String buildRedisKey(String scope, String key) {
        return String.format("rl:%s:%s", scope, key);
    }

    // Helper class for in-memory fallback
    private static class InMemoryBucket {
        int tokens;
        int water;
        long lastRefill;
        long lastLeak;
        InMemoryBucket(RateLimiterConfig config, long now) {
            this.tokens = config.getCapacity();
            this.water = 0;
            this.lastRefill = now;
            this.lastLeak = now;
        }
    }
}