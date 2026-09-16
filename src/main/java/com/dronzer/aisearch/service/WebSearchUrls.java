package com.dronzer.aisearch.service;

import java.net.URI;
import java.util.Locale;

/** Pure URL parsing: never resolves DNS or fetches a result URL. */
public final class WebSearchUrls {
    private WebSearchUrls() {}

    public static URI parse(String value) {
        if (value == null || value.isBlank()) return null;
        try {
            URI uri = URI.create(value.trim());
            String scheme = uri.getScheme();
            if (!("http".equalsIgnoreCase(scheme) || "https".equalsIgnoreCase(scheme))
                    || uri.getHost() == null || uri.getRawUserInfo() != null
                    || uri.getPort() > 65535 || uri.getPort() == 0) return null;
            return uri;
        } catch (IllegalArgumentException exception) {
            return null;
        }
    }

    /** Search identity only; retain the original destination, including its fragment, for navigation. */
    public static String canonicalize(String value) {
        URI uri = parse(value);
        if (uri == null) return null;
        String scheme = uri.getScheme().toLowerCase(Locale.ROOT);
        String host = uri.getHost().toLowerCase(Locale.ROOT);
        int port = uri.getPort();
        String authority = host + (port == -1 || (port == 80 && scheme.equals("http"))
                || (port == 443 && scheme.equals("https")) ? "" : ":" + port);
        String path = uri.getRawPath();
        if (path == null || path.isEmpty()) path = "/";
        // A single terminal slash is an obvious search duplicate, except on query-bearing URLs.
        // Do not collapse repeated slashes, decode escapes, reorder queries, or change path case.
        if (uri.getRawQuery() == null && path.length() > 1 && path.endsWith("/") && !path.endsWith("//")) {
            path = path.substring(0, path.length() - 1);
        }
        return scheme + "://" + authority + path
                + (uri.getRawQuery() == null ? "" : "?" + uri.getRawQuery());
    }

    public static String breadcrumb(URI uri) {
        StringBuilder result = new StringBuilder(uri.getHost().toLowerCase(Locale.ROOT));
        for (String segment : uri.getRawPath().split("/")) {
            if (!segment.isEmpty()) result.append(" › ").append(segment);
        }
        return result.toString();
    }
}
