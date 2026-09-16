package com.dronzer.aisearch.controller;

import com.dronzer.aisearch.client.WebSearchClient;
import com.dronzer.aisearch.dto.WebSearchResult;
import com.dronzer.aisearch.service.JwtService;
import java.util.Collections;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;
import org.springframework.web.client.RestClientException;

import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@TestPropertySource(properties = "jwt.secret=test-secret-key-that-is-at-least-32-bytes-long")
class WebSearchControllerSecurityTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JwtService jwtService;

    @MockitoBean
    private WebSearchClient webSearchClient;

    @Test
    void authenticatedSearchReturnsNormalizedResults() throws Exception {
        when(webSearchClient.search("java 25", 3)).thenReturn(List.of(
                new WebSearchResult("Java 25", "https://example.com/java-25", "Fresh details", "example.com")));

        mockMvc.perform(get("/search/web")
                        .param("q", "java 25")
                        .param("limit", "3")
                        .with(authenticatedUser("searcher@example.test")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.query").value("java 25"))
                .andExpect(jsonPath("$.results[0].url").value("https://example.com/java-25"))
                .andExpect(jsonPath("$.results[0].publisher").value("example.com"));

        verify(webSearchClient).search(eq("java 25"), eq(3));
    }

    @Test
    void blankQueryIsRejected() throws Exception {
        mockMvc.perform(get("/search/web")
                        .param("q", "   ")
                        .with(authenticatedUser("searcher@example.test")))
                .andExpect(status().isBadRequest());
    }

    @Test
    void oversizedLimitIsRejected() throws Exception {
        mockMvc.perform(get("/search/web")
                        .param("q", "java")
                        .param("limit", "21")
                        .with(authenticatedUser("searcher@example.test")))
                .andExpect(status().isBadRequest());
    }

    @Test
    void providerFailureBecomesBadGateway() throws Exception {
        when(webSearchClient.search("java", 10)).thenThrow(new com.dronzer.aisearch.exception.WebSearchUpstreamException());

        mockMvc.perform(get("/search/web")
                        .param("q", "java")
                        .with(authenticatedUser("searcher@example.test")))
                .andExpect(status().isBadGateway())
                .andExpect(jsonPath("$.message").value("Web search is temporarily unavailable"));
    }

    @Test
    void unauthenticatedSearchIsRejected() throws Exception {
        mockMvc.perform(get("/search/web").param("q", "java"))
                .andExpect(status().is4xxClientError());
    }

    @Test
    void jwtAuthenticationWorksForSearch() throws Exception {
        when(webSearchClient.search("java", 10)).thenReturn(List.of());

        mockMvc.perform(get("/search/web")
                        .param("q", "java")
                        .header("Authorization", "Bearer " + jwtService.generateToken("searcher@example.test")))
                .andExpect(status().isOk());
    }

    private RequestPostProcessor authenticatedUser(String email) {
        Authentication authentication = new UsernamePasswordAuthenticationToken(
                email, null, Collections.emptyList());
        return SecurityMockMvcRequestPostProcessors.authentication(authentication);
    }
}