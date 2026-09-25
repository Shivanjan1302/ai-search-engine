package com.dronzer.aisearch.query;

import java.util.Set;

/** Result of revalidating request-local document hints against the authenticated tenant. */
public record DocumentContextResolution(Status status, Set<Long> documentIds) {

    public DocumentContextResolution {
        if (status == null) {
            throw new IllegalArgumentException("status must not be null");
        }
        documentIds = Set.copyOf(documentIds == null ? Set.of() : documentIds);
        if (status == Status.RESOLVED && documentIds.isEmpty()) {
            throw new IllegalArgumentException("resolved documentIds must not be empty");
        }
        if (status != Status.RESOLVED && !documentIds.isEmpty()) {
            throw new IllegalArgumentException("only resolved documentIds may be non-empty");
        }
    }

    public static DocumentContextResolution noScope() {
        return new DocumentContextResolution(Status.NO_SCOPE, Set.of());
    }

    public static DocumentContextResolution ambiguous() {
        return new DocumentContextResolution(Status.AMBIGUOUS, Set.of());
    }

    public static DocumentContextResolution unavailable() {
        return new DocumentContextResolution(Status.UNAVAILABLE, Set.of());
    }

    public static DocumentContextResolution resolved(Set<Long> documentIds) {
        return new DocumentContextResolution(Status.RESOLVED, documentIds);
    }

    public enum Status {
        /** No request-local document context was established. */
        NO_SCOPE,
        /** Multiple or unordered antecedents prevent deterministic resolution. */
        AMBIGUOUS,
        /** A reference exists, but its owned document is missing or unavailable. */
        UNAVAILABLE,
        /** Every selected ID was revalidated against the authenticated user's documents. */
        RESOLVED
    }
}
