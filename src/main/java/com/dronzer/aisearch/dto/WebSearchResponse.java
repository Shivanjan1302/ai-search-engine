package com.dronzer.aisearch.dto;

import java.util.List;

public record WebSearchResponse(
        String query,
        List<WebSearchResult> results) {
}