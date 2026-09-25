package com.dronzer.aisearch.query;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Deterministic, conservative interpretation of obvious conversation follow-ups. */
public final class DefaultQueryInterpreter implements QueryInterpretationService {

    private static final Pattern ORDINAL = Pattern.compile(
            "\\b(first|second|third)\\s+(one|document)\\b", Pattern.CASE_INSENSITIVE);
    private static final Pattern PREVIOUS = Pattern.compile(
            "\\bprevious\\s+one\\b", Pattern.CASE_INSENSITIVE);
    private static final Pattern SINGULAR = Pattern.compile(
            "\\b(it|this|that)\\b", Pattern.CASE_INSENSITIVE);
    private static final Pattern PLURAL = Pattern.compile(
            "\\b(these|those)\\b", Pattern.CASE_INSENSITIVE);
    private static final Pattern SUBJECT = Pattern.compile(
            "(?i)(?:the\\s+|a\\s+|an\\s+)?([a-z][a-z0-9' -]{1,80}?)\\s+"
                    + "(?:is|are|was|were|includes?|covered|covers?|describes?|"
                    + "explains?|means?|states?|governs?|applies?)\\b");
    private static final Pattern TOPIC = Pattern.compile(
            "(?i)\\b(?:about|regarding|concerning)\\s+(.+?)(?:[.!?]|$)");
    private static final Pattern ITEM = Pattern.compile(
            "(?i)(?:^|\\s)(?:\\d+[.)]|[-*])\\s*([^\\n]+?)"
                    + "(?=(?:\\s+\\d+[.)]\\s*)|$)");
    private static final Pattern DOCUMENT = Pattern.compile(
            "(?im)(?:^|\\n|\\s)(?:document|doc)\\s+([A-Z0-9]+)"
                    + "\\s*(?:[:\\-–]\\s*([^\\n]+))?");
    private static final Pattern DECLARED_ORDINAL = Pattern.compile(
            "(?i)\\b(first|second|third)\\s+(one|document)\\s+(?:is|was|=|:)\\s+"
                    + "([a-z][a-z0-9' -]{1,80}?)(?:[.!?]|$)");
    private static final Pattern FILENAME = Pattern.compile(
            "(?i)(?:^|[\\s\"'`(])([a-z0-9][a-z0-9 ._()/-]{0,180}?"
                    + "\\.(?:pdf|txt|docx?|rtf|md|csv))(?:$|[\\s\"'`),.;:!?])");
    private static final Pattern COMPARISON = Pattern.compile(
            "(?i)\\b(compare|comparison|differences?|differ|versus|vs\\.?|both|"
                    + "all documents|across documents|two documents)\\b");
    private static final Pattern DOCUMENT_REFERENCE = Pattern.compile(
            "(?i)\\b(document|documents|doc|file|it|this|that|first|second|third|"
                    + "both)\\b");
    private static final Pattern IMPLICIT_FOLLOW_UP = Pattern.compile(
            "(?i)^\\s*(?:what about|anything about|does it|do they|is there|"
                    + "anything else|how about)\\b");

    @Override
    public InterpretedQuery interpret(
            String question, Optional<ConversationContext> context) {
        if (question == null || question.isBlank()) {
            throw new IllegalArgumentException("question must not be blank");
        }
        if (context == null) {
            throw new IllegalArgumentException("conversationContext must not be null");
        }
        InterpretedQuery result = new InterpretedQuery(question);
        if (context.isEmpty()) {
            return result;
        }
        ConversationContext conversation = context.get();
        result = result.withConversationContext(conversation);
        DocumentContext documentContext = interpretDocumentContext(question, conversation.recentTurns());
        if (documentContext.isPresent()) {
            result = result.withDocumentContext(documentContext)
                    .withDocumentSpecific(true);
        }
        InterpretationResult interpretation = interpretReference(question, conversation.recentTurns());
        if (isUnchanged(interpretation) && IMPLICIT_FOLLOW_UP.matcher(question).find()) {
            Optional<String> contextualSubject = contextualSubject(conversation.recentTurns());
            if (contextualSubject.isPresent() && filenames(question).isEmpty()) {
                interpretation = InterpretationResult.resolved(
                        contextualize(question, contextualSubject.get()));
            }
        }
        InterpretedQuery statusResult = result.withInterpretationStatus(interpretation.status());
        return interpretation.normalizedQuery()
                .map(statusResult::withNormalizedQuery)
                .orElse(statusResult);
    }

    private static DocumentContext interpretDocumentContext(
            String question, List<ConversationContext.Turn> turns) {
        List<String> currentFilenames = filenames(question);
        Set<String> recentFilenames = new LinkedHashSet<>();
        boolean assistantSourceOrder = false;
        for (ConversationContext.Turn turn : turns) {
            if (turn == null) {
                continue;
            }
            if ("assistant".equals(turn.role()) && !turn.sourceFilenames().isEmpty()) {
                recentFilenames.addAll(turn.sourceFilenames());
                // Assistant metadata is client-supplied in the request-local contract.
                // It may contribute candidate names, but never establishes ordering.
                assistantSourceOrder = false;
            }
        }
        String latestUser = latest(turns, "user");
        if (latestUser != null) {
            recentFilenames.addAll(filenames(latestUser));
        }

        List<String> ordered = new ArrayList<>(currentFilenames);
        for (String filename : recentFilenames) {
            if (!containsIgnoreCase(ordered, filename)) {
                ordered.add(filename);
            }
        }

        if (COMPARISON.matcher(question).find()) {
            if (ordered.size() >= 2) {
                return new DocumentContext(ordered, DocumentContext.Reference.MULTI, 0, true);
            }
            return new DocumentContext(List.of(), DocumentContext.Reference.MULTI, 0, false);
        }
        if (ORDINAL.matcher(question).find()) {
            int ordinal = ordinalNumber(question);
            boolean explicitlyOrdered = ordered.size() >= 2
                    && (currentFilenames.size() >= 2 || assistantSourceOrder
                    || mentionsOrderedSequence(latestUser));
            return new DocumentContext(ordered, DocumentContext.Reference.ORDINAL,
                    ordinal, explicitlyOrdered);
        }

        boolean documentReference = DOCUMENT_REFERENCE.matcher(question).find()
                || IMPLICIT_FOLLOW_UP.matcher(question).find();
        if (currentFilenames.size() == 1) {
            return new DocumentContext(currentFilenames, DocumentContext.Reference.SINGLE, 0, false);
        }
        if (documentReference && ordered.size() == 1) {
            return new DocumentContext(ordered, DocumentContext.Reference.SINGLE, 0, false);
        }
        if (documentReference && ordered.size() > 1) {
            return new DocumentContext(ordered, DocumentContext.Reference.SINGLE, 0, false);
        }
        return DocumentContext.none();
    }

    private static List<String> filenames(String content) {
        if (content == null || content.isBlank()) {
            return List.of();
        }
        Matcher matcher = FILENAME.matcher(content);
        List<String> values = new ArrayList<>();
        while (matcher.find()) {
            String filename = matcher.group(1).trim().replaceAll("\\s+", " ");
            if (!filename.isBlank() && !containsIgnoreCase(values, filename)) {
                values.add(filename);
            }
        }
        return values.size() > DocumentContext.MAX_FILENAME_HINTS
                ? List.copyOf(values.subList(0, DocumentContext.MAX_FILENAME_HINTS))
                : List.copyOf(values);
    }

    private static boolean mentionsOrderedSequence(String content) {
        if (content == null) {
            return false;
        }
        List<String> names = filenames(content);
        return names.size() >= 2 && (content.toLowerCase(Locale.ROOT).contains(" and ")
                || COMPARISON.matcher(content).find());
    }

    private static int ordinalNumber(String question) {
        String lower = question.toLowerCase(Locale.ROOT);
        if (lower.contains("third")) {
            return 3;
        }
        if (lower.contains("second")) {
            return 2;
        }
        return 1;
    }

    private static boolean containsIgnoreCase(List<String> values, String candidate) {
        return values.stream().anyMatch(value -> value.equalsIgnoreCase(candidate));
    }

    private static Optional<String> contextualSubject(List<ConversationContext.Turn> turns) {
        Set<String> filenames = new LinkedHashSet<>();
        for (ConversationContext.Turn turn : turns) {
            if (turn != null) {
                for (String filename : filenames(turn.content())) {
                    filenames.add(filename);
                }
            }
        }
        return filenames.size() == 1
                ? Optional.of(filenames.iterator().next())
                : Optional.empty();
    }

    private static String contextualize(String question, String subject) {
        String stem = question.trim().replaceFirst("[.!?]+$", "").trim();
        return stem + " in " + subject;
    }

    private static boolean isUnchanged(InterpretationResult interpretation) {
        return interpretation.status() == InterpretationStatus.UNCHANGED;
    }

    private static InterpretationResult interpretReference(
            String question, List<ConversationContext.Turn> turns) {
        if (!hasReference(question)) {
            return InterpretationResult.unchanged();
        }

        String user = latest(turns, "user");
        String evidence = latestTurn(turns);
        if (evidence == null || evidence.isBlank()) {
            return InterpretationResult.ambiguous();
        }

        Optional<String> declared = declaredOrdinal(question, evidence);
        if (declared.isPresent()) {
            return InterpretationResult.resolved(
                    rewriteOrdinal(question, declared.get(), contextSuffix(evidence, user)));
        }

        Optional<String> ordinal = ordinal(question, orderedItems(evidence));
        if (hasOrdinalReference(question)) {
            return ordinal.isPresent()
                    ? InterpretationResult.resolved(rewriteOrdinal(
                    question, ordinal.get(), contextSuffix(evidence, user)))
                    : InterpretationResult.ambiguous();
        }

        if (SINGULAR.matcher(question).find()) {
            if (!isFollowUp(question)) {
                return InterpretationResult.ambiguous();
            }
            List<String> subjects = subjects(evidence);
            if (subjects.size() == 1 && !containsCoordination(subjects.get(0))) {
                return InterpretationResult.resolved(
                        replace(question, SINGULAR, article(subjects.get(0))));
            }
            return InterpretationResult.ambiguous();
        }

        if (PLURAL.matcher(question).find()) {
            List<String> items = orderedItems(evidence);
            if (items.size() >= 2) {
                return InterpretationResult.resolved(
                        replace(question, PLURAL, String.join(" and ", items)));
            }
            List<String> subjects = subjects(evidence);
            if (subjects.size() == 1) {
                return InterpretationResult.resolved(
                        replace(question, PLURAL, article(subjects.get(0))));
            }
        }
        return InterpretationResult.ambiguous();
    }

    private record InterpretationResult(
            InterpretationStatus status, Optional<String> normalizedQuery) {
        private static InterpretationResult unchanged() {
            return new InterpretationResult(InterpretationStatus.UNCHANGED, Optional.empty());
        }

        private static InterpretationResult resolved(String normalizedQuery) {
            return new InterpretationResult(InterpretationStatus.RESOLVED, Optional.of(normalizedQuery));
        }

        private static InterpretationResult ambiguous() {
            return new InterpretationResult(InterpretationStatus.AMBIGUOUS, Optional.empty());
        }
    }

    private static Optional<String> declaredOrdinal(String question, String evidence) {
        Matcher declaration = DECLARED_ORDINAL.matcher(evidence);
        if (!declaration.find()) {
            return Optional.empty();
        }
        Matcher reference = ORDINAL.matcher(question);
        if (!reference.find()
                || !declaration.group(1).equalsIgnoreCase(reference.group(1))) {
            return Optional.empty();
        }
        String target = clean(declaration.group(3));
        return usable(target) ? Optional.of(target) : Optional.empty();
    }

    private static Optional<String> ordinal(String question, List<String> items) {
        Matcher previous = PREVIOUS.matcher(question);
        if (previous.find() && !items.isEmpty()) {
            return Optional.of(items.get(items.size() - 1));
        }
        Matcher matcher = ORDINAL.matcher(question);
        if (!matcher.find() || items.isEmpty()) {
            return Optional.empty();
        }
        int index = switch (matcher.group(1).toLowerCase(Locale.ROOT)) {
            case "first" -> 0;
            case "second" -> 1;
            case "third" -> 2;
            default -> -1;
        };
        return index >= 0 && index < items.size()
                ? Optional.of(items.get(index)) : Optional.empty();
    }

    private static String rewriteOrdinal(String question, String item, String suffix) {
        Matcher matcher = ORDINAL.matcher(question);
        Matcher previous = PREVIOUS.matcher(question);
        int start;
        int end;
        if (matcher.find()) {
            start = matcher.start();
            end = matcher.end();
        } else if (previous.find()) {
            start = previous.start();
            end = previous.end();
        } else {
            return question;
        }
        String resolved = item + (suffix.isBlank() ? "" : " " + suffix);
        String prefix = question.substring(0, start);
        if (prefix.toLowerCase(Locale.ROOT).endsWith("the ")) {
            prefix = prefix.substring(0, prefix.length() - 4);
        }
        return prefix + resolved + question.substring(end);
    }

    private static String replace(String question, Pattern reference, String replacement) {
        Matcher matcher = reference.matcher(question);
        if (!matcher.find()) {
            return question;
        }
        return question.substring(0, matcher.start()) + replacement + question.substring(matcher.end());
    }

    private static boolean isFollowUp(String question) {
        String lower = question.toLowerCase(Locale.ROOT);
        return lower.matches(".*\\b(what about|tell me more about|explain|why is|how does|what is|what are)\\b.*");
    }

    private static boolean hasReference(String question) {
        return hasOrdinalReference(question)
                || SINGULAR.matcher(question).find()
                || PLURAL.matcher(question).find();
    }

    private static boolean hasOrdinalReference(String question) {
        return ORDINAL.matcher(question).find() || PREVIOUS.matcher(question).find();
    }

    private static List<String> subjects(String content) {
        Matcher declaration = DECLARED_ORDINAL.matcher(content);
        if (declaration.find()) {
            String target = clean(declaration.group(3));
            return usable(target) ? List.of(target) : List.of();
        }

        List<String> subjects = new ArrayList<>();
        Matcher subject = SUBJECT.matcher(content);
        while (subject.find()) {
            String candidate = clean(subject.group(1));
            if (usable(candidate) && !subjects.contains(candidate)) {
                subjects.add(candidate);
            }
        }
        if (subjects.isEmpty()) {
            Matcher topic = TOPIC.matcher(content);
            while (topic.find()) {
                String candidate = clean(topic.group(1));
                if (usable(candidate) && !subjects.contains(candidate)) {
                    subjects.add(candidate);
                }
            }
        }
        return subjects;
    }

    private static Optional<String> subject(String content) {
        List<String> subjects = subjects(content);
        return subjects.isEmpty() ? Optional.empty() : Optional.of(subjects.get(0));
    }

    private static boolean containsCoordination(String subject) {
        return subject.toLowerCase(Locale.ROOT).matches(".*\\b(?:and|or)\\b.*");
    }

    private static String clean(String value) {
        String result = value.trim().replaceAll("[\\s,;:]+$", "")
                .replaceAll("\\s+", " ");
        result = result.replaceFirst("^(?:the|a|an)\\s+", "");
        return result.replaceFirst("\\s+(?:and|or)$", "");
    }

    private static boolean usable(String subject) {
        if (subject == null || subject.length() < 3 || subject.length() > 100) {
            return false;
        }
        String lower = subject.toLowerCase(Locale.ROOT);
        return !lower.equals("it") && !lower.equals("this") && !lower.equals("that")
                && !lower.equals("answer") && !lower.equals("question")
                && !lower.equals("risk") && !lower.equals("risks")
                && !lower.equals("document") && !lower.equals("documents");
    }

    private static String article(String subject) {
        return subject.toLowerCase(Locale.ROOT).startsWith("the ") ? subject : "the " + subject;
    }

    private static List<String> orderedItems(String content) {
        Matcher matcher = ITEM.matcher(content);
        List<String> items = new ArrayList<>();
        while (matcher.find()) {
            String item = matcher.group(1).trim()
                    .replaceFirst("[.!?].*$", "")
                    .replaceFirst("\\s+(?:and|or)$", "")
                    .trim();
            if (!item.isBlank()) {
                items.add(item);
            }
        }
        if (!items.isEmpty()) {
            return items;
        }
        Matcher documents = DOCUMENT.matcher(content);
        while (documents.find()) {
            String label = "Document " + documents.group(1);
            String description = documents.group(2) == null ? "" : documents.group(2).trim();
            items.add(description.isBlank() ? label : label + ": " + description);
        }
        return items;
    }

    private static String contextSuffix(String assistant, String user) {
        if (user == null) {
            return "";
        }
        Optional<String> subjectTopic = subject(assistant);
        if (subjectTopic.isEmpty()) {
            return "";
        }
        String marker = subjectTopic.get();
        int index = user.toLowerCase(Locale.ROOT).indexOf(marker.toLowerCase(Locale.ROOT));
        if (index < 0) {
            return "";
        }
        String suffix = user.substring(index + marker.length()).trim();
        suffix = suffix.replaceFirst("[.!?]+$", "");
        if (suffix.matches("(?i)^(?:in|on|for|from)\\b.*")) {
            return suffix;
        }
        java.util.regex.Matcher suffixTopic = java.util.regex.Pattern.compile(
                "(?i)\\b(?:in|on|for|from)\\s+(?:this|my|the)\\s+[^.!?]+")
                .matcher(user);
        return suffixTopic.find() ? suffixTopic.group() : "";
    }

    private static String latestTurn(List<ConversationContext.Turn> turns) {
        if (turns == null || turns.isEmpty()) {
            return null;
        }
        ConversationContext.Turn turn = turns.get(turns.size() - 1);
        return turn == null || turn.content() == null ? null : turn.content().trim();
    }

    private static String latest(List<ConversationContext.Turn> turns, String role) {
        for (int i = turns.size() - 1; i >= 0; i--) {
            ConversationContext.Turn turn = turns.get(i);
            if (turn != null && role.equals(turn.role()) && turn.content() != null) {
                return turn.content().trim();
            }
        }
        return null;
    }
}
