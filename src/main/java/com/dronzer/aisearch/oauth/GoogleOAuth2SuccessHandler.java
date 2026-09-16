package com.dronzer.aisearch.oauth;

import com.dronzer.aisearch.entity.User;
import com.dronzer.aisearch.repository.UserRepository;
import com.dronzer.aisearch.service.JwtService;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.time.LocalDateTime;
import java.util.Map;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.core.OAuth2AuthenticationException;
import org.springframework.security.oauth2.core.OAuth2Error;
import org.springframework.security.oauth2.core.user.OAuth2User;
import org.springframework.security.web.authentication.AuthenticationSuccessHandler;
import org.springframework.stereotype.Component;
import org.springframework.web.util.UriComponentsBuilder;

@Component
public class GoogleOAuth2SuccessHandler implements AuthenticationSuccessHandler {

    private static final String TOKEN_COOKIE = "dronzer_oauth_token";

    private final UserRepository userRepository;
    private final JwtService jwtService;
    private final String frontendUrl;
    private final boolean secureCookie;

    public GoogleOAuth2SuccessHandler(
            UserRepository userRepository,
            JwtService jwtService,
            @Value("${app.frontend-url:http://localhost:5173}") String frontendUrl,
            @Value("${app.oauth.secure-cookie:false}") boolean secureCookie) {
        this.userRepository = userRepository;
        this.jwtService = jwtService;
        this.frontendUrl = frontendUrl;
        this.secureCookie = secureCookie;
    }

    @Override
    public void onAuthenticationSuccess(
            HttpServletRequest request,
            HttpServletResponse response,
            Authentication authentication) throws IOException, ServletException {
        OAuth2User googleUser = (OAuth2User) authentication.getPrincipal();
        Map<String, Object> attributes = googleUser.getAttributes();
        String subject = text(attributes, "sub");
        String email = text(attributes, "email");
        if (subject.isBlank() || email.isBlank() || !isVerified(attributes.get("email_verified"))) {
            throw oauthFailure("Google account did not provide a verified email");
        }

        User user = userRepository.findByGoogleSubject(subject).orElseGet(() -> {
            User existing = userRepository.findByEmail(email).orElse(null);
            if (existing != null && existing.getGoogleSubject() == null) {
                throw oauthFailure("This email is already registered for password login");
            }
            User created = new User(email, null, LocalDateTime.now());
            created.setAuthProvider("google");
            created.setGoogleSubject(subject);
            return userRepository.save(created);
        });

        if (!email.equalsIgnoreCase(user.getEmail())) {
            throw oauthFailure("Google account identity does not match the linked account");
        }

        Cookie token = new Cookie(TOKEN_COOKIE, jwtService.generateToken(user.getEmail()));
        token.setHttpOnly(true);
        token.setSecure(secureCookie);
        token.setPath("/");
        token.setMaxAge(300);
        response.addCookie(token);
        response.sendRedirect(UriComponentsBuilder.fromUriString(frontendUrl)
                .path("/auth/callback")
                .build()
                .toUriString());
    }

    private OAuth2AuthenticationException oauthFailure(String message) {
        return new OAuth2AuthenticationException(new OAuth2Error("invalid_google_identity"), message);
    }

    private boolean isVerified(Object value) {
        return value instanceof Boolean booleanValue && booleanValue;
    }

    private String text(Map<String, Object> attributes, String key) {
        Object value = attributes.get(key);
        return value == null ? "" : value.toString().trim();
    }
}