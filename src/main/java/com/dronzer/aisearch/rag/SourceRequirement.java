package com.dronzer.aisearch.rag;

import com.dronzer.aisearch.dto.KnowledgeSource;

/**
 * A single source requirement within a {@link SourcePlan}.
 *
 * <p>This is a contract only for Phase 2B-0. It captures which knowledge source
 * is being requested and how strongly it is required.</p>
 */
public record SourceRequirement(

        /** The knowledge source this requirement refers to. */
        KnowledgeSource source,

        /**
         * How strongly this source is required.
         * {@code REQUIRED} means the pipeline should fail or report an error
         * if the source cannot be consulted.
         * {@code OPTIONAL} means the pipeline may proceed without it.
         */
        RequirementLevel level,

        /**
         * Optional human-readable justification for this requirement.
         * Useful for future auditability, but not required.
         */
        String reason

) {

    public SourceRequirement(KnowledgeSource source, RequirementLevel level) {
        this(source, level, null);
    }

    public static SourceRequirement required(KnowledgeSource source) {
        return new SourceRequirement(source, RequirementLevel.REQUIRED);
    }

    public static SourceRequirement required(KnowledgeSource source, String reason) {
        return new SourceRequirement(source, RequirementLevel.REQUIRED, reason);
    }

    public static SourceRequirement optional(KnowledgeSource source) {
        return new SourceRequirement(source, RequirementLevel.OPTIONAL);
    }

    public static SourceRequirement optional(KnowledgeSource source, String reason) {
        return new SourceRequirement(source, RequirementLevel.OPTIONAL, reason);
    }

    public SourceRequirement withReason(String reason) {
        return new SourceRequirement(source, level, reason);
    }
}
