package com.dronzer.aisearch.rag;

import com.dronzer.aisearch.dto.KnowledgeSource;
import org.junit.jupiter.api.Test;

import java.lang.annotation.Annotation;
import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

class DefaultContextBuilderTest {
    private final DefaultContextBuilder builder = new DefaultContextBuilder();

    @Test void documentAndWebMetadataArePreserved() {
        DocumentEvidence d = doc(1, 2, "a.pdf", "D", .8, .2, .7, .9);
        WebEvidence w = new WebEvidence("https://a.test", "T", "P", "W", .7, "web");
        var result = builder.build("q", List.of(d, w), Optional.empty(), null);
        assertEquals(List.of("document", "web"), result.pieces().stream().map(ContextPiece::role).toList());
        assertSame(d, result.pieces().get(0).evidence());
        assertEquals("a.pdf", ((DocumentEvidence) result.pieces().get(0).evidence()).filename());
        assertEquals(2, ((DocumentEvidence) result.pieces().get(0).evidence()).chunkIndex());
        assertEquals("a.pdf, chunk 2", result.pieces().get(0).evidence().provenance());
        assertEquals("https://a.test", ((WebEvidence) result.pieces().get(1).evidence()).url());
        assertEquals("T", ((WebEvidence) result.pieces().get(1).evidence()).title());
        assertEquals("P", ((WebEvidence) result.pieces().get(1).evidence()).publisher());
        assertEquals("T, P", result.pieces().get(1).evidence().provenance());
        assertEquals(.7, ((WebEvidence) result.pieces().get(1).evidence()).retrievalScore());
        assertEquals("web", ((WebEvidence) result.pieces().get(1).evidence()).retrievalMethod());
    }

    @Test void documentOnlyAndWebOnlyContextsRetainSourceBoundaries() {
        DocumentEvidence document = doc(1, 0, "document", "D", 0, 0, 0, 0);
        WebEvidence web = new WebEvidence("https://a.test", "T", "P", "W", 0.7, "web");

        var documents = builder.build("q", List.of(document), Optional.empty(), null);
        var websites = builder.build("q", List.of(web), Optional.empty(), null);

        assertEquals(List.of("document"), documents.pieces().stream().map(ContextPiece::role).toList());
        assertEquals(KnowledgeSource.DOCUMENT, documents.pieces().get(0).evidence().source());
        assertEquals(List.of("web"), websites.pieces().stream().map(ContextPiece::role).toList());
        assertEquals(KnowledgeSource.WEB, websites.pieces().get(0).evidence().source());
    }

    @Test void orderingIsPreserved() {
        DocumentEvidence a = doc(1, 0, "a", "A", 0, 0, 0, 0);
        WebEvidence b = new WebEvidence("https://b.test", null, null, "B", null, null);
        DocumentEvidence c = doc(2, 0, "c", "C", 0, 0, 0, 0);
        var result = builder.build("q", List.of(a, b, c), Optional.of(new RerankResult(List.of(c, a), null)), null);
        assertEquals(List.of(c, a, b), result.pieces().stream().map(ContextPiece::evidence).toList());
    }

    @Test void exactDuplicateEvidenceIsDroppedFirstWins() {
        DocumentEvidence d1 = doc(1, 2, "a", "first", 0, 0, 0, 0);
        DocumentEvidence d2 = doc(1, 2, "b", "duplicate", 0, 0, 0, 0);
        WebEvidence w1 = new WebEvidence("https://a.test", null, null, "first", null, null);
        WebEvidence w2 = new WebEvidence("https://a.test", null, null, "duplicate", null, null);
        var result = builder.build("q", List.of(d1, d2, w1, w2), Optional.empty(), null);
        assertEquals(List.of(d1, w1), result.pieces().stream().map(ContextPiece::evidence).toList());
        assertEquals(List.of(d2, w2), result.droppedEvidence());

        var reranked = builder.build("q", List.of(d1, d2, w1, w2),
                Optional.of(new RerankResult(List.of(w1, d1), null)), null);
        assertEquals(List.of(w1, d1), reranked.pieces().stream().map(ContextPiece::evidence).toList());
        assertEquals(List.of(w2, d2), reranked.droppedEvidence());
    }

    @Test void identityUsesDocumentIdAndChunkIndexAndExactWebUrl() {
        DocumentEvidence firstChunk = doc(1, 0, "first", "A", 0, 0, 0, 0);
        DocumentEvidence secondChunk = doc(1, 1, "second", "B", 0, 0, 0, 0);
        DocumentEvidence otherDocument = doc(2, 0, "other", "C", 0, 0, 0, 0);
        WebEvidence exactUrl = new WebEvidence("https://a.test", null, null, "D", null, null);
        WebEvidence trailingSlash = new WebEvidence("https://a.test/", null, null, "E", null, null);

        var result = builder.build("q", List.of(firstChunk, secondChunk, otherDocument,
                exactUrl, trailingSlash), Optional.empty(), null);

        assertEquals(List.of(firstChunk, secondChunk, otherDocument, exactUrl, trailingSlash),
                result.pieces().stream().map(ContextPiece::evidence).toList());
        assertEquals("1::0", firstChunk.id());
        assertEquals("1::1", secondChunk.id());
        assertEquals(exactUrl.url(), exactUrl.id());
    }

    @Test void blankContentIsReported() {
        DocumentEvidence blank = doc(1, 0, "a", " \n", 0, 0, 0, 0);
        var result = builder.build("q", List.of(blank), Optional.empty(), null);
        assertTrue(result.pieces().isEmpty());
        assertEquals(List.of(blank), result.droppedEvidence());
    }

    @Test void sizeLimitsAreWholePieceOnlyAndDeterministicAtBoundaries() {
        DocumentEvidence first = doc(1, 0, "a", "1234", 0, 0, 0, 0);
        DocumentEvidence overBoundary = doc(2, 0, "b", "56789", 0, 0, 0, 0);
        DocumentEvidence pieceOverflow = doc(3, 0, "c", "text", 0, 0, 0, 0);
        var tokenBoundary = new ContextBuilder.ContextBuilderConfig(3, 1, false, false, Optional.empty());
        var result = builder.build("q", List.of(first, overBoundary), Optional.empty(), tokenBoundary);

        assertEquals(List.of(first), result.pieces().stream().map(ContextPiece::evidence).toList());
        assertEquals("1234", result.pieces().get(0).evidence().content());
        assertEquals("56789", result.droppedEvidence().get(0).content());
        assertEquals(1, result.metadata().estimatedTokenCount());
        assertEquals(List.of(first), result.pieces().stream().map(ContextPiece::evidence).toList());
        assertEquals(List.of(overBoundary), result.droppedEvidence());
        assertEquals(result, builder.build("q", List.of(first, overBoundary), Optional.empty(), tokenBoundary));

        var pieceBoundary = new ContextBuilder.ContextBuilderConfig(1, 10, false, false, Optional.empty());
        var limited = builder.build("q", List.of(first, pieceOverflow), Optional.empty(), pieceBoundary);
        assertEquals(List.of(first), limited.pieces().stream().map(ContextPiece::evidence).toList());
        assertEquals(List.of(pieceOverflow), limited.droppedEvidence());
    }

    @Test void nullAndMalformedInputFailClearly() {
        assertThrows(ContextConstructionException.class, () -> builder.build(null, List.of(), Optional.empty(), null));
        assertThrows(ContextConstructionException.class, () -> builder.build("q", null, Optional.empty(), null));
        List<Evidence> nullElement = new ArrayList<>();
        nullElement.add(null);
        assertThrows(ContextConstructionException.class, () -> builder.build("q", nullElement, Optional.empty(), null));
        Evidence malformed = new Evidence() {
            public KnowledgeSource source() { return null; }
            public String content() { return "x"; }
            public String id() { return "id"; }
            public String provenance() { return null; }
            public Double score() { return null; }
        };
        assertThrows(ContextConstructionException.class, () -> builder.build("q", List.of(malformed), Optional.empty(), null));
    }

    @Test void emptyInputAndMissingProvenanceAreSupported() {
        assertTrue(builder.build("q", List.of(), Optional.empty(), null).pieces().isEmpty());
        DocumentEvidence e = new DocumentEvidence(1L, null, 0, "content", null, 0, 0, null, "hybrid");
        assertEquals(1, builder.build("q", List.of(e), Optional.empty(), null).pieces().size());
    }

    @Test void repeatedExecutionIsDeterministicAndInputIsUnchanged() {
        DocumentEvidence document = doc(1, 0, "a", "content", 0, 0, 0, 0);
        WebEvidence web = new WebEvidence("https://a.test", null, null, "web", null, null);
        List<Evidence> input = new ArrayList<>(List.of(document, web));
        List<Evidence> ranked = new ArrayList<>(List.of(web, document));
        RerankResult rerankResult = new RerankResult(ranked, null);

        var first = builder.build("q", input, Optional.of(rerankResult), null);
        var second = builder.build("q", input, Optional.of(rerankResult), null);

        assertEquals(List.of(document, web), input);
        assertEquals(List.of(web, document), ranked);
        assertEquals(List.of(web, document), first.pieces().stream().map(ContextPiece::evidence).toList());
        assertEquals(first, second);
    }

    @Test void builderIsStatelessAndHasNoFrameworkDependencies() {
        Constructor<?>[] constructors = DefaultContextBuilder.class.getDeclaredConstructors();
        assertEquals(1, constructors.length);
        assertEquals(0, constructors[0].getParameterCount());
        for (Field field : DefaultContextBuilder.class.getDeclaredFields()) {
            assertTrue(Modifier.isStatic(field.getModifiers()), "builder must not hold injected state");
        }
        for (Annotation annotation : DefaultContextBuilder.class.getAnnotations()) {
            assertFalse(annotation.annotationType().getName().startsWith("org.springframework."),
                    "builder must remain isolated from Spring and live RAG wiring");
        }
    }

    @Test void diversificationUsesSourcePlanPrecedenceAndPreservesWithinSourceStrength() {
        DocumentEvidence d1 = doc(1, 0, "d1", "D1", 0, 0, 0, 0);
        DocumentEvidence d2 = doc(2, 0, "d2", "D2", 0, 0, 0, 0);
        WebEvidence w1 = web("https://w1.test", "W1");
        WebEvidence w2 = web("https://w2.test", "W2");
        SourcePlan webFirst = plan(
                List.of(SourceRequirement.required(KnowledgeSource.WEB),
                        SourceRequirement.required(KnowledgeSource.DOCUMENT)),
                true, true);

        var result = builder.build("q", List.of(d1, d2, w1, w2), Optional.empty(),
                config(4, 100, true, false, webFirst));

        assertEquals(List.of(w1, d1, w2, d2), evidenceIn(result));
    }

    @Test void diversificationWithoutPlanUsesFirstSeenSourceOrder() {
        DocumentEvidence d1 = doc(1, 0, "d1", "D1", 0, 0, 0, 0);
        DocumentEvidence d2 = doc(2, 0, "d2", "D2", 0, 0, 0, 0);
        WebEvidence w1 = web("https://w1.test", "W1");

        var result = builder.build("q", List.of(d1, d2, w1), Optional.empty(),
                config(3, 100, true, false, null));

        assertEquals(List.of(d1, w1, d2), evidenceIn(result));
    }

    @Test void requiredSourceAbsenceRemainsVisibleToEvidencePolicyWithoutFabrication() {
        DocumentEvidence document = doc(1, 0, "doc", "only document evidence", 0, 0, 0, 0);
        SourcePlan mixed = plan(
                List.of(SourceRequirement.required(KnowledgeSource.DOCUMENT),
                        SourceRequirement.required(KnowledgeSource.WEB)),
                true, false);

        var result = builder.build("q", List.of(document), Optional.empty(),
                config(3, 100, true, false, mixed));

        assertEquals(List.of(document), evidenceIn(result));
        assertEquals(Optional.of(mixed), result.metadata().sourcePlan());
        assertTrue(result.droppedEvidence().isEmpty());
    }


    @Test void neighboringChunksNeverExceedTokenBudgetAndRemainDeterministic() {
        DocumentEvidence previous = doc(1, 0, "manual", "12345", 0, 0, 0, 0);
        DocumentEvidence anchor = doc(1, 1, "manual", "1234", 0, 0, 0, 0);
        DocumentEvidence following = doc(1, 2, "manual", "56789", 0, 0, 0, 0);
        var settings = config(2, 3, false, true, null);

        var first = builder.build("q", List.of(anchor, previous, following),
                Optional.empty(), settings);
        var second = builder.build("q", List.of(anchor, previous, following),
                Optional.empty(), settings);

        assertEquals(List.of(anchor), evidenceIn(first));
        assertSame(previous, first.pieces().get(0).preceding().orElseThrow().evidence());
        assertTrue(first.pieces().get(0).following().isEmpty());
        assertEquals(List.of(following), first.droppedEvidence());
        assertEquals(3, first.metadata().estimatedTokenCount());
        assertEquals(first, second);
    }

    @Test void rerankCannotIntroduceForeignEvidenceOrInvalidEntries() {
        DocumentEvidence retrieved = doc(1, 0, "doc", "retrieved", 0, 0, 0, 0);
        WebEvidence foreign = web("https://foreign.test", "foreign");

        var introduced = builder.build("q", List.of(retrieved),
                Optional.of(new RerankResult(List.of(foreign), null)), null);
        assertEquals(List.of(retrieved), evidenceIn(introduced));

        List<Evidence> withNull = new ArrayList<>();
        withNull.add(null);
        assertThrows(ContextConstructionException.class,
                () -> builder.build("q", List.of(retrieved),
                        Optional.of(new RerankResult(withNull, null)), null));
        assertThrows(ContextConstructionException.class,
                () -> builder.build("q", List.of(retrieved),
                        Optional.of(new RerankResult(null, null)), null));
    }

    @Test void invalidConfigurationAndSourcePlanAreRejectedExplicitly() {
        assertThrows(ContextConstructionException.class,
                () -> builder.build("q", List.of(), Optional.empty(), config(0, 10, false, false, null)));
        assertThrows(ContextConstructionException.class,
                () -> builder.build("q", List.of(), Optional.empty(), config(1, 0, false, false, null)));
        assertThrows(ContextConstructionException.class,
                () -> builder.build("q", List.of(), Optional.empty(),
                        config(1, 10, false, false, plan(
                                List.of(new SourceRequirement(null, RequirementLevel.REQUIRED)),
                                false, false))));

        SourcePlan invalidPolicy = new SourcePlan(
                List.of(SourceRequirement.required(KnowledgeSource.DOCUMENT)),
                false, false, null, FallbackPolicy.FAIL_FAST);
        assertThrows(ContextConstructionException.class,
                () -> builder.build("q", List.of(), Optional.empty(),
                        config(1, 10, false, false, invalidPolicy)));
    }

    @Test void evidenceAccessorFailureIsWrappedAsContextConstructionFailure() {
        Evidence broken = new Evidence() {
            @Override public KnowledgeSource source() { throw new IllegalStateException("broken source"); }
            @Override public String content() { return "unused"; }
            @Override public String id() { return "unused"; }
            @Override public String provenance() { return null; }
            @Override public Double score() { return null; }
        };

        ContextConstructionException failure = assertThrows(ContextConstructionException.class,
                () -> builder.build("q", List.of(broken), Optional.empty(), null));
        assertInstanceOf(IllegalStateException.class, failure.getCause());
    }

    @Test void outputCollectionsAreImmutableAndDropMetadataIsDeterministic() {
        DocumentEvidence first = doc(1, 0, "a", "first", 0, 0, 0, 0);
        DocumentEvidence duplicate = doc(1, 0, "b", "duplicate", 0, 0, 0, 0);
        DocumentEvidence overflow = doc(2, 0, "c", "overflow", 0, 0, 0, 0);
        var result = builder.build("q", List.of(first, duplicate, overflow), Optional.empty(),
                config(1, 100, false, false, null));

        assertThrows(UnsupportedOperationException.class,
                () -> result.pieces().add(new ContextPiece(overflow)));
        assertThrows(UnsupportedOperationException.class,
                () -> result.droppedEvidence().clear());
        assertEquals("duplicate=1, pieceLimit=1", result.metadata().droppedReasonSummary().orElseThrow());
        assertEquals(2, result.metadata().piecesDropped());
    }

    @Test void neighboringRetrievedChunksAreAttachedWithProvenanceAndNoTopLevelDuplication() {
        DocumentEvidence chunk0 = doc(1, 0, "manual", "chunk zero", 0, 0, 0, 0);
        DocumentEvidence chunk1 = doc(1, 1, "manual", "chunk one", 0, 0, 0, 0);
        DocumentEvidence chunk2 = doc(1, 2, "manual", "chunk two", 0, 0, 0, 0);
        DocumentEvidence chunk3 = doc(1, 3, "manual", "chunk three", 0, 0, 0, 0);

        var result = builder.build("q", List.of(chunk1, chunk0, chunk2, chunk3),
                Optional.empty(), config(2, 20, false, true, null));

        assertEquals(List.of(chunk1, chunk3), evidenceIn(result));
        assertSame(chunk0, result.pieces().get(0).preceding().orElseThrow().evidence());
        assertSame(chunk2, result.pieces().get(0).following().orElseThrow().evidence());
        assertEquals("document", result.pieces().get(0).preceding().orElseThrow().role());
        assertEquals(12, result.metadata().estimatedTokenCount());
        assertTrue(result.droppedEvidence().isEmpty());
    }

    private static List<Evidence> evidenceIn(ContextBuilder.ConstructionResult result) {
        return result.pieces().stream().map(ContextPiece::evidence).toList();
    }

    private static WebEvidence web(String url, String content) {
        return new WebEvidence(url, "Title", "Publisher", content, 0.5, "web");
    }

    private static SourcePlan plan(
            List<SourceRequirement> sources, boolean mixed, boolean freshness) {
        return new SourcePlan(sources, mixed, freshness,
                EvidenceRequirement.STRICT, FallbackPolicy.FAIL_FAST);
    }

    private static ContextBuilder.ContextBuilderConfig config(
            int maxPieces, int tokenBudget, boolean diversify,
            boolean neighbors, SourcePlan sourcePlan) {
        return new ContextBuilder.ContextBuilderConfig(
                maxPieces, tokenBudget, diversify, neighbors, Optional.ofNullable(sourcePlan));
    }

    private static DocumentEvidence doc(long id, int chunk, String file, String content,
                                        double similarity, double keyword, double hybrid, double retrieval) {
        return new DocumentEvidence(id, file, chunk, content, similarity, keyword, hybrid, retrieval, "hybrid");
    }
}
