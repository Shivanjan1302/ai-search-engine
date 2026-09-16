package com.dronzer.aisearch.dto;

import java.util.List;

public record RagResponse(
        String answer,
        List<RagSource> sources,
        List<WebSearchResult> webSources,
        RagOrigin origin
) {

    public RagResponse(String answer, List<RagSource> sources) {
        this(answer, sources, List.of(), sources.isEmpty()
                ? RagOrigin.INSUFFICIENT_EVIDENCE
                : RagOrigin.DOCUMENTS);
    }
}
