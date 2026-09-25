package com.dronzer.aisearch.query;

import com.dronzer.aisearch.entity.Document;
import com.dronzer.aisearch.service.DocumentService;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Set;

/**
 * Revalidates request-local filename hints against documents owned by the authenticated
 * email. The conversation never supplies or overrides a tenant/user identity.
 */
@Service
public class DefaultDocumentContextResolver implements DocumentContextResolver {

    private final DocumentService documentService;

    public DefaultDocumentContextResolver(DocumentService documentService) {
        this.documentService = Objects.requireNonNull(
                documentService, "documentService must not be null");
    }

    @Override
    public DocumentContextResolution resolve(InterpretedQuery query, String authenticatedEmail) {
        Objects.requireNonNull(query, "query must not be null");
        if (authenticatedEmail == null || authenticatedEmail.isBlank()) {
            throw new IllegalArgumentException("authenticatedEmail must not be blank");
        }

        DocumentContext context = query.documentContext().orElse(DocumentContext.none());
        if (!context.isPresent() || context.filenameHints().isEmpty()) {
            return DocumentContextResolution.noScope();
        }

        if (context.reference() == DocumentContext.Reference.SINGLE
                && context.filenameHints().size() != 1) {
            return DocumentContextResolution.ambiguous();
        }
        if (context.reference() == DocumentContext.Reference.ORDINAL
                && (!context.ordered() || context.ordinal() > context.filenameHints().size())) {
            return DocumentContextResolution.ambiguous();
        }
        if (context.reference() == DocumentContext.Reference.MULTI
                && context.filenameHints().size() < 2) {
            return DocumentContextResolution.ambiguous();
        }

        List<Document> ownedDocuments = documentService.getDocuments(authenticatedEmail);
        List<Document> candidates = new ArrayList<>();
        List<String> unavailable = new ArrayList<>();

        for (String hint : context.filenameHints()) {
            List<Document> matches = matchingDocuments(ownedDocuments, hint);
            if (matches.size() > 1) {
                return DocumentContextResolution.ambiguous();
            }
            if (matches.isEmpty()) {
                unavailable.add(hint);
            } else if (matches.get(0).getId() == null) {
                unavailable.add(hint);
            } else {
                candidates.add(matches.get(0));
            }
        }

        if (!unavailable.isEmpty()) {
            return context.reference() == DocumentContext.Reference.MULTI
                    ? DocumentContextResolution.unavailable()
                    : DocumentContextResolution.unavailable();
        }
        if (candidates.size() != context.filenameHints().size()) {
            return DocumentContextResolution.ambiguous();
        }

        if (context.reference() == DocumentContext.Reference.ORDINAL) {
            return DocumentContextResolution.resolved(
                    Set.of(candidates.get(context.ordinal() - 1).getId()));
        }
        Set<Long> resolvedIds = new LinkedHashSet<>();
        for (Document document : candidates) {
            resolvedIds.add(document.getId());
        }
        return DocumentContextResolution.resolved(resolvedIds);
    }

    private static List<Document> matchingDocuments(List<Document> documents, String hint) {
        String expected = normalize(hint);
        return documents.stream()
                .filter(Objects::nonNull)
                .filter(document -> normalize(document.getFilename()).equals(expected))
                .toList();
    }

    private static String normalize(String value) {
        return value == null ? "" : value.trim().toLowerCase(Locale.ROOT);
    }
}
