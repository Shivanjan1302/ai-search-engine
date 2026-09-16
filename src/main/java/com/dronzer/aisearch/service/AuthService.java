package com.dronzer.aisearch.service;

import com.dronzer.aisearch.dto.LoginRequest;
import com.dronzer.aisearch.dto.RegisterRequest;
import com.dronzer.aisearch.entity.User;
import com.dronzer.aisearch.exception.EmailAlreadyRegisteredException;
import com.dronzer.aisearch.exception.InvalidCredentialsException;
import com.dronzer.aisearch.repository.UserRepository;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;

@Service
public class AuthService {

    private final UserRepository userRepository;

    private final PasswordEncoder passwordEncoder;

    private final JwtService jwtService;

    public AuthService(
            UserRepository userRepository,
            PasswordEncoder passwordEncoder,
            JwtService jwtService) {

        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.jwtService = jwtService;
    }

    public void register(
            RegisterRequest request) {

        String email = requireEmail(request.getEmail());
        String password = requirePassword(request.getPassword());

        if (userRepository.findByEmail(
                email).isPresent()) {

            throw new EmailAlreadyRegisteredException();
        }

        User user = new User();

        user.setEmail(email);

        user.setPassword(
                encodePassword(password));

        user.setCreatedAt(
                LocalDateTime.now());

        userRepository.save(user);
    }

    public String login(
            LoginRequest request) {

        String email = requireEmail(request.getEmail());
        String password = requirePassword(request.getPassword());

        User user =
                userRepository.findByEmail(email)
                        .orElse(null);

        if (user == null || user.getPassword() == null) {

            throw new InvalidCredentialsException();
        }

        if (!passwordMatches(password, user.getPassword())) {

            throw new InvalidCredentialsException();
        }

        return jwtService.generateToken(
                user.getEmail());
    }

    private String requireEmail(String email) {
        if (email == null || email.isBlank()) {
            throw new IllegalArgumentException("Email must not be blank");
        }
        return email;
    }

    private String requirePassword(String password) {
        if (password == null || password.isBlank()) {
            throw new IllegalArgumentException("Password must not be blank");
        }
        return password;
    }

    private String encodePassword(String password) {
        try {
            return passwordEncoder.encode(password);
        } catch (IllegalArgumentException exception) {
            throw new IllegalArgumentException("Password could not be processed");
        }
    }

    private boolean passwordMatches(String rawPassword, String encodedPassword) {
        try {
            return passwordEncoder.matches(rawPassword, encodedPassword);
        } catch (IllegalArgumentException exception) {
            return false;
        }
    }
}