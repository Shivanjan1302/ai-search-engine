package com.dronzer.aisearch.service;

import java.net.URI;
import java.net.URISyntaxException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import org.springframework.stereotype.Service;

import com.dronzer.aisearch.client.WebSearchClient;
import com.dronzer.aisearch.dto.WebSearchResult;
import com.dronzer.aisearch.dto.WebSearchResponse;

@Service
public class WebSearchService {

    private final WebSearchClient client;

    public WebSearchService(WebSearchClient client) {
        this.client = client;
    }

    public WebSearchResponse search(String query, int limit) {
        return new WebSearchResponse(query, client.search(query, limit));
    }
}
