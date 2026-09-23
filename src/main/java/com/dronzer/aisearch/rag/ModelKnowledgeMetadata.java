package com.dronzer.aisearch.rag;

import com.dronzer.aisearch.dto.KnowledgeSource;
import java.util.Objects;

/**
 * Represents the model's own parametric knowledge at generation time.
 *
 * <p>This is deliberately <strong>not</strong> an {@link Evidence}.
 * Model knowledge is not retrieved external material; it is a generation-time
 * capability. It should never be stored, cited, or treated as equivalent to
 * a document chunk or web result.</p>
 *
 * <p>This record exists so that future phases can attach structured metadata
 * to the model-knowledge path without smuggling it into the evidence pipeline
 * or pretending it has an external provenance.</p>
 */
public record ModelKnowledgeMetadata(

        /**
         * Human-readable description of what gap or context the model knowledge
         * is expected to fill. May be null if no description is available.
         */
        String gapDescription,

        /**
         * Whether the model knowledge is intended to supplement retrieved
         * evidence or to answer independently.
         */
        UsageMode usageMode

) {

    public enum UsageMode {

        /**
         * Model knowledge is used only to fill gaps in retrieved evidence.
         * The answer should still be anchored to retrieved sources where
         * possible.
         */
        SUPPLEMENTAL,

        /**
         * Model knowledge is expected to answer the query independently
         * because no retrieved evidence is available or appropriate.
         */
        INDEPENDENT
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof ModelKnowledgeMetadata that)) return false;
        return Objects.equals(gapDescription, that.gapDescription)
                && usageMode == that.usageMode;
    }

    @Override
    public int hashCode() {
        return Objects.hash(gapDescription, usageMode);
    }
}
