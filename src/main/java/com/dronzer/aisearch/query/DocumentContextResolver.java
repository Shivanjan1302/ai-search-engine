package com.dronzer.aisearch.query;

/** Resolves untrusted conversation document hints without changing tenant identity. */
@FunctionalInterface
public interface DocumentContextResolver {

    DocumentContextResolution resolve(InterpretedQuery query, String authenticatedEmail);
}
