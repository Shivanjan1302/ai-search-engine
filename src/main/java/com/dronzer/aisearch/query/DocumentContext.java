package com.dronzer.aisearch.query;

import java.util.List;
import java.util.Objects;

/**
 * Request-local, pre-retrieval description of a document reference.
 *
 * <p>Filename hints originate in natural-language conversation turns and are therefore
 * untrusted. They are never document IDs. A retrieval boundary must resolve every hint
 * against documents owned by the authenticated user before it can be used as a scope.</p>
 */
public record DocumentContext(
        List<String> filenameHints,
        Reference reference,
        int ordinal,
        boolean ordered) {

    public static final int MAX_FILENAME_HINTS = 20;

    public DocumentContext {
        filenameHints = List.copyOf(Objects.requireNonNull(
                filenameHints, "filenameHints must not be null"));
        reference = Objects.requireNonNull(reference, "reference must not be null");
        if (filenameHints.size() > MAX_FILENAME_HINTS) {
            throw new IllegalArgumentException(
                    "filenameHints must not exceed " + MAX_FILENAME_HINTS + " entries");
        }
        if (filenameHints.stream().anyMatch(hint -> hint == null || hint.isBlank())) {
            throw new IllegalArgumentException("filenameHints must not contain blank values");
        }
        if (reference != Reference.ORDINAL && ordinal != 0) {
            throw new IllegalArgumentException("ordinal is only valid for ORDINAL references");
        }
        if (reference == Reference.ORDINAL && (ordinal < 1 || ordinal > MAX_FILENAME_HINTS)) {
            throw new IllegalArgumentException("ordinal must be between 1 and "
                    + MAX_FILENAME_HINTS);
        }
    }

    public DocumentContext(Reference reference, int ordinal, boolean ordered,
                           String... filenameHints) {
        this(List.of(filenameHints), reference, ordinal, ordered);
    }

    public static DocumentContext none() {
        return new DocumentContext(List.of(), Reference.NONE, 0, false);
    }

    public boolean isPresent() {
        return reference != Reference.NONE;
    }

    /** Deterministic shape of the document reference, before ownership resolution. */
    public enum Reference {
        NONE,
        SINGLE,
        ORDINAL,
        MULTI
    }
}
