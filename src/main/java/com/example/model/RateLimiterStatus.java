package com.example.model;

public class RateLimiterStatus {
    private int tokensLeft;
    private int capacity;
    private int refillRate;
    private RateLimiterType type;

    public RateLimiterStatus(int tokensLeft, int capacity, int refillRate, RateLimiterType type) {
        this.tokensLeft = tokensLeft;
        this.capacity = capacity;
        this.refillRate = refillRate;
        this.type = type;
    }

    public int getTokensLeft() { return tokensLeft; }
    public int getCapacity() { return capacity; }
    public int getRefillRate() { return refillRate; }
    public RateLimiterType getType() { return type; }
}