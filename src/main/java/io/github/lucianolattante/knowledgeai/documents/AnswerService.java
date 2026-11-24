package io.github.lucianolattante.knowledgeai.documents;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;

import io.github.lucianolattante.knowledgeai.documents.DocumentService.Identity;

@Service
public class AnswerService {
    private static final int CANDIDATE_LIMIT = 10;
    private static final int MAX_PASSAGES = 3;

    private final DocumentService documents;
    private final LocalAnswerProvider provider;

    AnswerService(DocumentService documents, LocalAnswerProvider provider) {
        this.documents = documents;
        this.provider = provider;
    }

    public Answer answer(Identity owner, String question) {
        var candidates = documents.search(owner, question, CANDIDATE_LIMIT);
        var queryTerms = TextTerms.queryTerms(question).collect(Collectors.toSet());
        var passages = new ArrayList<Passage>();
        var citations = new ArrayList<Citation>();
        for (var source : candidates) {
            var passage = provider.passage(queryTerms, source.excerpt());
            if (passage.isPresent()) {
                int reference = citations.size() + 1;
                passages.add(new Passage(passage.get(), reference));
                citations.add(new Citation(reference, source.documentId(), source.title(), source.chunk()));
                if (citations.size() == MAX_PASSAGES) {
                    break;
                }
            }
        }
        return new Answer(passages.isEmpty() ? Status.NO_SOURCES : Status.ANSWERED,
                List.copyOf(passages), List.copyOf(citations));
    }

    public enum Status { ANSWERED, NO_SOURCES }
    public record Answer(Status status, List<Passage> answer, List<Citation> sources) {}
    public record Passage(String text, int citation) {}
    public record Citation(int number, UUID documentId, String title, int chunk) {}
}
