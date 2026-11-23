package io.github.lucianolattante.knowledgeai.documents;

import java.text.BreakIterator;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;

import org.springframework.stereotype.Component;

@Component
class LocalAnswerProvider {
    Optional<String> passage(Set<String> queryTerms, String excerpt) {
        var sentences = BreakIterator.getSentenceInstance(Locale.ROOT);
        sentences.setText(excerpt);
        String best = null;
        long topScore = 0;
        int start = sentences.first();
        for (int end = sentences.next(); end != BreakIterator.DONE; end = sentences.next()) {
            String sentence = excerpt.substring(start, end).strip();
            long score = TextTerms.terms(sentence).distinct().filter(queryTerms::contains).count();
            if (score > topScore) {
                best = sentence;
                topScore = score;
            }
            start = end;
        }
        return Optional.ofNullable(best);
    }
}
