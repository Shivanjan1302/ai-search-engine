package com.dronzer.aisearch.oauth;

import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.web.authentication.AuthenticationFailureHandler;
import org.springframework.stereotype.Component;
import org.springframework.web.util.UriComponentsBuilder;

@Component
public class GoogleOAuth2FailureHandler implements AuthenticationFailureHandler {

    private final String frontendUrl;

    public GoogleOAuth2FailureHandler(
            @Value("${app.frontend-url:http://localhost:5173}") String frontendUrl) {
        this.frontendUrl = frontendUrl;
    }

    @Override
    public void onAuthenticationFailure(
            HttpServletRequest request,
            HttpServletResponse response,
            AuthenticationException exception) throws IOException, ServletException {
        response.sendRedirect(UriComponentsBuilder.fromUriString(frontendUrl)
                .path("/login")
                .queryParam("oauth", "failed")
                .build()
                .toUriString());
    }
}