package io.github.lucianolattante.knowledgeai.documents;

import java.text.Normalizer;
import java.util.Locale;
import java.util.Set;
import java.util.regex.MatchResult;
import java.util.regex.Pattern;
import java.util.stream.Stream;

final class TextTerms {
    private static final Pattern TOKEN = Pattern.compile("[\\p{L}\\p{N}]+");

    private static final Set<String> QUESTION_WORDS = Set.of(
            "a", "an", "and", "are", "as", "by", "do", "does", "for", "from", "how", "in",
            "is", "it", "of", "on", "or", "the", "to", "was", "what", "when", "where", "which", "who", "with",
            "al", "como", "cómo", "con", "cual", "cuál", "de", "del", "el", "en", "es", "la", "las",
            "los", "o", "para", "por", "que", "qué", "un", "una", "y");

    private TextTerms() {}

    static Stream<String> queryTerms(String question) {
        return terms(question).filter(term -> !QUESTION_WORDS.contains(term));
    }

    static Stream<String> terms(String text) {
        String normalized = Normalizer.normalize(text, Normalizer.Form.NFKC).toLowerCase(Locale.ROOT);
        return TOKEN.matcher(normalized).results().map(MatchResult::group);
    }
}
