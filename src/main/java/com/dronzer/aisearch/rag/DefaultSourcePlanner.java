package com.dronzer.aisearch.rag;

import com.dronzer.aisearch.dto.KnowledgeSource;
import com.dronzer.aisearch.query.InterpretedQuery;
import com.dronzer.aisearch.query.QueryIntent;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.regex.Pattern;

/**
 * Default {@link SourcePlanner} implementation for Phase 2B-1A.
 *
 * <p>Pure function: {@link InterpretedQuery} in, {@link SourcePlan} out.
 * No database access, no HTTP calls, no LLM calls, no tenant logic,
 * no Spring wiring. Stateless and thread-safe.</p>
 *
 * <h2>Semantics</h2>
 * <ul>
 *   <li>{@link QueryIntent} is the authoritative signal when present and
 *       not {@link QueryIntent#UNKNOWN}. It always wins over heuristics.</li>
 *   <li>Explicit {@link Optional} signals on {@link InterpretedQuery}
 *       ({@code documentSpecific}, {@code requiresFreshness}, {@code webRequired},
 *       {@code mixedSource}, {@code modelKnowledgeAllowed}) win over heuristics
 *       when present, whether {@code true} or {@code false}.</li>
 *   <li>{@link Optional#empty()} means unknown and never silently means
 *       {@code false}: heuristics are consulted only when the classifier
 *       signal is absent.</li>
 *   <li>Heuristics (keyword patterns) are a clearly isolated interim
 *       fallback until a real classifier populates {@link InterpretedQuery}.
 *       They are conservative: any document/freshness signal makes a source
 *       more required, never less.</li>
 *   <li>{@code mixedSource} is derived from the actual requirements
 *       (DOCUMENT plus WEB-or-model required), never copied from the
 *       provisional query signal.</li>
 * </ul>
 */
public final class DefaultSourcePlanner implements SourcePlanner {

    // ------------------------------------------------------------------
    // Interim keyword heuristics (isolated; replaceable by a classifier).
    // ------------------------------------------------------------------

    /** Phrases suggesting the query refers to the user's private documents. */
    private static final List<Pattern> DOCUMENT_PATTERNS = List.of(
            Pattern.compile("\\bmy (document|documents|doc|docs|file|files|notes?|uploads?|pdf|report|policy|policies|contract|contracts|resume|cv|thesis|paper)\\b"),
            Pattern.compile("\\b(our|the) (company|team|org|organization)('s)? (document|documents|doc|docs|policy|policies|handbook|report|wiki|knowledge base|kb)\\b"),
            Pattern.compile("\\b(uploaded|attached|provided) (document|documents|doc|file|files)\\b"),
            Pattern.compile("\\b(according to|based on|in) (my|our|the) (document|documents|doc|docs|file|files|notes?|report|policy|policies|contract|handbook|wiki)\\b"),
            Pattern.compile("\\bwhat does (my|our|the) (document|documents|doc|docs|file|files|notes?|report|policy|policies)\\b"),
            Pattern.compile("\\b(summari[sz]e|summarise) (my|our|this|that|the) (document|documents|doc|file|files|notes?|report|policy|pdf)\\b"),
            Pattern.compile("\\b(this|that) (document|file|pdf|report|policy|contract)\\b"));

    /** Phrases suggesting the query needs current/external information. */
    private static final List<Pattern> FRESHNESS_PATTERNS = List.of(
            Pattern.compile("\\bwhat happened (today|yesterday|this week|recently)\\b"),
            Pattern.compile("\\b(latest|newest|current|recent|breaking|live|ongoing) (news|price|prices|update|updates|developments?|events?|scores?|results?|standings?|rates?|data|numbers?|figures?|stats?|statistics|release|releases)\\b"),
            Pattern.compile("\\b(current|today'?s|live) (price|prices|cost|rate|rates|value|stock|weather|score|scores|news)\\b"),
            Pattern.compile("\\b(as of|since) (today|this (week|month|year)|january|february|march|april|may|june|july|august|september|october|november|december|20\\d\\d)\\b"),
            Pattern.compile("\\bright now\\b"),
            Pattern.compile("\\b(up to date|up-to-date) (on|info|information|news|data)\\b"),
            Pattern.compile("\\bthis (week|month|year)('s)? (news|events?|results?|data|numbers?|releases?)\\b"));

    private DefaultSourcePlanner() {
        // Utility-style construction guard; instances are created via factory.
    }

    /** Create a new planner instance. Stateless; safe to share or discard. */
    public static DefaultSourcePlanner create() {
        return new DefaultSourcePlanner();
    }

    @Override
    public SourcePlan plan(InterpretedQuery interpretedQuery) {
        if (interpretedQuery == null) {
            throw new SourcePlanningException("interpretedQuery must not be null");
        }
        if (interpretedQuery.originalQuery() == null || interpretedQuery.originalQuery().isBlank()) {
            throw new SourcePlanningException("interpretedQuery.originalQuery must not be blank");
        }

        Resolution resolution = resolve(interpretedQuery);

        List<SourceRequirement> requirements = new ArrayList<>(3);
        switch (resolution.category()) {
            case DOCUMENT -> {
                requirements.add(SourceRequirement.required(
                        KnowledgeSource.DOCUMENT, "Query is document-specific; private documents are authoritative"));
                requirements.add(SourceRequirement.optional(
                        KnowledgeSource.WEB, "Web is supplementary for document-specific queries"));
                requirements.add(modelRequirement(resolution.modelKnowledgeAllowed(),
                        "Model knowledge is supplementary, never authoritative for document claims"));
            }
            case CURRENT -> {
                requirements.add(SourceRequirement.required(
                        KnowledgeSource.WEB, "Query requires fresh/current external information"));
                requirements.add(SourceRequirement.optional(
                        KnowledgeSource.DOCUMENT, "Documents are supplementary for current-information queries"));
                requirements.add(modelRequirement(false,
                        "Model knowledge is not authoritative for freshness-sensitive claims"));
            }
            case MIXED -> {
                requirements.add(SourceRequirement.required(
                        KnowledgeSource.DOCUMENT, "Query requires private-document information"));
                requirements.add(SourceRequirement.required(
                        KnowledgeSource.WEB, "Query additionally requires current/external information"));
                requirements.add(modelRequirement(resolution.modelKnowledgeAllowed(),
                        "Model knowledge is supplementary; document and web evidence stay distinguished"));
            }
            case GENERAL -> {
                requirements.add(SourceRequirement.optional(
                        KnowledgeSource.DOCUMENT, "Documents are supplementary when relevant"));
                requirements.add(SourceRequirement.optional(
                        KnowledgeSource.WEB, "Web is supplementary for general-knowledge queries"));
                requirements.add(modelRequirement(resolution.modelKnowledgeAllowed(),
                        "Model knowledge may answer general-knowledge queries; retrieved evidence supplements it"));
            }
        }

        List<SourceRequirement> sources = List.copyOf(requirements);
        boolean requiresFreshness = resolution.requiresFreshness();
        boolean mixedSource = isDocumentRequired(sources)
                && (isWebRequired(sources) || isModelRequired(sources));
        EvidenceRequirement evidenceRequirement = switch (resolution.category()) {
            case DOCUMENT, MIXED -> EvidenceRequirement.MODERATE;
            case CURRENT -> EvidenceRequirement.MODERATE;
            case GENERAL -> EvidenceRequirement.RELAXED;
        };
        FallbackPolicy fallbackPolicy = requiresFreshness
                ? FallbackPolicy.DEGRADE_GRADUALLY
                : FallbackPolicy.FAIL_FAST;

        return new SourcePlan(sources, mixedSource, requiresFreshness, evidenceRequirement, fallbackPolicy);
    }

    // ------------------------------------------------------------------
    // Resolution: explicit signals first, heuristics only when absent.
    // ------------------------------------------------------------------

    /** Planning category after combining intent, signals, and heuristics. */
    private enum Category {
        DOCUMENT,
        CURRENT,
        MIXED,
        GENERAL
    }

    /** Resolved planning inputs. */
    private record Resolution(Category category, boolean requiresFreshness, boolean modelKnowledgeAllowed) {
    }

    private static Resolution resolve(InterpretedQuery query) {
        Optional<QueryIntent> intent = query.intent();

        boolean documentSignal = query.documentSpecific().orElse(false);
        boolean freshnessSignal = query.requiresFreshness().orElse(false);
        boolean webSignal = query.webRequired().orElse(false);
        boolean hasAnyExplicitSignal = query.documentSpecific().isPresent()
                || query.requiresFreshness().isPresent()
                || query.webRequired().isPresent()
                || query.mixedSource().isPresent();
        boolean mixedSignal = query.mixedSource().orElse(false);
        boolean modelAllowed = resolveModelKnowledgeAllowed(query);

        // 1. Authoritative intent wins outright when classified.
        if (intent.isPresent() && intent.get() != QueryIntent.UNKNOWN) {
            return switch (intent.get()) {
                case DOCUMENT_SPECIFIC -> {
                    // Conservative blend: an explicit freshness/web signal alongside
                    // document intent means both are needed, never less.
                    if (freshnessSignal || webSignal) {
                        yield new Resolution(Category.MIXED, true, modelAllowed);
                    }
                    yield new Resolution(Category.DOCUMENT, false, modelAllowed);
                }
                case CURRENT_INFORMATION -> {
                    // Conservative blend: an explicit document signal alongside
                    // current intent means both are needed, never less.
                    if (documentSignal) {
                        yield new Resolution(Category.MIXED, true, modelAllowed);
                    }
                    yield new Resolution(Category.CURRENT, true, false);
                }
                case MIXED_SOURCE ->
                        new Resolution(Category.MIXED, true, modelAllowed);
                case GENERAL_KNOWLEDGE -> {
                    if (freshnessSignal || webSignal) {
                        yield new Resolution(Category.CURRENT, true, false);
                    }
                    if (documentSignal || mixedSignal) {
                        yield mixedSignal || freshnessSignal || webSignal
                                ? new Resolution(Category.MIXED, freshnessSignal || webSignal, modelAllowed)
                                : new Resolution(Category.DOCUMENT, false, modelAllowed);
                    }
                    yield new Resolution(Category.GENERAL, false, modelAllowed);
                }
                case UNKNOWN ->
                        new Resolution(Category.GENERAL, false, modelAllowed);
            };
        }

        // 2. Explicit classifier signals (present, true or false) outrank heuristics.
        if (hasAnyExplicitSignal) {
            boolean documentFocused = documentSignal;
            boolean freshnessNeeded = freshnessSignal || webSignal;
            if (documentFocused && freshnessNeeded) {
                return new Resolution(Category.MIXED, true, modelAllowed);
            }
            if (documentFocused) {
                return new Resolution(Category.DOCUMENT, false, modelAllowed);
            }
            if (freshnessNeeded) {
                return new Resolution(Category.CURRENT, true, false);
            }
            // All present signals are explicitly false: genuinely general.
            return new Resolution(Category.GENERAL, false, modelAllowed);
        }

        // 3. No signals at all: isolated interim heuristics (conservative).
        String text = effectiveText(query);
        boolean looksDocument = matchesAny(text, DOCUMENT_PATTERNS);
        boolean looksFresh = matchesAny(text, FRESHNESS_PATTERNS);
        if (looksDocument && looksFresh) {
            return new Resolution(Category.MIXED, true, modelAllowed);
        }
        if (looksDocument) {
            return new Resolution(Category.DOCUMENT, false, modelAllowed);
        }
        if (looksFresh) {
            return new Resolution(Category.CURRENT, true, false);
        }
        return new Resolution(Category.GENERAL, false, modelAllowed);
    }

    /**
     * Resolve whether model knowledge is permitted. An explicit
     * {@code modelKnowledgeAllowed} signal is always honoured. Otherwise the
     * conservative default is {@code true} (it only ever supplements retrieved
     * evidence); freshness-sensitive plans override this to {@code false}.
     */
    private static boolean resolveModelKnowledgeAllowed(InterpretedQuery query) {
        return query.modelKnowledgeAllowed().orElse(true);
    }

    private static SourceRequirement modelRequirement(boolean allowed, String reason) {
        SourceRequirement requirement = allowed
                ? SourceRequirement.optional(KnowledgeSource.MODEL_KNOWLEDGE, reason)
                : SourceRequirement.optional(KnowledgeSource.MODEL_KNOWLEDGE,
                        reason + " [currently disallowed by plan policy]");
        return requirement.withPermitted(allowed);
    }

    private static String effectiveText(InterpretedQuery query) {
        String normalized = query.normalizedQuery().orElse(null);
        String text = (normalized != null && !normalized.isBlank())
                ? normalized
                : query.originalQuery();
        return text.toLowerCase(Locale.ROOT);
    }

    private static boolean matchesAny(String text, List<Pattern> patterns) {
        for (Pattern pattern : patterns) {
            if (pattern.matcher(text).find()) {
                return true;
            }
        }
        return false;
    }

    private static boolean isDocumentRequired(List<SourceRequirement> sources) {
        return sources.stream().anyMatch(r ->
                r.source() == KnowledgeSource.DOCUMENT && r.level() == RequirementLevel.REQUIRED);
    }

    private static boolean isWebRequired(List<SourceRequirement> sources) {
        return sources.stream().anyMatch(r ->
                r.source() == KnowledgeSource.WEB && r.level() == RequirementLevel.REQUIRED);
    }

    private static boolean isModelRequired(List<SourceRequirement> sources) {
        return sources.stream().anyMatch(r ->
                r.source() == KnowledgeSource.MODEL_KNOWLEDGE && r.level() == RequirementLevel.REQUIRED);
    }
}
