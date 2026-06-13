package com.dronzer.aisearch.controller;

import com.dronzer.aisearch.dto.RegisterRequest;
import com.dronzer.aisearch.service.AuthService;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/auth")
public class AuthController {

    private final AuthService authService;

    public AuthController(
            AuthService authService) {

        this.authService = authService;
    }

    @PostMapping("/register")
    public String register(
            @RequestBody RegisterRequest request) {

        return authService.register(request);
    }
}