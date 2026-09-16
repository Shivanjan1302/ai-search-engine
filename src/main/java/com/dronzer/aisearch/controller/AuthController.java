package com.dronzer.aisearch.controller;

import com.dronzer.aisearch.dto.LoginRequest;
import com.dronzer.aisearch.dto.LoginResponse;
import com.dronzer.aisearch.dto.RegisterRequest;
import com.dronzer.aisearch.dto.RegisterResponse;
import com.dronzer.aisearch.service.AuthService;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.*;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

@RestController
@RequestMapping("/auth")
public class AuthController {

    private final AuthService authService;

    public AuthController(
            AuthService authService) {

        this.authService = authService;
    }

    @PostMapping("/register")
    @ResponseStatus(HttpStatus.CREATED)
    public RegisterResponse register(
            @Valid @RequestBody RegisterRequest request) {

        authService.register(request);
        return new RegisterResponse("User registered successfully");
    }

    @PostMapping("/login")
    public LoginResponse login(
            @Valid @RequestBody LoginRequest request) {

        return new LoginResponse(authService.login(request));
    }

    @GetMapping("/oauth-token")
    public ResponseEntity<String> oauthToken(
            @CookieValue(name = "dronzer_oauth_token", required = false) String token,
            HttpServletResponse response) {
        if (token == null || token.isBlank()) {
            return ResponseEntity.status(401).body("OAuth login is not available");
        }

        Cookie cleared = new Cookie("dronzer_oauth_token", "");
        cleared.setHttpOnly(true);
        cleared.setPath("/");
        cleared.setMaxAge(0);
        response.addCookie(cleared);
        return ResponseEntity.ok(token);
    }
}