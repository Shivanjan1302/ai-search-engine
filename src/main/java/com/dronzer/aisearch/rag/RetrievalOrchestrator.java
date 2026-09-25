package com.dronzer.aisearch.rag;

import com.dronzer.aisearch.dto.KnowledgeSource;
import com.dronzer.aisearch.dto.SemanticSearchResult;
import com.dronzer.aisearch.dto.WebSearchResponse;
import com.dronzer.aisearch.dto.WebSearchResult;
import com.dronzer.aisearch.query.DocumentContextResolution;
import com.dronzer.aisearch.query.DocumentContextResolver;
import com.dronzer.aisearch.query.InterpretedQuery;
import com.dronzer.aisearch.service.HybridRetrievalService;
import com.dronzer.aisearch.service.WebSearchService;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * The retrieval orchestration boundary of the new RAG pipeline.
 *
 * <p>Responsibility, and nothing more:</p>
 *
 * <pre>
 * SourcePlan + InterpretedQuery + user identity
 *     → document retrieval / web retrieval via the EXISTING services
 *     → Evidence conversion via the existing adapters
 *     → deterministic deduplicated {@link RetrievalResult}
 * </pre>
 *
 * <p>This class deliberately does <strong>not</strong> perform answer generation, prompt
 * construction, context building, citation validation, provenance assembly, or source
 * planning. It never calls an AI model, never opens a database connection, never talks
 * to an HTTP provider directly, and never adds SQL.</p>
 *
 * <h2>Document retrieval</h2>
 * Delegates to {@link HybridRetrievalService#retrieve(String, int, String)}, the existing
 * tenant-aware hybrid path. The email identity is passed through <em>unchanged</em>;
 * tenant isolation remains owned by the existing layer ({@code DocumentService} →
 * {@code VectorSearchRepository}/{@code KeywordSearchRepository}, both scoped to
 * {@code user_id}). This orchestrator contains no repository, no ownership lookup, and no
 * filtering of its own. Results are converted exclusively through
 * {@link DocumentRetrievalAdapter}; no mapping logic is duplicated here.
 *
 * <h2>Web retrieval</h2>
 * Delegates to {@link WebSearchService#search(String, int)} (which routes to the existing
 * {@code WebSearchClient}/Tavily implementation). This orchestrator never constructs an
 * HTTP client, never reads API keys, and never changes request format or result
 * semantics. Results are converted exclusively through {@link WebRetrievalAdapter}.
 *
 * <h2>Query handling</h2>
 * The retrieval query is {@link InterpretedQuery#retrievalQuery()}: the normalized
 * standalone form when interpretation supplied one, otherwise the original wording.
 * The original wording remains available on the interpreted query for generation
 * and provenance.
 *
 * <h2>Optional-source execution semantics (documented decision)</h2>
 * OPTIONAL does <em>not</em> mean "always execute". The deterministic rule, derived only
 * from fields already present in {@link SourcePlan}:
 * <ol>
 *   <li>A REQUIRED retrievable source (DOCUMENT or WEB) is always executed.</li>
 *   <li>An OPTIONAL retrievable source is executed only when the plan itself justifies
 *       enrichment: {@link SourcePlan#mixedSource()} is {@code true} (the plan expects
 *       "to combine evidence from more than one source"), <em>or</em> the source is WEB
 *       and {@link SourcePlan#requiresFreshness()} is {@code true} (the plan states
 *       "web retrieval should be preferred"). Freshness alone never pulls optional
 *       document retrieval; mixedSource alone justifies both optional counterparts.</li>
 *   <li>{@link KnowledgeSource#MODEL_KNOWLEDGE} is never executed at all, at either
 *       level: it is parametric model knowledge consumed at generation time, not
 *       retrieved external evidence.</li>
 *   <li>A source appears at most once even if listed multiple times; the strongest
 *       declared level wins (REQUIRED over OPTIONAL) and first plan position is kept.</li>
 * </ol>
 * Consequences, verified against {@link DefaultSourcePlanner} output: a DOCUMENT plan
 * (mixed=false, fresh=false) never performs web search; a CURRENT plan (WEB required,
 * DOCUMENT optional, mixed=false) never performs document retrieval; a GENERAL plan
 * (all optional, no flags) performs no retrieval at all and returns an empty result.
 *
 * <h2>Failure semantics (documented decision)</h2>
 * No retry system, no fallback chains:
 * <ul>
 *   <li>OPTIONAL source fails → the failure is recorded in
 *       {@link RetrievalResult#unavailableSources()} and retrieval continues; evidence
 *       already collected from other sources is never discarded.</li>
 *   <li>REQUIRED source fails under {@link FallbackPolicy#FAIL_FAST} → a
 *       {@link RetrievalException} is thrown immediately, preserving the upstream cause
 *       (the existing exception/error convention: unchecked stage exceptions with the
 *       original cause attached).</li>
 *   <li>REQUIRED source fails under {@link FallbackPolicy#DEGRADE_GRADUALLY} → the
 *       failure is recorded in {@link RetrievalResult#unavailableSources()} and the
 *       remaining sources are still attempted, exactly as the policy documents.</li>
 *   <li>{@code null} returns from either service are defensively treated as empty
 *       results.</li>
 * </ul>
 *
 * <h2>Deterministic ordering and deduplication</h2>
 * Evidence identity is exact and type-local: {@link DocumentEvidence} equality
 * ({@code documentId::chunkIndex}) and {@link WebEvidence} equality (exact URL) — no
 * fuzzy matching, no URL normalization. Dedup keeps the first (highest-ranked)
 * occurrence. Combined ordering rule: (1) required-source evidence before
 * optional-source evidence, (2) DOCUMENT before WEB within a tier, (3) the existing
 * retrieval ranking within each source — {@code HybridRanker} remains responsible for
 * document relevance and the web provider remains responsible for web result order,
 * (4) first-occurrence identity as the final stable tie-breaker. Execution sequence
 * follows plan order; evidence ordering follows the rule chain above. No new relevance
 * ranking is introduced.
 *
 * <p>Not a Spring bean yet by design: nothing consumes this phase's output and
 * {@code RagService} stays untouched. Wiring happens in a later integration phase.
 * Stateless apart from its two injected services; constructor-injected and
 * thread-safe given thread-safe collaborators.</p>
 */
public final class RetrievalOrchestrator {

    /**
     * Document candidate limit, mirroring the existing
     * {@code app.rag.retrieval-candidate-limit} default (20) used by {@code RagService}.
     * Config-driven limits are deferred to the integration phase so live behaviour of
     * the existing pipeline is untouched.
     */
    public static final int DEFAULT_DOCUMENT_CANDIDATE_LIMIT = 20;

    /**
     * Web result limit, mirroring the existing {@code app.rag.web-result-limit}
     * default (5) used by {@code RagService}.
     */
    public static final int DEFAULT_WEB_RESULT_LIMIT = 5;

    private final HybridRetrievalService documentRetrieval;
    private final WebSearchService webRetrieval;
    private final DocumentContextResolver documentContextResolver;
    private final int documentCandidateLimit;
    private final int webResultLimit;

    public RetrievalOrchestrator(
            HybridRetrievalService documentRetrieval,
            WebSearchService webRetrieval) {
        this(documentRetrieval, webRetrieval, (query, email) ->
                DocumentContextResolution.noScope());
    }

    public RetrievalOrchestrator(
            HybridRetrievalService documentRetrieval,
            WebSearchService webRetrieval,
            DocumentContextResolver documentContextResolver) {
        this.documentRetrieval = Objects.requireNonNull(
                documentRetrieval, "documentRetrieval must not be null");
        this.webRetrieval = Objects.requireNonNull(webRetrieval, "webRetrieval must not be null");
        this.documentContextResolver = Objects.requireNonNull(
                documentContextResolver, "documentContextResolver must not be null");
        this.documentCandidateLimit = DEFAULT_DOCUMENT_CANDIDATE_LIMIT;
        this.webResultLimit = DEFAULT_WEB_RESULT_LIMIT;
    }

    /**
     * Execute the plan's retrieval decisions and collect deduplicated evidence.
     *
     * @param plan  the source plan to honour, never null
     * @param query the interpreted query; its normalized form is used when present
     * @param email the authenticated user's email, passed unchanged to the existing
     *              tenant-aware document retrieval layer
     * @return a deterministic, deduplicated evidence collection
     * @throws IllegalArgumentException if the query, retrieval query, or email is
     *                                  null/blank
     * @throws RetrievalException        if a REQUIRED source fails under
     *                                   {@link FallbackPolicy#FAIL_FAST}
     */
    public RetrievalResult retrieve(SourcePlan plan, InterpretedQuery query, String email) {
        Objects.requireNonNull(plan, "plan must not be null");
        Objects.requireNonNull(query, "query must not be null");
        if (query.originalQuery() == null || query.originalQuery().isBlank()) {
            throw new IllegalArgumentException("query.originalQuery must not be blank");
        }
        if (email == null || email.isBlank()) {
            throw new IllegalArgumentException("email must not be blank");
        }

        final String retrievalQuery = query.retrievalQuery();

        Map<KnowledgeSource, RequirementLevel> resolved = resolveExecutionSet(plan);

        List<DocumentEvidence> documents = List.of();
        List<WebEvidence> webEvidence = List.of();
        RequirementLevel documentLevel = null;
        RequirementLevel webLevel = null;
        List<KnowledgeSource> executedSources = new ArrayList<>(resolved.size());
        List<KnowledgeSource> unavailableSources = new ArrayList<>();

        for (Map.Entry<KnowledgeSource, RequirementLevel> entry : resolved.entrySet()) {
            KnowledgeSource source = entry.getKey();
            RequirementLevel level = entry.getValue();

            if (source == KnowledgeSource.MODEL_KNOWLEDGE) {
                continue; // never retrieved; generation-time concern only.
            }
            if (!shouldExecute(source, level, plan)) {
                continue; // optional source the plan did not justify.
            }

            executedSources.add(source);
            try {
                if (source == KnowledgeSource.DOCUMENT) {
                    DocumentContextResolution context = documentContextResolver.resolve(query, email);
                    if (context.status() == DocumentContextResolution.Status.UNAVAILABLE) {
                        documentLevel = level;
                        unavailableSources.add(source);
                        continue;
                    }
                    documents = deduplicate(DocumentRetrievalAdapter.fromResults(
                            retrieveDocuments(retrievalQuery, email, context.documentIds())));
                    documentLevel = level;
                } else {
                    webEvidence = deduplicate(WebRetrievalAdapter.fromResults(
                            retrieveWebResults(retrievalQuery)));
                    webLevel = level;
                }
            } catch (RuntimeException failure) {
                if (level == RequirementLevel.REQUIRED
                        && plan.fallbackPolicy() != FallbackPolicy.DEGRADE_GRADUALLY) {
                    throw new RetrievalException(
                            "Required knowledge source " + source + " could not be consulted",
                            failure);
                }
                unavailableSources.add(source);
            }
        }

        return new RetrievalResult(
                documents,
                webEvidence,
                combineInDeterministicOrder(documents, documentLevel, webEvidence, webLevel),
                executedSources,
                unavailableSources);
    }

    // ------------------------------------------------------------------
    // Plan interpretation (pure, deterministic).
    // ------------------------------------------------------------------

    /**
     * Resolve the plan's source list into at most one requirement per source, in plan
     * order of first appearance, with the strongest declared level winning if a source
     * is listed more than once.
     */
    private static Map<KnowledgeSource, RequirementLevel> resolveExecutionSet(SourcePlan plan) {
        Map<KnowledgeSource, RequirementLevel> resolved = new LinkedHashMap<>();
        for (SourceRequirement requirement : plan.sources()) {
            resolved.merge(requirement.source(), requirement.level(), RetrievalOrchestrator::strongest);
        }
        return resolved;
    }

    private static RequirementLevel strongest(RequirementLevel first, RequirementLevel second) {
        return (first == RequirementLevel.REQUIRED || second == RequirementLevel.REQUIRED)
                ? RequirementLevel.REQUIRED
                : RequirementLevel.OPTIONAL;
    }

    /**
     * The documented optional-execution rule. See the class javadoc
     * ("Optional-source execution semantics").
     */
    private static boolean shouldExecute(KnowledgeSource source, RequirementLevel level, SourcePlan plan) {
        if (level == RequirementLevel.REQUIRED) {
            return true;
        }
        if (plan.mixedSource()) {
            return true;
        }
        return source == KnowledgeSource.WEB && plan.requiresFreshness();
    }

    // ------------------------------------------------------------------
    // Retrieval delegates (existing services only; no direct I/O here).
    // ------------------------------------------------------------------

    private List<SemanticSearchResult> retrieveDocuments(
            String retrievalQuery, String email, java.util.Set<Long> documentIds) {
        List<SemanticSearchResult> results = documentIds.isEmpty()
                ? documentRetrieval.retrieve(retrievalQuery, documentCandidateLimit, email)
                : documentRetrieval.retrieve(
                        retrievalQuery, documentCandidateLimit, email, documentIds);
        return results == null ? List.of() : results;
    }

    private List<WebSearchResult> retrieveWebResults(String retrievalQuery) {
        WebSearchResponse response = webRetrieval.search(retrievalQuery, webResultLimit);
        if (response == null || response.results() == null) {
            return List.of();
        }
        return response.results();
    }

    // ------------------------------------------------------------------
    // Deterministic deduplication and ordering.
    // ------------------------------------------------------------------

    /**
     * Exact-identity deduplication via the evidence type's own equality (LinkedHashSet
     * keeps the first occurrence, which is the higher-ranked one, and preserves
     * insertion order). No fuzzy matching, no URL normalization.
     */
    private static <T extends Evidence> List<T> deduplicate(List<T> evidence) {
        return new ArrayList<>(new LinkedHashSet<>(evidence));
    }

    /**
     * Combined ordering: required tier before optional tier; DOCUMENT before WEB within
     * a tier; existing retrieval/provider order within each source (the lists arrive
     * already in that order). A source that was never resolved/attempted contributes
     * nothing because its level is neither REQUIRED nor OPTIONAL.
     */
    private static List<Evidence> combineInDeterministicOrder(
            List<DocumentEvidence> documents,
            RequirementLevel documentLevel,
            List<WebEvidence> webEvidence,
            RequirementLevel webLevel) {
        List<Evidence> combined = new ArrayList<>(documents.size() + webEvidence.size());
        appendTier(combined, documents, documentLevel == RequirementLevel.REQUIRED);
        appendTier(combined, webEvidence, webLevel == RequirementLevel.REQUIRED);
        appendTier(combined, documents, documentLevel == RequirementLevel.OPTIONAL);
        appendTier(combined, webEvidence, webLevel == RequirementLevel.OPTIONAL);
        return List.copyOf(combined);
    }

    private static void appendTier(List<Evidence> target, List<? extends Evidence> source, boolean inTier) {
        if (inTier) {
            target.addAll(source);
        }
    }


}


