package com.dronzer.aisearch.exception;

import com.dronzer.aisearch.rag.GenerationException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class GlobalExceptionHandlerTest {

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.standaloneSetup(new ProbeController())
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
    }

    @Test
    void translatesUnexpectedExceptionWithoutLeakingInternals() throws Exception {
        mockMvc.perform(get("/probe/unexpected"))
                .andExpect(status().isInternalServerError())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.status").value(500))
                .andExpect(jsonPath("$.error").value("Internal Server Error"))
                .andExpect(jsonPath("$.message").value("Unexpected server error"))
                .andExpect(jsonPath("$.path").value("/probe/unexpected"))
                .andExpect(content().string(not(containsString("User not found"))))
                .andExpect(content().string(not(containsString("RuntimeException"))))
                .andExpect(content().string(not(containsString("com.dronzer"))));
    }

    @Test
    void translatesChunkOwnershipViolationIntoNotFound() throws Exception {
        mockMvc.perform(get("/probe/owned-chunk"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.status").value(404))
                .andExpect(jsonPath("$.message").value("Chunk is not owned by the requested user"));
    }

    @Test
    void translatesMissingRequestParameterIntoApiErrorResponse() throws Exception {
        mockMvc.perform(get("/probe/missing-parameter"))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.error").value("Bad Request"))
                .andExpect(jsonPath("$.message").value("Required request parameter 'q' is missing"))
                .andExpect(jsonPath("$.path").value("/probe/missing-parameter"));
    }

    @Test
    void translatesUnsupportedMethodIntoApiErrorResponse() throws Exception {
        mockMvc.perform(get("/probe/post-only"))
                .andExpect(status().isMethodNotAllowed())
                .andExpect(jsonPath("$.status").value(405))
                .andExpect(jsonPath("$.message").value("HTTP method is not supported for this endpoint"));
    }

    @Test
    void keepsGeminiAndWebSearchFailuresDistinguishable() throws Exception {
        mockMvc.perform(get("/probe/gemini"))
                .andExpect(status().isBadGateway())
                .andExpect(jsonPath("$.status").value(502))
                .andExpect(jsonPath("$.code").value("GEMINI_UNAVAILABLE"))
                .andExpect(jsonPath("$.message").value("Gemini service is temporarily unavailable"));

        mockMvc.perform(get("/probe/web-search"))
                .andExpect(status().isBadGateway())
                .andExpect(jsonPath("$.status").value(502))
                .andExpect(jsonPath("$.code").value("WEB_SEARCH_UNAVAILABLE"))
                .andExpect(jsonPath("$.message").value("Web search is temporarily unavailable"));
    }

    @Test
    void exposesUpstreamGeminiStatusAsSafeDiagnostics() throws Exception {
        mockMvc.perform(get("/probe/gemini-quota"))
                .andExpect(status().isTooManyRequests())
                .andExpect(jsonPath("$.status").value(429))
                .andExpect(jsonPath("$.code").value("GEMINI_QUOTA_EXCEEDED"))
                .andExpect(jsonPath("$.message").value("Gemini request quota is currently exhausted"));

        mockMvc.perform(get("/probe/gemini-bad-request"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.code").value("GEMINI_BAD_REQUEST"));

        mockMvc.perform(get("/probe/gemini-forbidden"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.status").value(403))
                .andExpect(jsonPath("$.code").value("GEMINI_FORBIDDEN"));
    }

    @Test
    void recoversUpstreamStatusThroughGroundedGenerationWrapping() throws Exception {
        mockMvc.perform(get("/probe/generation-gemini-quota"))
                .andExpect(status().isTooManyRequests())
                .andExpect(jsonPath("$.status").value(429))
                .andExpect(jsonPath("$.code").value("GEMINI_QUOTA_EXCEEDED"))
                .andExpect(content().string(not(containsString("RESOURCE_EXHAUSTED"))))
                .andExpect(content().string(not(containsString("x-goog-api-key"))))
                .andExpect(content().string(not(containsString("test-key"))));
    }

    @Test
    void keepsNonGeminiGenerationFailuresOnTheGenericServerError() throws Exception {
        mockMvc.perform(get("/probe/generation-unexpected"))
                .andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.status").value(500))
                .andExpect(jsonPath("$.message").value("Unexpected server error"))
                .andExpect(content().string(not(containsString("secret upstream detail"))));
    }

    @RestController
    static class ProbeController {

        @GetMapping("/probe/unexpected")
        String unexpected() {
            throw new RuntimeException("User not found");
        }

        @GetMapping("/probe/owned-chunk")
        String ownedChunk() {
            throw new ResourceNotFoundException("Chunk is not owned by the requested user");
        }

        @GetMapping("/probe/missing-parameter")
        String missingParameter(@RequestParam("q") String query) {
            return query;
        }

        @PostMapping("/probe/post-only")
        String postOnly() {
            return "ok";
        }

        @GetMapping("/probe/gemini")
        String gemini() {
            throw new GeminiUpstreamException();
        }

        @GetMapping("/probe/gemini-quota")
        String geminiQuota() {
            throw new GeminiUpstreamException(429);
        }

        @GetMapping("/probe/gemini-bad-request")
        String geminiBadRequest() {
            throw new GeminiUpstreamException(400);
        }

        @GetMapping("/probe/gemini-forbidden")
        String geminiForbidden() {
            throw new GeminiUpstreamException(403);
        }

        @GetMapping("/probe/generation-gemini-quota")
        String generationGeminiQuota() {
            throw new GenerationException("generation model invocation failed",
                    new GeminiUpstreamException(429));
        }

        @GetMapping("/probe/generation-unexpected")
        String generationUnexpected() {
            throw new GenerationException("generation model invocation failed",
                    new IllegalStateException("secret upstream detail"));
        }

        @GetMapping("/probe/web-search")
        String webSearch() {
            throw new WebSearchUpstreamException();
        }
    }
}