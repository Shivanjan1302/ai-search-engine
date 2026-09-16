package com.dronzer.aisearch.controller;

import com.dronzer.aisearch.client.WebSearchClient;
import com.dronzer.aisearch.dto.DocumentResponse;
import com.dronzer.aisearch.entity.Document;
import com.dronzer.aisearch.service.DocumentService;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.Collections;

import static org.hamcrest.Matchers.containsString;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@TestPropertySource(properties = {
        "jwt.secret=test-secret-key-that-is-at-least-32-bytes-long",
        "app.document.max-pdf-pages=2"
})
class RequestContractIntegrationTest {

    private static final String EMAIL = "contract@example.test";

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private DocumentService documentService;

    @MockitoBean
    private WebSearchClient webSearchClient;

    @Test
    void missingKeywordParameterUsesApiErrorResponse() throws Exception {
        mockMvc.perform(get("/documents/search").with(authenticatedUser()))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.error").value("Bad Request"))
                .andExpect(jsonPath("$.message").value("Required request parameter 'keyword' is missing"))
                .andExpect(jsonPath("$.path").value("/documents/search"));
    }

    @Test
    void missingSemanticSearchQueryParameterUsesApiErrorResponse() throws Exception {
        mockMvc.perform(get("/documents/semantic-search").with(authenticatedUser()))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.message").value("Required request parameter 'query' is missing"))
                .andExpect(jsonPath("$.path").value("/documents/semantic-search"));
    }

    @Test
    void missingWebSearchQueryParameterUsesApiErrorResponse() throws Exception {
        mockMvc.perform(get("/search/web").with(authenticatedUser()))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.message").value("Required request parameter 'q' is missing"))
                .andExpect(jsonPath("$.path").value("/search/web"));
    }

    @Test
    void rejectsDocumentWithoutFilenameWithBadRequest() throws Exception {
        mockMvc.perform(post("/documents")
                        .with(authenticatedUser())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"content\":\"Docker notes\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.message").value("filename must not be blank"))
                .andExpect(jsonPath("$.path").value("/documents"));
    }

    @Test
    void rejectsDocumentWithOversizedFilenameWithBadRequest() throws Exception {
        mockMvc.perform(post("/documents")
                        .with(authenticatedUser())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"filename\":\"" + "a".repeat(256) + "\",\"content\":\"Docker notes\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.message").value("filename must not exceed 255 characters"));
    }

    @Test
    void rejectsPdfAbovePageLimitWithUnprocessableEntityInsteadOfPayloadTooLarge() throws Exception {
        MockMultipartFile pdf = new MockMultipartFile(
                "file", "long-document.pdf", "application/pdf", pdfWithPages(3));

        mockMvc.perform(multipart("/documents/upload")
                        .file(pdf)
                        .with(authenticatedUser()))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.status").value(422))
                .andExpect(jsonPath("$.message").value(containsString("page count")))
                .andExpect(jsonPath("$.message").value(containsString("2")));
    }

    @Test
    void acceptsTextUploadWithinConfiguredLimits() throws Exception {
        Document document = new Document("notes.txt", "Docker notes");
        when(documentService.saveDocument(eq("notes.txt"), anyString(), eq(EMAIL)))
                .thenReturn(document);
        when(documentService.toResponse(document))
                .thenReturn(new DocumentResponse(7L, "notes.txt", LocalDateTime.now()));

        MockMultipartFile text = new MockMultipartFile(
                "file", "notes.txt", "text/plain", "Docker notes".getBytes(StandardCharsets.UTF_8));

        mockMvc.perform(multipart("/documents/upload")
                        .file(text)
                        .with(authenticatedUser()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.filename").value("notes.txt"));
    }

    @Test
    void missingUploadPartUsesApiErrorResponse() throws Exception {
        mockMvc.perform(multipart("/documents/upload")
                        .with(authenticatedUser()))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.message").value("Required request part 'file' is missing"))
                .andExpect(jsonPath("$.path").value("/documents/upload"));
    }

    private byte[] pdfWithPages(int pageCount) throws IOException {
        try (PDDocument pdf = new PDDocument();
                ByteArrayOutputStream output = new ByteArrayOutputStream()) {
            for (int page = 0; page < pageCount; page++) {
                pdf.addPage(new PDPage());
            }
            pdf.save(output);
            return output.toByteArray();
        }
    }

    private RequestPostProcessor authenticatedUser() {
        Authentication authentication = new UsernamePasswordAuthenticationToken(
                EMAIL, null, Collections.emptyList());
        return SecurityMockMvcRequestPostProcessors.authentication(authentication);
    }
}
