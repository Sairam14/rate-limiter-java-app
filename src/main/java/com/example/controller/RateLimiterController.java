package com.example.controller;

import com.example.model.*;
import com.example.service.RateLimiterService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/")
public class RateLimiterController {
    @Autowired
    private RateLimiterService rateLimiterService;

    @PostMapping("/acquire")
    public ResponseEntity<String> acquire(@RequestParam String key) {
        boolean allowed = rateLimiterService.acquire(key);
        if (allowed) {
            return ResponseEntity.ok("Allowed");
        } else {
            return ResponseEntity.status(HttpStatus.TOO_MANY_REQUESTS).body("Rate limit exceeded");
        }
    }

    @GetMapping("/status")
    public RateLimiterStatus status(@RequestParam String key) {
        return rateLimiterService.getStatus(key);
    }

    // Optional: API to set per-user/key/global config at runtime
    @PostMapping("/config")
    public ResponseEntity<String> setConfig(
            @RequestParam String key,
            @RequestParam int capacity,
            @RequestParam int refillRate,
            @RequestParam RateLimiterType type) {
        rateLimiterService.setConfig(key, new RateLimiterConfig(capacity, refillRate, type));
        return ResponseEntity.ok("Config updated");
    }
}