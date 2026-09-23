package com.dronzer.aisearch.rag;

import com.dronzer.aisearch.dto.KnowledgeSource;
import java.util.List;
import java.util.Objects;

/**
 * Deterministic output of {@link RetrievalOrchestrator}: evidence collected for one
 * {@link SourcePlan} + query, with provenance fully preserved.
 *
 * <p>This is a retrieval-only structure. It contains no answer text, no prompt, and no
 * assembled provenance narrative; those belong to later pipeline stages.</p>
 *
 * <h2>Field guarantees</h2>
 * <ul>
 *   <li>{@link #documents()} — document evidence in the existing retrieval ranking order
 *       (preserved from {@link com.dronzer.aisearch.service.HybridRetrievalService}),
 *       deduplicated by {@link DocumentEvidence} identity ({@code documentId::chunkIndex}),
 *       first occurrence wins. Every provenance field is preserved: document ID, filename,
 *       chunk index, content, similarity, keyword score, hybrid score, retrieval score,
 *       and retrieval method.</li>
 *   <li>{@link #webEvidence()} — web evidence in provider order, deduplicated by
 *       {@link WebEvidence} identity (exact URL), first occurrence wins. URL, title,
 *       publisher, snippet/content, and retrieval method are preserved.</li>
 *   <li>{@link #evidence()} — the combined view in the documented deterministic order:
 *       required-source evidence before optional-source evidence; within a tier,
 *       DOCUMENT before WEB; within a source, the existing retrieval/provider order.</li>
 *   <li>{@link #executedSources()} — sources the orchestrator actually attempted, in
 *       plan order. A source appears at most once.</li>
 *   <li>{@link #unavailableSources()} — a subset of {@link #executedSources()} whose
 *       retrieval attempt failed and was tolerated (optional sources, or required
 *       sources under {@link FallbackPolicy#DEGRADE_GRADUALLY}).</li>
 * </ul>
 *
 * <p>All lists are immutable copies. Iteration order is fully deterministic: no
 * {@code HashSet}/{@code HashMap} iteration order is ever observable here.</p>
 */
public record RetrievalResult(

        /** Deduplicated document evidence, in retrieval ranking order. */
        List<DocumentEvidence> documents,

        /** Deduplicated web evidence, in provider order. */
        List<WebEvidence> webEvidence,

        /** Combined evidence in the documented deterministic order. */
        List<Evidence> evidence,

        /** Sources attempted, in plan order. */
        List<KnowledgeSource> executedSources,

        /** Attempted sources whose retrieval failed and was tolerated, in plan order. */
        List<KnowledgeSource> unavailableSources

) {

    public RetrievalResult {
        documents = List.copyOf(Objects.requireNonNull(documents, "documents must not be null"));
        webEvidence = List.copyOf(Objects.requireNonNull(webEvidence, "webEvidence must not be null"));
        evidence = List.copyOf(Objects.requireNonNull(evidence, "evidence must not be null"));
        executedSources = List.copyOf(Objects.requireNonNull(executedSources, "executedSources must not be null"));
        unavailableSources = List.copyOf(Objects.requireNonNull(unavailableSources, "unavailableSources must not be null"));
    }

    /**
     * Whether any evidence at all was retrieved, from any source.
     */
    public boolean hasEvidence() {
        return !evidence.isEmpty();
    }
}
