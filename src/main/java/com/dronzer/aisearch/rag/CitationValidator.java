package com.dronzer.aisearch.rag;

import java.util.List;
import java.util.Optional;

/**
 * Contract for validating that a generated answer is supported by the
 * evidence it claims to use.
 *
 * <p>This is a contract only for Phase 2B-0. No implementation is provided.
 * Later phases may plug in an LLM-based validator, a rule-based validator, or
 * any other validation strategy without changing the rest of the pipeline.</p>
 *
 * <p>Implementations must be able to report both successful validation and
 * validation failures. A validator must never silently assume that an answer
 * is fully grounded.</p>
 */
public interface CitationValidator {

    /**
     * Validate that the generated answer is supported by the context pieces
     * it claims to use.
     *
     * @param userQuery   the original user query, never null
     * @param answer      the generated answer text, never null
     * @param contextPieces the context pieces presented to generation, never null
     * @param context the generation context metadata, may be null
     * @return the validation result
     */
    ValidationResult validate(
            String userQuery,
            String answer,
            List<ContextPiece> contextPieces,
            Optional<String> context
    );
}

