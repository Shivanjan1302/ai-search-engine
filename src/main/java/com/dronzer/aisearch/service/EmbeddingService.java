package com.dronzer.aisearch.service;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

@Service
public class EmbeddingService {

    private final RestTemplate restTemplate;

    @Value("${gemini.api.key}")
    private String apiKey;

    public EmbeddingService(
            RestTemplate restTemplate) {

        this.restTemplate = restTemplate;
    }
}