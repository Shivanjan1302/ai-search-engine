package com.dronzer.aisearch.client;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;

@Component
public class GeminiClient {

    private final RestTemplate restTemplate;

    @Value("${gemini.api.key}")
    private String apiKey;

    public GeminiClient(
            RestTemplate restTemplate) {

        this.restTemplate = restTemplate;
    }

}