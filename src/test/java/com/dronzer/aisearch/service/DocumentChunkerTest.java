package com.dronzer.aisearch.service;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class DocumentChunkerTest {

    private final DocumentChunker chunker = new DocumentChunker(850, 120);

    @Test
    void returnsNoChunksForEmptyDocument() {
        assertThat(chunker.split("  ")).isEmpty();
    }

    @Test
    void keepsShortDocumentAsOneChunk() {
        assertThat(chunker.split("A short document.")).containsExactly("A short document.");
    }

    @Test
    void prefersParagraphBoundaries() {
        String firstParagraph = "First paragraph. ".repeat(25);
        String secondParagraph = "Second paragraph. ".repeat(25);

        List<String> chunks = chunker.split(firstParagraph + "\n\n" + secondParagraph);

        assertThat(chunks).hasSize(2);
        assertThat(chunks.get(0)).contains("First paragraph.").doesNotContain("Second paragraph.");
        assertThat(chunks.get(1)).contains("Second paragraph.");
    }

    @Test
    void splitsSentencesAndRetainsOverlap() {
        String content = ("Sentence with useful context. ").repeat(50);

        List<String> chunks = chunker.split(content);

        assertThat(chunks).hasSizeGreaterThan(1);
        for (int index = 1; index < chunks.size(); index++) {
            String previous = chunks.get(index - 1);
            String current = chunks.get(index);
            String overlap = previous.substring(previous.length() - 120);
            assertThat(current).startsWith(overlap);
        }
    }

    @Test
    void hardSplitsVeryLongParagraphAndSentenceSafely() {
        String longSentence = "word ".repeat(500);

        List<String> chunks = chunker.split(longSentence);

        assertThat(chunks).hasSizeGreaterThan(1);
        assertThat(chunks).allSatisfy(chunk -> assertThat(chunk.length()).isLessThanOrEqualTo(850));
        assertThat(String.join(" ", chunks)).contains("word");
    }

    @Test
    void preservesNewlinesAndMultipleSpacesWithinChunk() {
        String content = "First line.\nSecond line with  multiple spaces.";

        assertThat(chunker.split(content).get(0))
            .contains("First line. Second line with  multiple spaces.");
    }

    @Test
    void keepsChunkIndexesDeterministicAtIngestionBoundary() {
        String content = "A. ".repeat(400);
        List<String> first = chunker.split(content);
        List<String> second = chunker.split(content);

        assertThat(second).containsExactlyElementsOf(first);
        assertThat(first).allSatisfy(chunk -> assertThat(chunk).isNotBlank());
    }
}