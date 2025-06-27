package com.example.model;

public class RateLimiterConfig {
    private int capacity;
    private int refillRate;
    private RateLimiterType type;

    public RateLimiterConfig(int capacity, int refillRate, RateLimiterType type) {
        this.capacity = capacity;
        this.refillRate = refillRate;
        this.type = type;
    }

    public int getCapacity() { return capacity; }
    public int getRefillRate() { return refillRate; }
    public RateLimiterType getType() { return type; }
}