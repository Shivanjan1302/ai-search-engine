package com.dronzer.aisearch.controller;

import com.dronzer.aisearch.dto.WebSearchResponse;
import com.dronzer.aisearch.service.WebSearchService;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@Validated
@RestController
@RequestMapping("/search")
public class WebSearchController {

    private static final int MAX_QUERY_LENGTH = 500;

    private final WebSearchService webSearchService;

    public WebSearchController(WebSearchService webSearchService) {
        this.webSearchService = webSearchService;
    }

    @GetMapping("/web")
    public WebSearchResponse search(
            @RequestParam("q")
            @NotBlank(message = "query must not be blank")
            @Size(max = MAX_QUERY_LENGTH, message = "query must not exceed 500 characters")
            String query,
            @RequestParam(defaultValue = "10")
            @Min(value = 1, message = "limit must be at least 1")
            @Max(value = 20, message = "limit must not exceed 20")
            int limit) {
        return webSearchService.search(query, limit);
    }
}
