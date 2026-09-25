package com.dronzer.aisearch.query;

import com.dronzer.aisearch.entity.Document;
import com.dronzer.aisearch.service.DocumentService;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class DefaultDocumentContextResolverTest {

    private static final String EMAIL = "owner@example.test";

    private final DocumentService documents = mock(DocumentService.class);
    private final DefaultDocumentContextResolver resolver =
            new DefaultDocumentContextResolver(documents);

    @Test
    void singleDocumentNameIsResolvedOnlyThroughAuthenticatedOwners() {
        when(documents.getDocuments(EMAIL)).thenReturn(List.of(document(11L, "contract.pdf")));

        InterpretedQuery query = new InterpretedQuery("What about termination?")
                .withDocumentContext(new DocumentContext(
                        List.of("contract.pdf"), DocumentContext.Reference.SINGLE, 0, false));

        DocumentContextResolution result = resolver.resolve(query, EMAIL);

        assertThat(result.status()).isEqualTo(DocumentContextResolution.Status.RESOLVED);
        assertThat(result.documentIds()).containsExactly(11L);
        verify(documents).getDocuments(EMAIL);
    }

    @Test
    void twoPlausibleDocumentsRemainAmbiguous() {
        when(documents.getDocuments(EMAIL)).thenReturn(List.of(
                document(11L, "contract.pdf"), document(12L, "policy.pdf")));

        InterpretedQuery query = new InterpretedQuery("What does it mean?")
                .withDocumentContext(new DocumentContext(
                        List.of("contract.pdf", "policy.pdf"),
                        DocumentContext.Reference.SINGLE, 0, false));

        assertThat(resolver.resolve(query, EMAIL).status())
                .isEqualTo(DocumentContextResolution.Status.AMBIGUOUS);
    }

    @Test
    void orderedReferenceSelectsOnlyTheDeterministicOrdinal() {
        when(documents.getDocuments(EMAIL)).thenReturn(List.of(
                document(11L, "contract.pdf"), document(12L, "policy.pdf")));

        InterpretedQuery query = new InterpretedQuery("What does the second document say?")
                .withDocumentContext(new DocumentContext(
                        List.of("contract.pdf", "policy.pdf"),
                        DocumentContext.Reference.ORDINAL, 2, true));

        assertThat(resolver.resolve(query, EMAIL).documentIds()).containsExactly(12L);
    }

    @Test
    void unknownOrderIsNotResolved() {
        when(documents.getDocuments(EMAIL)).thenReturn(List.of(
                document(11L, "contract.pdf"), document(12L, "policy.pdf")));

        InterpretedQuery query = new InterpretedQuery("What does the second document say?")
                .withDocumentContext(new DocumentContext(
                        List.of("contract.pdf", "policy.pdf"),
                        DocumentContext.Reference.ORDINAL, 2, false));

        assertThat(resolver.resolve(query, EMAIL).status())
                .isEqualTo(DocumentContextResolution.Status.AMBIGUOUS);
    }

    @Test
    void missingOrUnownedNameIsUnavailableAndNeverCreatesAnId() {
        when(documents.getDocuments(EMAIL)).thenReturn(List.of(document(11L, "contract.pdf")));

        InterpretedQuery query = new InterpretedQuery("What does the other document say?")
                .withDocumentContext(new DocumentContext(
                        List.of("other-tenant.pdf"), DocumentContext.Reference.SINGLE, 0, false));

        DocumentContextResolution result = resolver.resolve(query, EMAIL);

        assertThat(result.status()).isEqualTo(DocumentContextResolution.Status.UNAVAILABLE);
        assertThat(result.documentIds()).isEmpty();
    }

    @Test
    void conversationFilenameForAnotherTenantIsUnavailableAndNeverBecomesAnId() {
        when(documents.getDocuments(EMAIL)).thenReturn(List.of(document(11L, "contract.pdf")));
        ConversationContext context = ConversationContext.fromRecentTurns(List.of(
                new ConversationContext.Turn("assistant", "Other tenant document",
                        List.of("other-tenant.pdf"))));
        InterpretedQuery query = new DefaultQueryInterpreter()
                .interpret("What does it say?", context);

        assertThat(query.documentContext().orElseThrow().filenameHints())
                .containsExactly("other-tenant.pdf");
        DocumentContextResolution result = resolver.resolve(query, EMAIL);

        assertThat(result.status()).isEqualTo(DocumentContextResolution.Status.UNAVAILABLE);
        assertThat(result.documentIds()).isEmpty();
        verify(documents).getDocuments(EMAIL);
    }

    @Test
    void comparisonRetainsBothOrderedDocuments() {
        when(documents.getDocuments(EMAIL)).thenReturn(List.of(
                document(11L, "contract.pdf"), document(12L, "policy.pdf")));

        InterpretedQuery query = new InterpretedQuery("Compare contract.pdf and policy.pdf")
                .withDocumentContext(new DocumentContext(
                        List.of("contract.pdf", "policy.pdf"),
                        DocumentContext.Reference.MULTI, 0, true));

        assertThat(resolver.resolve(query, EMAIL).documentIds())
                .containsExactlyInAnyOrder(11L, 12L);
    }

    private static Document document(Long id, String filename) {
        Document document = new Document(filename, "content");
        try {
            var idField = Document.class.getDeclaredField("id");
            idField.setAccessible(true);
            idField.set(document, id);
        } catch (ReflectiveOperationException exception) {
            throw new AssertionError(exception);
        }
        return document;
    }
}

