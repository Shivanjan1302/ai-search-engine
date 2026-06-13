package com.dronzer.aisearch.service;

import com.dronzer.aisearch.dto.RegisterRequest;
import com.dronzer.aisearch.entity.User;
import com.dronzer.aisearch.repository.UserRepository;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;

@Service
public class AuthService {

    private final UserRepository userRepository;

    private final PasswordEncoder passwordEncoder;

    public AuthService(
            UserRepository userRepository,
            PasswordEncoder passwordEncoder) {

        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
    }

    public String register(
            RegisterRequest request) {

        if (userRepository.findByEmail(
                request.getEmail()).isPresent()) {

            return "Email already registered";
        }

        User user = new User();

        user.setEmail(request.getEmail());

        user.setPassword(
                passwordEncoder.encode(
                        request.getPassword()));

        user.setCreatedAt(
                LocalDateTime.now());

        userRepository.save(user);

        return "User registered successfully";
    }
}