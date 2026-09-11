package com.dronzer.aisearch.controller;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MockMvc;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import com.dronzer.aisearch.exception.GeminiUpstreamException;
import com.dronzer.aisearch.exception.GlobalExceptionHandler;
import com.dronzer.aisearch.service.DocumentService;

class DocumentControllerTest {

    private MockMvc mockMvc;
        private DocumentService documentService;

    @BeforeEach
    void setUp() {
        documentService = mock(DocumentService.class);
        mockMvc = MockMvcBuilders
                .standaloneSetup(new DocumentController(documentService))
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
    }

    @Test
    void rejectsUploadLargerThanConfiguredMaximumWithStableError() throws Exception {
        MockMultipartFile file = new MockMultipartFile(
                "file",
                "large.txt",
                "text/plain",
                new byte[10 * 1024 * 1024 + 1]);

        mockMvc.perform(multipart("/documents/upload").file(file))
                .andExpect(status().isPayloadTooLarge())
                .andExpect(jsonPath("$.status").value(413))
                .andExpect(jsonPath("$.message")
                        .value("Uploaded file exceeds the maximum allowed size"));
    }

    @Test
    void translatesGeminiFailureIntoStableBadGatewayResponse() throws Exception {
        when(documentService.saveDocument(anyString(), anyString(),
                org.mockito.ArgumentMatchers.any()))
                .thenThrow(new GeminiUpstreamException());
        MockMultipartFile file = new MockMultipartFile(
                "file", "notes.txt", "text/plain", "normal text".getBytes());

        mockMvc.perform(multipart("/documents/upload").file(file))
                .andExpect(status().isBadGateway())
                .andExpect(jsonPath("$.status").value(502))
                .andExpect(jsonPath("$.message")
                        .value("Gemini service is temporarily unavailable"));
    }
}