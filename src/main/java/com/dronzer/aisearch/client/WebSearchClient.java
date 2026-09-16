package com.dronzer.aisearch.client;

import java.util.List;

import com.dronzer.aisearch.dto.WebSearchResult;

public interface WebSearchClient {

    List<WebSearchResult> search(String query, int limit);
}