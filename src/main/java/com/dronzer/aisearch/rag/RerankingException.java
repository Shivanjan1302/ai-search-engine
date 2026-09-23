package com.dronzer.aisearch.rag;

/**
 * Thrown when a reranker cannot perform reranking.
 *
 * <p>This is a contract only for Phase 2B-0.</p>
 */
public class RerankingException extends RuntimeException {

    public RerankingException(String message) {
        super(message);
    }

    public RerankingException(String message, Throwable cause) {
        super(message, cause);
    }
}
