package com.dronzer.aisearch.rag;

import com.dronzer.aisearch.dto.KnowledgeSource;

/**
 * A single source requirement within a {@link SourcePlan}.
 *
 * <p>{@link #level()} controls whether missing evidence blocks the plan, while
 * {@link #permitted()} separately controls whether the source may be used at
 * all. Existing constructors default {@code permitted} to true.</p>
 */
public record SourceRequirement(
        KnowledgeSource source,
        RequirementLevel level,
        String reason,
        boolean permitted
) {
    public SourceRequirement(KnowledgeSource source, RequirementLevel level) {
        this(source, level, null, true);
    }

    public SourceRequirement(KnowledgeSource source, RequirementLevel level, String reason) {
        this(source, level, reason, true);
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
        return new SourceRequirement(source, level, reason, permitted);
    }

    public SourceRequirement withPermitted(boolean permitted) {
        return new SourceRequirement(source, level, reason, permitted);
    }
}
