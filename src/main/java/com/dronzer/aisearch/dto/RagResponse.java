package com.dronzer.aisearch.dto;

import java.util.List;

public record RagResponse(
        String answer,
        List<RagSource> sources
) {
}
