package com.dronzer.aisearch.dto;

public record WebSearchResult(
        String title,
        String url,
        String snippet,
        String publisher,
        String domain,
        String path,
        String breadcrumb,
        String publishedDate) {
    public WebSearchResult(String title, String url, String snippet, String publisher) {
        this(title, url, snippet, publisher,
             extractDomain(url), extractPath(url), extractBreadcrumb(url, title),
             null);
    }

    public WebSearchResult(String title, String url, String snippet, String publisher,
                            String domain, String path, String breadcrumb, String publishedDate) {
        this.title = title;
        this.url = url;
        this.snippet = snippet;
        this.publisher = publisher;
        this.domain = domain;
        this.path = path;
        this.breadcrumb = breadcrumb;
        this.publishedDate = publishedDate;
    }

    public static String extractDomain(String url) {
        if (url == null) return null;
        try {
            var host = java.net.URI.create(url).getHost();
            return host == null ? null : host.toLowerCase(java.util.Locale.ROOT);
        } catch (Exception e) {
            return null;
        }
    }

    public static String extractPath(String url) {
        if (url == null) return null;
        try {
            String path = java.net.URI.create(url).getPath();
            if (path == null || path.isEmpty()) return "/";
            if (path.length() > 1 && path.endsWith("/")) return path.substring(0, path.length() - 1);
            return path;
        } catch (Exception e) {
            return null;
        }
    }

    public static String extractBreadcrumb(String url, String title) {
        String path = extractPath(url);
        if (path == null || path.equals("/") || path.isEmpty()) {
            String domain = extractDomain(url);
            return domain != null ? domain : (title != null ? title : "");
        }
        try {
            var uri = java.net.URI.create(url);
            String[] segments = path.split("/");
            StringBuilder display = new StringBuilder();
            String domain = extractDomain(url);
            if (domain != null) display.append(domain);
            int start = 0;
            if (segments.length > 0 && segments[0].isEmpty()) start = 1;
            int count = 0;
            for (int i = segments.length - 1; i >= start && count < 3; i--, count++) {
                String seg = segments[i];
                if (seg.isEmpty()) continue;
                String cleaned = seg.replaceFirst("\\.[a-zA-Z]+$", "")
                        .replaceAll("[-_]+", " ")
                        .replaceAll("\s+", " ")
                        .trim();
                if (!cleaned.isEmpty()) {
                    if (display.length() > 0) display.append(" › ");
                    display.append(cleaned);
                }
            }
            return display.length() > 0 ? display.toString() : (title != null ? title : path);
        } catch (Exception e) {
            return path;
        }
    }
}
