package com.dronzer.aisearch.rag;

/** Thrown when an evidence policy cannot evaluate its inputs safely. */
public class EvidencePolicyException extends RuntimeException {
    public EvidencePolicyException(String message) {
        super(message);
    }

    public EvidencePolicyException(String message, Throwable cause) {
        super(message, cause);
    }
}
