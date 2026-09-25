package com.dronzer.aisearch.service;

import java.util.List;
import java.util.Objects;
import java.util.Set;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import com.dronzer.aisearch.dto.KeywordSearchResult;
import com.dronzer.aisearch.dto.SemanticSearchResult;
import com.dronzer.aisearch.entity.User;
import com.dronzer.aisearch.exception.ResourceNotFoundException;
import com.dronzer.aisearch.repository.KeywordSearchRepository;
import com.dronzer.aisearch.repository.UserRepository;

/**
 * Orchestrates Phase 2A hybrid retrieval: it gathers candidates from the existing
 * semantic (vector) path and from PostgreSQL full-text keyword search, then delegates
 * to the {@link HybridCandidateMerger} and {@link HybridRanker}.
 *
 * <p>The hybrid behaviour is configurable and can be disabled entirely via
 * {@code app.rag.keyword-search-enabled} (default {@code true}). When disabled, the
 * service behaves exactly like the original vector-only path so that the keyword
 * search can be toggled off or rolled back without touching {@code RagService}.
 *
 * <p>Weights are validated (non-negative) and normalized here, then forwarded to the
 * ranker. They are read once at construction; configuration changes require a restart,
 * which keeps the ranking deterministic for a given running instance.
 */
@Service
public class HybridRetrievalService {

    private final DocumentService documentService;
    private final UserRepository userRepository;
    private final KeywordSearchRepository keywordSearchRepository;
    private final HybridCandidateMerger candidateMerger;
    private final HybridRanker ranker;

    private final boolean keywordSearchEnabled;
    private final double semanticWeight;
    private final double keywordWeight;

    /**
     * Production constructor used by Spring. Pulls all collaborators (including the
     * PostgreSQL keyword repository) and the hybrid configuration.
     */
    @Autowired
    public HybridRetrievalService(
            DocumentService documentService,
            UserRepository userRepository,
            KeywordSearchRepository keywordSearchRepository,
            HybridCandidateMerger candidateMerger,
            HybridRanker ranker,
            @Value("${app.rag.keyword-search-enabled:true}") boolean keywordSearchEnabled,
            @Value("${app.rag.semantic-weight:0.7}") double semanticWeight,
            @Value("${app.rag.keyword-weight:0.3}") double keywordWeight) {
        this.documentService = Objects.requireNonNull(documentService, "documentService must not be null");
        this.userRepository = userRepository;
        this.keywordSearchRepository = keywordSearchRepository;
        this.candidateMerger = Objects.requireNonNull(candidateMerger, "candidateMerger must not be null");
        this.ranker = Objects.requireNonNull(ranker, "ranker must not be null");
        this.keywordSearchEnabled = keywordSearchEnabled;
        validateWeights(semanticWeight, keywordWeight);
        this.semanticWeight = semanticWeight;
        this.keywordWeight = keywordWeight;
    }

    /**
     * Semantic-only convenience constructor intended for tests that do not exercise
     * keyword search. It produces a service that delegates straight to the existing
     * vector path, leaving every Phase 1 behaviour untouched.
     */
    HybridRetrievalService(DocumentService documentService) {
        this.documentService = documentService;
        this.userRepository = null;
        this.keywordSearchRepository = null;
        this.candidateMerger = new HybridCandidateMerger();
        this.ranker = new HybridRanker();
        this.keywordSearchEnabled = false;
        this.semanticWeight = 1.0;
        this.keywordWeight = 0.0;
    }

    private static void validateWeights(double semanticWeight, double keywordWeight) {
        if (semanticWeight < 0.0 || keywordWeight < 0.0) {
            throw new IllegalArgumentException(
                    "app.rag.semantic-weight and app.rag.keyword-weight must be non-negative");
        }
        if (semanticWeight == 0.0 && keywordWeight == 0.0) {
            throw new IllegalArgumentException(
                    "app.rag.semantic-weight and app.rag.keyword-weight must not both be zero");
        }
    }

    /**
     * Retrieves and ranks hybrid candidates for the given question.
     *
     * @param question        the user question (never blank in normal flow)
     * @param candidateLimit  maximum candidates to request from each individual source
     * @param email           the authenticated user's email; scopes every lookup to
     *                        that user's documents
     * @return merged, scored and ranked candidates (semantic-first ordering)
     */
    public List<SemanticSearchResult> retrieve(
            String question,
            int candidateLimit,
            String email) {
        return retrieve(question, candidateLimit, email, Set.of());
    }

    /**
     * Retrieves hybrid candidates with an optional document constraint. IDs must have
     * been validated for {@code email}; this method never changes tenant identity.
     */
    public List<SemanticSearchResult> retrieve(
            String question,
            int candidateLimit,
            String email,
            Set<Long> documentIds) {
        List<SemanticSearchResult> semantic;
        if (documentIds == null || documentIds.isEmpty()) {
            semantic = documentService.searchSemantically(question, candidateLimit, email);
        } else {
            semantic = documentService.searchSemantically(
                    question, candidateLimit, email, documentIds);
        }

        if (!keywordSearchEnabled || candidateLimit <= 0) {
            return semantic;
        }

        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new ResourceNotFoundException("User not found"));
        List<KeywordSearchResult> keyword;
        if (documentIds == null || documentIds.isEmpty()) {
            keyword = keywordSearchRepository.findMatches(
                    user.getId(), question, candidateLimit);
        } else {
            keyword = keywordSearchRepository.findMatches(
                    user.getId(), question, candidateLimit, documentIds);
        }

        return ranker.rank(candidateMerger.merge(semantic, keyword),
                semanticWeight, keywordWeight);
    }
}
