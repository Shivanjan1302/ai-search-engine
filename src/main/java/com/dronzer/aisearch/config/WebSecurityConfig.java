package com.dronzer.aisearch.config;

import com.dronzer.aisearch.security.JwtAuthenticationFilter;
import com.dronzer.aisearch.oauth.GoogleOAuth2FailureHandler;
import com.dronzer.aisearch.oauth.GoogleOAuth2SuccessHandler;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.security.web.authentication.HttpStatusEntryPoint;
import org.springframework.http.HttpStatus;
import org.springframework.security.web.util.matcher.AntPathRequestMatcher;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

@Configuration
public class WebSecurityConfig {

    private final JwtAuthenticationFilter
            jwtAuthenticationFilter;

        private final GoogleOAuth2SuccessHandler googleSuccessHandler;
        private final GoogleOAuth2FailureHandler googleFailureHandler;

    public WebSecurityConfig(
            JwtAuthenticationFilter
                    jwtAuthenticationFilter,
            GoogleOAuth2SuccessHandler googleSuccessHandler,
            GoogleOAuth2FailureHandler googleFailureHandler) {

        this.jwtAuthenticationFilter =
                jwtAuthenticationFilter;
        this.googleSuccessHandler = googleSuccessHandler;
        this.googleFailureHandler = googleFailureHandler;
    }

    @Bean
    public SecurityFilterChain securityFilterChain(
            HttpSecurity http)
            throws Exception {

        http
                .cors(cors -> { })
                .csrf(csrf -> csrf.disable())
                .sessionManagement(session -> session
                        .sessionCreationPolicy(SessionCreationPolicy.IF_REQUIRED))
                .authorizeHttpRequests(auth ->
                        auth
                                .requestMatchers(
                                        "/auth/**")
                                .permitAll()

                                .requestMatchers("/error")
                                .permitAll()

                                .requestMatchers("/oauth2/**", "/login/oauth2/**")
                                .permitAll()

                                .anyRequest()
                                .authenticated())
                .exceptionHandling(exceptions -> exceptions
                        .defaultAuthenticationEntryPointFor(
                                new HttpStatusEntryPoint(HttpStatus.UNAUTHORIZED),
                                new AntPathRequestMatcher("/**")))

                .oauth2Login(oauth -> oauth
                        .successHandler(googleSuccessHandler)
                        .failureHandler(googleFailureHandler))
                .addFilterBefore(
                        jwtAuthenticationFilter,
                        UsernamePasswordAuthenticationFilter.class);

        return http.build();
    }
}
