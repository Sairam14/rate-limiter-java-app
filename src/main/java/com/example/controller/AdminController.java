package com.example.controller;

import com.example.model.RateLimiterStatus;
import com.example.service.RateLimiterService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.util.*;

@RestController
@RequestMapping("/admin")
public class AdminController {
    @Autowired
    private RateLimiterService rateLimiterService;

    @GetMapping("/all-status")
    public Map<String, RateLimiterStatus> allStatus() {
        return rateLimiterService.getAllStatuses();
    }
}