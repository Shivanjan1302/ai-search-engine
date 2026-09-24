package com.dronzer.aisearch.rag;

import com.dronzer.aisearch.dto.KnowledgeSource;
import com.dronzer.aisearch.rag.ContextBuilder.ConstructionMetadata;
import com.dronzer.aisearch.rag.ContextBuilder.ConstructionResult;
import com.dronzer.aisearch.rag.ContextBuilder.ContextBuilderConfig;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Set;

import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Production {@link ContextBuilder} implementation: transforms already
 * retrieved/reranked {@link Evidence} into a deterministic, bounded,
 * provenance-preserving list of {@link ContextPiece}s for downstream grounded
 * generation.
 *
 * <p>This class implements the CONTEXT CONSTRUCTION stage only. It runs after
 * retrieval ({@link RetrievalOrchestrator}) and optional reranking
 * ({@link Reranker}/{@link LexicalReranker}). It never retrieves, never
 * reranks, never generates, and never validates citations. Specifically it
 * never touches {@code HybridRetrievalService}, {@code WebSearchService}, any
 * repository, any HTTP client, any AI model, or any external API. It is a
 * pure, stateless function over its arguments: same input, same output,
 * always.</p>
 *
 * <h2>Input authority and rerank handling</h2>
 * <ul>
 *   <li>The {@code evidence} parameter is authoritative for <em>membership</em>:
 *       only candidates passed in can appear in the output.</li>
 *   <li>When {@code rerankResult} is present and non-empty, its
 *       {@link RerankResult#ranked()} order is the upstream ordering authority
 *       and is preserved verbatim for the candidates it covers. Candidates not
 *       listed by the reranker retain their input order and are appended after
 *       the reranked ones. Ranked entries that are not among the candidates are
 *       ignored (reranker output is an ordering refinement, not a second
 *       membership channel).</li>
 *   <li>When {@code rerankResult} is {@code null} or empty, the input order is
 *       preserved verbatim.</li>
 *   <li>No score is ever read, compared, recomputed, or sorted on:
 *       {@link Evidence#score()} is explicitly not comparable across providers,
 *       and this stage must not become another ranking engine.</li>
 * </ul>
 *
 * <h2>Ordering determinism and source-plan semantics</h2>
 * Output follows the effective retrieval/rerank order by default. Within each
 * source, upstream strength is never recalculated. When
 * {@link ContextBuilderConfig#diversifyAcrossSources()} is enabled, sources are
 * interleaved deterministically in round-robin order using the source plan's
 * order, followed by unplanned sources in first-seen order. No evidence source
 * is scored or compared across provider boundaries.
 *
 * <h2>Neighboring chunks</h2>
 * When {@link ContextBuilderConfig#includeNeighboringChunks()} is enabled,
 * already-retrieved chunks immediately before and after an included document
 * chunk may be attached through {@link ContextPiece#preceding()} and
 * {@link ContextPiece#following()}. Neighbors are never fetched, generated, or
 * substituted; each remains a provenance-bearing {@link Evidence} instance.
 * Attached content is charged to the token budget but does not consume the
 * top-level piece cap. Exact identity deduplication still applies.
 *
 * <h2>Source policy boundary</h2>
 * A source plan controls construction precedence, but it is not proof that a
 * required source was available or that generation should proceed. This stage
 * does not refetch missing sources, invent evidence, or decide sufficiency;
 * {@link EvidencePolicy} owns that downstream decision. Tenant isolation
 * remains owned by the existing authenticated document-retrieval boundary and
 * is neither broadened nor reimplemented here.
 *
 * <h2>Deduplication</h2>
 * Exact-identity deduplication, first occurrence wins:
 * <ul>
 *   <li>Document identity: {@link DocumentEvidence#id()} =
 *       {@code documentId + "::" + chunkIndex}.</li>
 *   <li>Web identity: {@link WebEvidence#id()} — the exact URL, never
 *       normalized (no trailing-slash stripping, no case folding, no query
 *       sorting), because no existing contract requires it.</li>
 *   <li>Identity is scoped by {@link KnowledgeSource}, so a document id can
 *       never collide with a URL.</li>
 * </ul>
 * There is no fuzzy, content-based, or cross-source deduplication in this
 * phase. Later occurrences are not silently discarded: they are reported in
 * {@link ConstructionResult#droppedEvidence()} and counted in
 * {@link ConstructionMetadata#droppedReasonSummary()}.
 *
 * <h2>Bounded context size (documented limitation)</h2>
 * Unbounded context is impossible by construction:
 * <ol>
 *   <li><strong>Piece cap</strong> — at most
 *       {@link ContextBuilderConfig#maxEvidencePieces()} pieces are included.
 *       When {@code null}, the default {@code 6} is used, mirroring the
 *       existing {@code app.rag.final-context-limit:6} property consumed by
 *       {@code RagService}.</li>
 *   <li><strong>Token budget</strong> — the running estimated token count never
 *       exceeds {@link ContextBuilderConfig#targetTokenBudget()}. When
 *       {@code null}, a documented default is used (see
 *       {@code DEFAULT_TARGET_TOKEN_BUDGET}). The existing configuration

 *       infrastructure exposes no token-budget property, so this documented
 *       constant stands in until the integration phase wires real
 *       configuration into {@link ContextBuilderConfig} — the same
 *       "config-driven limits are deferred to the integration phase" decision
 *       already made by {@link RetrievalOrchestrator}.</li>
 * </ol>
 * Token estimation is a deterministic character heuristic:
 * {@code ceil(content.length() / CHARS_PER_TOKEN)} — no tokenizer, no model,
 * no I/O. Admission is whole-pieces-only and content is never truncated,
 * rewritten, or summarized. Drops are reported explicitly.
 */
public final class DefaultContextBuilder implements ContextBuilder {

    static final int DEFAULT_MAX_EVIDENCE_PIECES = 6;
    static final int DEFAULT_TARGET_TOKEN_BUDGET = 8192;
    static final int CHARS_PER_TOKEN = 4;

    private static final String DUPLICATE = "duplicate";
    private static final String BLANK_CONTENT = "blankContent";
    private static final String PIECE_LIMIT = "pieceLimit";
    private static final String TOKEN_BUDGET = "tokenBudget";

    @Override
    public ConstructionResult build(
            String query,
            Iterable<Evidence> evidence,
            Optional<RerankResult> rerankResult,
            ContextBuilderConfig config) {
        try {
            if (query == null || query.isBlank()) {
                throw failure("query must not be null or blank", null);
            }
            if (evidence == null) {
                throw failure("evidence must not be null", null);
            }
            if (config != null && config.sourcePlan() == null) {
                throw failure("config.sourcePlan must not be null", null);
            }

            Settings settings = resolveSettings(config);
            List<Evidence> candidates = copyAndValidate(evidence);
            List<Evidence> ordered = effectiveOrder(candidates, rerankResult);
            Deduplication deduplication = deduplicate(ordered);
            List<Evidence> constructionOrder = config != null && config.diversifyAcrossSources()
                    ? diversify(deduplication.unique(), settings.sourceOrder())
                    : deduplication.unique();
            return assemble(deduplication, constructionOrder, settings);
        } catch (ContextConstructionException failure) {
            throw failure;
        } catch (RuntimeException failure) {
            throw failure("unexpected context construction failure", failure);
        }
    }

    private static Settings resolveSettings(ContextBuilderConfig config) {
        int maxPieces = config == null || config.maxEvidencePieces() == null
                ? DEFAULT_MAX_EVIDENCE_PIECES : config.maxEvidencePieces();
        int tokenBudget = config == null || config.targetTokenBudget() == null
                ? DEFAULT_TARGET_TOKEN_BUDGET : config.targetTokenBudget();
        if (maxPieces <= 0) {
            throw failure("maxEvidencePieces must be positive", null);
        }
        if (tokenBudget <= 0) {
            throw failure("targetTokenBudget must be positive", null);
        }
        return new Settings(
                maxPieces,
                tokenBudget,
                config != null && config.includeNeighboringChunks(),
                config == null ? List.of() : sourceOrder(config.sourcePlan()),
                config == null ? Optional.empty() : config.sourcePlan());
    }

    private static List<KnowledgeSource> sourceOrder(Optional<SourcePlan> sourcePlan) {
        if (sourcePlan == null || sourcePlan.isEmpty()) {
            return List.of();
        }
        SourcePlan plan = sourcePlan.orElseThrow();
        if (plan.evidenceRequirement() == null || plan.fallbackPolicy() == null) {
            throw failure("source plan policy fields must not be null", null);
        }
        Set<KnowledgeSource> seen = new LinkedHashSet<>();
        for (int index = 0; index < plan.sources().size(); index++) {
            SourceRequirement requirement = plan.sources().get(index);
            if (requirement == null) {
                throw failure("source plan contains a null requirement at index " + index, null);
            }
            if (requirement.source() == null || requirement.level() == null) {
                throw failure("source plan has an invalid requirement at index " + index, null);
            }
            if (requirement.source() != KnowledgeSource.MODEL_KNOWLEDGE) {
                seen.add(requirement.source());
            }
        }
        return List.copyOf(seen);
    }

    private static List<Evidence> copyAndValidate(Iterable<Evidence> evidence) {
        List<Evidence> result = new ArrayList<>();
        int index = 0;
        for (Evidence item : evidence) {
            if (item == null) {
                throw failure("evidence element at index " + index + " must not be null", null);
            }
            validate(item, "evidence at index " + index);
            result.add(item);
            index++;
        }
        return result;
    }

    private static void validate(Evidence item, String location) {
        if (item.source() == null || item.id() == null || item.id().isBlank() || item.content() == null) {
            throw failure(location + " has a null or blank required identity/content field", null);
        }
        if (item.source() == KnowledgeSource.MODEL_KNOWLEDGE) {
            throw failure("model knowledge cannot be represented as retrieved evidence", null);
        }
        if (item.source() == KnowledgeSource.DOCUMENT) {
            if (!(item instanceof DocumentEvidence document)
                    || document.documentId() == null || document.chunkIndex() == null) {
                throw failure(location + " has incomplete document identity", null);
            }
        } else if (item.source() == KnowledgeSource.WEB) {
            if (!(item instanceof WebEvidence web) || web.url() == null || web.url().isBlank()) {
                throw failure(location + " has an invalid web URL", null);
            }
        } else {
            throw failure(location + " has an unsupported evidence source", null);
        }
    }

    private static List<Evidence> effectiveOrder(
            List<Evidence> candidates, Optional<RerankResult> optionalResult) {
        if (optionalResult == null || optionalResult.isEmpty()) {
            return candidates;
        }
        RerankResult result = optionalResult.get();
        if (result.ranked() == null) {
            throw failure("rerankResult.ranked must not be null", null);
        }
        Map<Identity, List<Evidence>> available = new LinkedHashMap<>();
        for (Evidence candidate : candidates) {
            available.computeIfAbsent(identity(candidate), ignored -> new ArrayList<>()).add(candidate);
        }
        List<Evidence> ordered = new ArrayList<>();
        Set<Identity> emitted = new LinkedHashSet<>();
        for (int index = 0; index < result.ranked().size(); index++) {
            Evidence ranked = result.ranked().get(index);
            if (ranked == null) {
                throw failure("rerankResult.ranked element at index " + index + " must not be null", null);
            }
            validate(ranked, "rerankResult.ranked element at index " + index);
            Identity id = identity(ranked);
            List<Evidence> matchingCandidates = available.get(id);
            if (matchingCandidates != null && !emitted.contains(id)) {
                ordered.addAll(matchingCandidates);
                emitted.add(id);
            }
        }
        for (Map.Entry<Identity, List<Evidence>> entry : available.entrySet()) {
            if (!emitted.contains(entry.getKey())) {
                ordered.addAll(entry.getValue());
                emitted.add(entry.getKey());
            }
        }
        return ordered;
    }

    /**
     * Stable round-robin across the configured source order, followed by any
     * unplanned source in first-seen order. Relative order within each source
     * remains exactly the effective retrieval/rerank order.
     */
    private static List<Evidence> diversify(
            List<Evidence> ordered, List<KnowledgeSource> configuredSources) {
        Map<KnowledgeSource, List<Evidence>> bySource = new LinkedHashMap<>();
        for (Evidence item : ordered) {
            bySource.computeIfAbsent(item.source(), ignored -> new ArrayList<>()).add(item);
        }
        List<KnowledgeSource> rotation = new ArrayList<>();
        for (KnowledgeSource source : configuredSources) {
            if (bySource.containsKey(source) && !rotation.contains(source)) {
                rotation.add(source);
            }
        }
        for (KnowledgeSource source : bySource.keySet()) {
            if (!rotation.contains(source)) {
                rotation.add(source);
            }
        }

        List<Evidence> result = new ArrayList<>(ordered.size());
        Map<KnowledgeSource, Integer> cursors = new LinkedHashMap<>();
        while (result.size() < ordered.size()) {
            boolean added = false;
            for (KnowledgeSource source : rotation) {
                List<Evidence> sourceItems = bySource.get(source);
                int cursor = cursors.getOrDefault(source, 0);
                if (cursor < sourceItems.size()) {
                    result.add(sourceItems.get(cursor));
                    cursors.put(source, cursor + 1);
                    added = true;
                }
            }
            if (!added) {
                throw failure("unable to diversify evidence source queues", null);
            }
        }
        return result;
    }



    private static Deduplication deduplicate(List<Evidence> ordered) {
        List<Evidence> unique = new ArrayList<>();
        List<Evidence> duplicates = new ArrayList<>();
        Map<Identity, Evidence> seen = new LinkedHashMap<>();
        for (Evidence item : ordered) {
            if (seen.putIfAbsent(identity(item), item) != null) {
                duplicates.add(item);
            } else {
                unique.add(item);
            }
        }
        return new Deduplication(unique, duplicates);
    }

    private static ConstructionResult assemble(
            Deduplication deduplication, List<Evidence> constructionOrder, Settings settings) {
        List<ContextPiece> pieces = new ArrayList<>();
        List<Evidence> dropped = new ArrayList<>(deduplication.duplicates());
        Map<String, Integer> reasons = new LinkedHashMap<>();
        if (!deduplication.duplicates().isEmpty()) {
            reasons.put(DUPLICATE, deduplication.duplicates().size());
        }

        Map<DocumentCoordinate, Evidence> documentChunks = new LinkedHashMap<>();
        if (settings.includeNeighboringChunks()) {
            for (Evidence item : deduplication.unique()) {
                if (item instanceof DocumentEvidence document) {
                    documentChunks.putIfAbsent(
                            new DocumentCoordinate(document.documentId(), document.chunkIndex()), item);
                }
            }
        }

        Set<Identity> attachedOrIncluded = new LinkedHashSet<>();
        int estimatedTokens = 0;
        for (Evidence item : constructionOrder) {
            Identity identity = identity(item);
            if (attachedOrIncluded.contains(identity)) {
                continue; // already used as an earlier anchor's neighbor
            }
            if (item.content().isBlank()) {
                dropped.add(item);
                increment(reasons, BLANK_CONTENT);
                continue;
            }
            if (pieces.size() >= settings.maxPieces()) {
                dropped.add(item);
                increment(reasons, PIECE_LIMIT);
                continue;
            }

            int primaryTokens = estimateTokens(item.content());
            if (primaryTokens > settings.tokenBudget() - estimatedTokens) {
                dropped.add(item);
                increment(reasons, TOKEN_BUDGET);
                continue;
            }

            NeighborSelection neighbors = settings.includeNeighboringChunks()
                    ? neighboringChunks(item, documentChunks, attachedOrIncluded,
                            settings.tokenBudget() - estimatedTokens - primaryTokens)
                    : NeighborSelection.none();

            pieces.add(new ContextPiece(
                    item,
                    Optional.ofNullable(neighbors.preceding()).map(DefaultContextBuilder::plainPiece),
                    Optional.ofNullable(neighbors.following()).map(DefaultContextBuilder::plainPiece),
                    role(item.source())));
            attachedOrIncluded.add(identity);
            if (neighbors.preceding() != null) {
                attachedOrIncluded.add(identity(neighbors.preceding()));
            }
            if (neighbors.following() != null) {
                attachedOrIncluded.add(identity(neighbors.following()));
            }
            estimatedTokens += primaryTokens + neighbors.tokenCount();
        }

        return new ConstructionResult(
                List.copyOf(pieces),
                List.copyOf(dropped),
                new ConstructionMetadata(
                        pieces.size(), dropped.size(), estimatedTokens,
                        settings.sourcePlan(), summarize(reasons)));
    }

    private static NeighborSelection neighboringChunks(
            Evidence primary,
            Map<DocumentCoordinate, Evidence> chunks,
            Set<Identity> used,
            int remainingTokens) {
        if (!(primary instanceof DocumentEvidence document) || remainingTokens <= 0) {
            return NeighborSelection.none();
        }
        Evidence preceding = chunks.get(new DocumentCoordinate(
                document.documentId(), document.chunkIndex() - 1L));
        Evidence following = chunks.get(new DocumentCoordinate(
                document.documentId(), document.chunkIndex() + 1L));
        int precedingTokens = preceding == null || used.contains(identity(preceding))
                || preceding.content().isBlank() ? 0 : estimateTokens(preceding.content());
        int followingTokens = following == null || used.contains(identity(following))
                || following.content().isBlank() ? 0 : estimateTokens(following.content());
        boolean includePreceding = precedingTokens > 0 && precedingTokens <= remainingTokens;
        boolean includeFollowing = followingTokens > 0
                && precedingTokens + followingTokens <= remainingTokens;
        return new NeighborSelection(
                includePreceding ? preceding : null,
                includeFollowing ? following : null,
                (includePreceding ? precedingTokens : 0) + (includeFollowing ? followingTokens : 0));
    }

    private static ContextPiece plainPiece(Evidence evidence) {
        return new ContextPiece(evidence, Optional.empty(), Optional.empty(), role(evidence.source()));
    }

    private static int estimateTokens(String content) {
        int length = content.length();
        return length / CHARS_PER_TOKEN + (length % CHARS_PER_TOKEN == 0 ? 0 : 1);
    }

    private static String role(KnowledgeSource source) {
        return source == KnowledgeSource.DOCUMENT ? "document" : "web";
    }

    private record Deduplication(List<Evidence> unique, List<Evidence> duplicates) {
    }

    private record Settings(
            int maxPieces,
            int tokenBudget,
            boolean includeNeighboringChunks,
            List<KnowledgeSource> sourceOrder,
            Optional<SourcePlan> sourcePlan) {
        private Settings {
            sourceOrder = List.copyOf(sourceOrder);
            sourcePlan = sourcePlan == null ? Optional.empty() : sourcePlan;
        }
    }

    private record DocumentCoordinate(Long documentId, long chunkIndex) {
    }

    private record NeighborSelection(
            Evidence preceding, Evidence following, int tokenCount) {
        private static NeighborSelection none() {
            return new NeighborSelection(null, null, 0);
        }
    }

    private static void increment(Map<String, Integer> reasons, String reason) {
        reasons.merge(reason, 1, Integer::sum);
    }

    private static Optional<String> summarize(Map<String, Integer> reasons) {
        if (reasons.isEmpty()) {
            return Optional.empty();
        }
        return Optional.of(reasons.entrySet().stream()
                .map(entry -> entry.getKey() + "=" + entry.getValue())
                .reduce((left, right) -> left + ", " + right)
                .orElseThrow());
    }

    private static Identity identity(Evidence item) {
        return new Identity(item.source(), item.id());
    }

    private record Identity(KnowledgeSource source, String id) {
    }

    private static ContextConstructionException failure(String message, Throwable cause) {
        return cause == null
                ? new ContextConstructionException(message)
                : new ContextConstructionException(message, cause);
    }
}


