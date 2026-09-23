package com.dronzer.aisearch.rag;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * Production {@link Reranker} implementation: deterministic lexical reranking of
 * already-retrieved evidence candidates.
 *
 * <p>This class implements the RERANKING stage only. It runs after evidence has been
 * collected, deduplicated, and initially ranked by the existing retrieval layer
 * ({@link com.dronzer.aisearch.service.HybridRanker} for documents, the web provider's
 * own order for web results, combined deterministically by
 * {@link RetrievalOrchestrator}). It never retrieves, never deduplicates, and never
 * replaces {@code HybridRanker}: the incoming candidate order <em>is</em> the initial
 * ranking and survives untouched wherever this reranker has no signal to reorder.</p>
 *
 * <h2>Relevance signal</h2>
 * The single reranking signal is <strong>query-term coverage</strong> over
 * {@link Evidence#content()}:
 *
 * <pre>
 * coverage = (distinct query terms present in evidence content) / (distinct query terms)
 * </pre>
 *
 * <p>Both inputs come directly from the Phase 2B contracts: the original user query
 * passed to {@link #rerank(String, List, RerankerConfig)} and the evidence text exposed
 * by {@link Evidence#content()}. Tokenization is case-insensitive ({@link Locale#ROOT})
 * over letter/digit runs, with a small built-in English stopword list removed from the
 * <em>query</em> so that ubiquitous function words ("what", "the", "is") cannot drown out
 * content words; if the query consists only of stopwords, the raw tokens are used
 * instead, and a query with no tokens at all yields zero coverage for every candidate
 * (a deterministic order-preserving no-op). No other relevance signal is used, invented,
 * or implied.</p>
 *
 * <h2>Why {@link Evidence#score()} is not a sort key</h2>
 * The {@link Evidence} contract states explicitly that scores from different providers
 * are <strong>not</strong> comparable. Mixing document hybrid scores with web provider
 * scores in one comparator would violate that contract. Within a single source, higher
 * input position already reflects that source's ranking (the initial ranking stage), so
 * the input order is used directly as the tie-breaker instead of re-deriving it from
 * incomparable numbers.
 *
 * <h2>Determinism</h2>
 * The sort is a total order: descending coverage, then ascending original input index.
 * Ties therefore always keep the initial retrieval ranking, repeated calls on equal
 * input always produce identical output, and the result reports
 * {@code deterministic = true} metadata.
 *
 * <h2>Preservation guarantees</h2>
 * <ul>
 *   <li>Identity: the returned list contains the exact same {@link Evidence} instances
 *       that were passed in — nothing is copied, wrapped, or reconstructed.</li>
 *   <li>Provenance: document/web provenance fields are untouched because the instances
 *       themselves are untouched ({@link Evidence} implementations are immutable
 *       records).</li>
 *   <li>No silent discarding: every candidate appears in the output exactly once — the
 *       {@link Reranker} contract does not require dropping, and deduplication already
 *       happened upstream in {@link RetrievalOrchestrator}.</li>
 *   <li>No mutation: the input list is never modified; sorting happens on an internal
 *       copy.</li>
 * </ul>
 *
 * <h2>Boundaries</h2>
 * No database access, no HTTP requests, no Gemini/Tavily calls, no repository or
 * ownership lookup (tenant isolation stays owned by the retrieval layer), and no
 * retrieval logic. The class is stateless and thread-safe.
 *
 * <h2>Configuration</h2>
 * {@code config} is accepted because the contract allows it, but this implementation has
 * no tunable parameters and {@link Reranker.RerankerConfig#configJson()} has no defined
 * schema, so configuration does not alter behaviour. Metadata always reports this
 * reranker's own name, never a caller-supplied label.
 *
 * <p>Not a Spring bean yet by design, mirroring {@link RetrievalOrchestrator} and
 * {@link DefaultSourcePlanner}: nothing consumes this phase's output and
 * {@code RagService} stays untouched. Wiring happens in a later integration phase.</p>
 */
public final class LexicalReranker implements Reranker {

    /** Stable identity of this reranker, reported in {@link RerankingMetadata}. */
    public static final String RERANKER_NAME = "lexical-query-coverage";

    private static final String RERANKER_DESCRIPTION =
            "Deterministic lexical reranking: query-term coverage over evidence content; "
                    + "ties keep the initial retrieval order.";

    /** Splits text into letter/digit token runs. */
    private static final Pattern TOKEN_SEPARATOR = Pattern.compile("[^\\p{L}\\p{N}]+");
    /**
     * Small built-in English stopword list. Applied to the query only, so that function
     * words cannot dominate coverage; membership tests are order-independent, keeping
     * the signal deterministic.
     */
    private static final Set<String> STOP_WORDS = Set.of(
            "a", "an", "the", "and", "or", "but", "if", "then", "else", "when", "while",
            "until", "as", "at", "by", "for", "with", "about", "against", "between",
            "into", "through", "during", "before", "after", "above", "below", "to",
            "from", "up", "down", "in", "out", "on", "off", "over", "under", "again",
            "further", "once", "here", "there", "all", "any", "both", "each", "few",
            "more", "most", "other", "some", "such", "no", "nor", "not", "only", "own",
            "same", "so", "than", "too", "very", "can", "will", "just", "should", "now",
            "is", "are", "was", "were", "be", "been", "being", "am", "have", "has",
            "had", "having", "do", "does", "did", "doing", "would", "could", "ought",
            "i", "me", "my", "myself", "we", "our", "ours", "ourselves", "you", "your",
            "yours", "yourself", "yourselves", "he", "him", "his", "himself", "she",
            "her", "hers", "herself", "it", "its", "itself", "they", "them", "their",
            "theirs", "themselves", "what", "which", "who", "whom", "whose", "this",
            "that", "these", "those", "of", "how", "where", "why",
            // fragments produced by splitting contractions (don't -> don + t)
            "s", "t", "m", "re", "ve", "ll", "d");

    /** A candidate plus the two keys of the total reranking order. */
    private record ScoredCandidate(int originalIndex, double coverage, Evidence evidence) {
    }

    /**
     * Total order: coverage descending, then original input index ascending so ties keep
     * the initial retrieval ranking. Fully deterministic for any input.
     */
    private static final Comparator<ScoredCandidate> RERANK_ORDER =
            Comparator.comparingDouble(ScoredCandidate::coverage)
                    .reversed()
                    .thenComparingInt(ScoredCandidate::originalIndex);

    /**
     * Rerank already-retrieved evidence candidates for the given query.
     *
     * @param query      the original user query, never null and never blank
     * @param candidates the evidence candidates in initial ranking order, never null,
     *                   may be empty
     * @param config     optional reranking configuration; accepted per contract but has
     *                   no effect on this implementation (see class documentation)
     * @return all candidates reordered by descending query-term coverage, ties keeping
     *         the initial order, with deterministic metadata
     * @throws RerankingException if the query is null/blank, the candidate list is null,
     *                            or the list contains a null element
     */
    @Override
    public RerankResult rerank(String query, List<Evidence> candidates, RerankerConfig config) {
        validate(query, candidates);

        Set<String> queryTerms = queryTerms(query);

        // Score on an internal copy: the caller's list is never mutated.
        List<ScoredCandidate> scored = new ArrayList<>(candidates.size());
        for (int index = 0; index < candidates.size(); index++) {
            Evidence candidate = candidates.get(index);
            scored.add(new ScoredCandidate(index, coverage(queryTerms, candidate), candidate));
        }
        scored.sort(RERANK_ORDER);

        List<Evidence> ranked = scored.stream().map(ScoredCandidate::evidence).toList();
        return new RerankResult(ranked, metadata());
    }

    private static void validate(String query, List<Evidence> candidates) {
        if (query == null) {
            throw new RerankingException("query must not be null");
        }
        if (query.isBlank()) {
            throw new RerankingException("query must not be blank");
        }
        if (candidates == null) {
            throw new RerankingException("candidates must not be null");
        }
        for (Evidence candidate : candidates) {
            if (candidate == null) {
                throw new RerankingException("candidates must not contain null elements");
            }
        }
    }

    /**
     * Distinct query terms after stopword removal. If every token is a stopword, the raw
     * tokens are the fallback signal; a token-less query yields an empty term set and
     * therefore zero coverage for all candidates.
     */
    private static Set<String> queryTerms(String query) {
        List<String> tokens = tokenize(query);
        Set<String> terms = new LinkedHashSet<>();
        for (String token : tokens) {
            if (!STOP_WORDS.contains(token)) {
                terms.add(token);
            }
        }
        if (terms.isEmpty()) {
            terms.addAll(tokens);
        }
        return terms;
    }

    /** Fraction of distinct query terms present in the candidate's content. */
    private static double coverage(Set<String> queryTerms, Evidence candidate) {
        if (queryTerms.isEmpty()) {
            return 0.0;
        }
        // Defensive: Evidence.content() is contractually never null, but a malformed
        // implementation must degrade to "no signal", not fail mid-sort.
        Set<String> contentTerms = new HashSet<>(tokenize(candidate.content()));
        int matched = 0;
        for (String term : queryTerms) {
            if (contentTerms.contains(term)) {
                matched++;
            }
        }
        return (double) matched / queryTerms.size();
    }

    /** Case-insensitive letter/digit tokenization; null/blank text yields no tokens. */
    private static List<String> tokenize(String text) {
        if (text == null || text.isEmpty()) {
            return List.of();
        }
        List<String> tokens = new ArrayList<>();
        for (String piece : TOKEN_SEPARATOR.split(text)) {
            if (!piece.isEmpty()) {
                tokens.add(piece.toLowerCase(Locale.ROOT));
            }
        }
        return tokens;
    }

    private static Reranker.RerankingMetadata metadata() {
        return new Reranker.RerankingMetadata(RERANKER_NAME, true, RERANKER_DESCRIPTION);
    }
}


