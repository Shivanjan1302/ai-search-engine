package com.dronzer.aisearch.rag;

/** Thrown when final provenance cannot be assembled safely. */
public class ProvenanceAssemblyException extends RuntimeException {

    public ProvenanceAssemblyException(String message) {
        super(message);
    }

    public ProvenanceAssemblyException(String message, Throwable cause) {
        super(message, cause);
    }
}
