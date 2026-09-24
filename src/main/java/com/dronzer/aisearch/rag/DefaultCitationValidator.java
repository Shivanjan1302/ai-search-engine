package com.dronzer.aisearch.rag;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Deterministic citation validator for the grounded answer contract.
 *
 * <p>Generated answers may cite supplied context as {@code [E1]}, {@code [E2]},
 * etc. Every such marker must identify a context piece. Any URL in the answer
 * must also be one of the supplied web URLs. The validator deliberately does
 * not attempt natural-language entailment; it enforces the citation and source
 * identity invariants that can be checked deterministically at this boundary.
 */
public final class DefaultCitationValidator implements CitationValidator {

    private static final Pattern CITATION = Pattern.compile("\\[E(\\d+)]");
    private static final Pattern URL = Pattern.compile("https?://[^\\s)\\]}>\"']+");

    @Override
    public ValidationResult validate(
            String userQuery,
            String answer,
            List<ContextPiece> contextPieces,
            Optional<String> context) {

        if (userQuery == null || userQuery.isBlank()) {
            throw new CitationValidationException("userQuery must not be null or blank");
        }
        if (answer == null || answer.isBlank()) {
            throw new CitationValidationException("answer must not be null or blank");
        }
        if (contextPieces == null) {
            throw new CitationValidationException("contextPieces must not be null");
        }
        if (context == null) {
            throw new CitationValidationException("context must not be null");
        }

        Set<String> suppliedUrls = new LinkedHashSet<>();
        for (int index = 0; index < contextPieces.size(); index++) {
            ContextPiece piece = contextPieces.get(index);
            if (piece == null || piece.evidence() == null) {
                throw new CitationValidationException(
                        "contextPieces contains an invalid piece at index " + index);
            }
            if (piece.evidence() instanceof WebEvidence web && web.url() != null) {
                suppliedUrls.add(web.url());
            }
        }

        List<UnsupportedClaim> invalid = new ArrayList<>();
        Matcher citationMatcher = CITATION.matcher(answer);
        while (citationMatcher.find()) {
            int citationNumber = Integer.parseInt(citationMatcher.group(1));
            if (citationNumber < 1 || citationNumber > contextPieces.size()) {
                invalid.add(new UnsupportedClaim(
                        citationMatcher.group(),
                        Optional.of("citation:" + citationMatcher.start()),
                        Optional.of("citation does not identify supplied context")));
            }
        }

        Matcher urlMatcher = URL.matcher(answer);
        while (urlMatcher.find()) {
            String url = urlMatcher.group();
            if (!suppliedUrls.contains(url)) {
                invalid.add(new UnsupportedClaim(
                        url,
                        Optional.of("url:" + urlMatcher.start()),
                        Optional.of("URL was not supplied as web evidence")));
            }
        }

        if (invalid.isEmpty()) {
            return new ValidationResult(
                    true,
                    false,
                    "All generated citations and URLs identify supplied context",
                    Optional.of(List.of()));
        }
        return new ValidationResult(
                false,
                true,
                "Generated answer contains citations or URLs outside the supplied context",
                Optional.of(List.copyOf(invalid)));
    }
}
