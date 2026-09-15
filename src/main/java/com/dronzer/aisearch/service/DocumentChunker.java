package com.dronzer.aisearch.service;

import java.util.ArrayList;
import java.util.List;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Component
public class DocumentChunker {

    private final int targetChunkSize;
    private final int chunkOverlap;

    public DocumentChunker(
            @Value("${app.document.chunk-size:850}") int targetChunkSize,
            @Value("${app.document.chunk-overlap:120}") int chunkOverlap) {
        if (targetChunkSize <= 0 || chunkOverlap < 0 || chunkOverlap >= targetChunkSize) {
            throw new IllegalArgumentException("Chunk size must be positive and overlap must be smaller than chunk size");
        }
        this.targetChunkSize = targetChunkSize;
        this.chunkOverlap = chunkOverlap;
    }

    public List<String> split(String content) {
        if (content == null || content.isBlank()) {
            return List.of();
        }

        List<String> chunks = new ArrayList<>();
        StringBuilder current = new StringBuilder();
        String pendingOverlap = "";
        String[] paragraphs = content.split("\\R\\s*\\R+");

        for (String paragraph : paragraphs) {
            String trimmedParagraph = paragraph.trim();
            if (trimmedParagraph.isEmpty()) {
                continue;
            }

            if (current.length() > 0) {
                addChunk(chunks, current);
                pendingOverlap = overlapSuffix(chunks.get(chunks.size() - 1));
                current = new StringBuilder();
            }

            for (String sentence : splitSentences(trimmedParagraph)) {
                for (String piece : splitOversized(sentence)) {
                    String candidate = current.length() == 0
                            ? withOverlap(pendingOverlap, piece)
                            : current + " " + piece;
                    if (candidate.length() <= targetChunkSize) {
                        current = new StringBuilder(candidate);
                        pendingOverlap = "";
                    } else if (current.length() > 0) {
                        addChunk(chunks, current);
                        pendingOverlap = overlapSuffix(chunks.get(chunks.size() - 1));
                        candidate = withOverlap(pendingOverlap, piece);
                        current = new StringBuilder(candidate.length() <= targetChunkSize
                                ? candidate : piece);
                        pendingOverlap = "";
                    } else {
                        current.append(piece);
                    }
                }
            }
        }

        if (current.length() > 0) {
            addChunk(chunks, current);
        }
        return List.copyOf(chunks);
    }

    private List<String> splitSentences(String paragraph) {
        List<String> sentences = new ArrayList<>();
        for (String sentence : paragraph.split("(?<=[.!?])\\s+")) {
            String trimmed = sentence.trim();
            if (!trimmed.isEmpty()) {
                sentences.add(trimmed);
            }
        }
        return sentences;
    }

    private List<String> splitOversized(String text) {
        if (text.length() <= targetChunkSize) {
            return List.of(text);
        }

        List<String> pieces = new ArrayList<>();
        int pieceSize = targetChunkSize - chunkOverlap - 1;
        for (int start = 0; start < text.length(); start += pieceSize) {
            pieces.add(text.substring(start, Math.min(start + pieceSize, text.length())));
        }
        return pieces;
    }

    private String withOverlap(String overlap, String piece) {
        return overlap.isEmpty() ? piece : overlap + " " + piece;
    }

    private String overlapSuffix(String chunk) {
        int start = Math.max(0, chunk.length() - chunkOverlap);
        return chunk.substring(start);
    }

    private void addChunk(List<String> chunks, StringBuilder chunk) {
        String value = chunk.toString().stripTrailing();
        if (!value.isEmpty()) {
            chunks.add(value);
        }
    }
}