package io.github.lucianolattante.knowledgeai.documents;

import java.text.Normalizer;
import java.util.Locale;
import java.util.regex.MatchResult;
import java.util.regex.Pattern;
import java.util.stream.Stream;

final class TextTerms {
    private static final Pattern TOKEN = Pattern.compile("[\\p{L}\\p{N}]+");

    private TextTerms() {}

    static Stream<String> terms(String text) {
        String normalized = Normalizer.normalize(text, Normalizer.Form.NFKC).toLowerCase(Locale.ROOT);
        return TOKEN.matcher(normalized).results().map(MatchResult::group);
    }
}
