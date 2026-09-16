package com.dronzer.aisearch.exception;

public class WebSearchUpstreamException extends RuntimeException {

    public static final String CODE = "WEB_SEARCH_UNAVAILABLE";

    public WebSearchUpstreamException() {
        super("Web search is temporarily unavailable");
    }
}