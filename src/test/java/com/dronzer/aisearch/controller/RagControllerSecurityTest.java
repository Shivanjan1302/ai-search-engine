package com.dronzer.aisearch.controller;

import com.dronzer.aisearch.dto.RagResponse;
import com.dronzer.aisearch.service.RagService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

import java.util.Collections;

import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@TestPropertySource(properties = "jwt.secret=test-secret-key-that-is-at-least-32-bytes-long")
class RagControllerSecurityTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private RagService ragService;

    @Test
    void authenticatedValidQuestionKeepsExistingRagBehavior() throws Exception {
        when(ragService.askQuestion("What is RAG?", "user@example.test"))
                .thenReturn(new RagResponse("It retrieves relevant context.", Collections.emptyList()));

        mockMvc.perform(post("/rag/ask")
                        .with(authenticatedUser())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"question\":\"What is RAG?\"}"))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.answer").value("It retrieves relevant context."));

        verify(ragService).askQuestion(eq("What is RAG?"), eq("user@example.test"));
    }

    @Test
    void authenticatedBlankQuestionReturnsBadRequest() throws Exception {
        assertBadRequest("{\"question\":\"\"}");
    }

    @Test
    void authenticatedWhitespaceQuestionReturnsBadRequest() throws Exception {
        assertBadRequest("{\"question\":\"   \"}");
    }

    @Test
    void authenticatedNullQuestionReturnsBadRequest() throws Exception {
        assertBadRequest("{\"question\":null}");
    }

    @Test
    void authenticatedMalformedJsonReturnsBadRequest() throws Exception {
        mockMvc.perform(post("/rag/ask")
                        .with(authenticatedUser())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"question\":"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.error").value("Bad Request"))
                .andExpect(jsonPath("$.path").value("/rag/ask"));
    }

    @Test
    void unauthenticatedQuestionRemainsRejected() throws Exception {
        mockMvc.perform(post("/rag/ask")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"question\":\"What is RAG?\"}"))
                .andExpect(status().is4xxClientError());
    }

    private void assertBadRequest(String body) throws Exception {
        mockMvc.perform(post("/rag/ask")
                        .with(authenticatedUser())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.error").value("Bad Request"))
                .andExpect(jsonPath("$.message").value("question must not be blank"))
                .andExpect(jsonPath("$.path").value("/rag/ask"));
    }

    private RequestPostProcessor authenticatedUser() {
        Authentication authentication = new UsernamePasswordAuthenticationToken(
                "user@example.test", null, Collections.emptyList());
        return SecurityMockMvcRequestPostProcessors.authentication(authentication);
    }
}