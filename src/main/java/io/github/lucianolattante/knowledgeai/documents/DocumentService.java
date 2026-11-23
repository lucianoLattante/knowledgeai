package io.github.lucianolattante.knowledgeai.documents;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;

import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import static io.github.lucianolattante.knowledgeai.documents.DocumentException.Reason.*;

@Service
public class DocumentService {
    private static final int CHUNK_CODE_POINTS = 700;
    private final EmbeddingModel embeddings;
    private final DocumentStore store;

    DocumentService(EmbeddingModel embeddings, DocumentStore store) {
        this.embeddings = embeddings;
        this.store = store;
    }

    @Transactional
    public IndexedDocument ingest(Identity identity, String title, String text) {
        String trimmedTitle = title.strip();
        if (trimmedTitle.isEmpty() || trimmedTitle.length() > 120 || trimmedTitle.indexOf(0) >= 0) {
            throw new DocumentException(INVALID_TITLE);
        }
        if (text.indexOf(0) >= 0) {
            throw new DocumentException(INVALID_UTF8);
        }
        var chunks = new ArrayList<IndexedChunk>();
        for (String chunk : split(text)) {
            var vector = embeddings.embed(chunk);
            if (nonzero(vector)) {
                chunks.add(new IndexedChunk(chunks.size() + 1, chunk, Arrays.toString(vector)));
            }
        }
        if (chunks.isEmpty()) {
            throw new DocumentException(EMPTY_TEXT);
        }

        UUID id = UUID.randomUUID();
        store.insertDocument(identity, id, trimmedTitle);
        store.insertChunks(id, chunks);
        return new IndexedDocument(id, trimmedTitle, chunks.size());
    }

    public List<Source> search(Identity identity, String query, int limit) {
        if (query.isBlank() || query.length() > 256 || limit < 1 || limit > 10) {
            throw new DocumentException(INVALID_QUERY);
        }
        var vector = embeddings.embed(query);
        if (!nonzero(vector)) {
            throw new DocumentException(INVALID_QUERY);
        }
        return store.search(identity, Arrays.toString(vector), limit).stream()
                .filter(hit -> identity.equals(hit.identity()))
                .map(DocumentStore.OwnedSource::source)
                .toList();
    }

    @Transactional
    public void delete(Identity identity, UUID documentId) {
        if (store.delete(identity, documentId) == 0) {
            throw new DocumentException(DOCUMENT_NOT_FOUND);
        }
    }

    private List<String> split(String input) {
        var chunks = new ArrayList<String>();
        int start = 0;
        while (start < input.length()) {
            int remaining = input.codePointCount(start, input.length());
            int end = input.offsetByCodePoints(start, Math.min(remaining, CHUNK_CODE_POINTS));
            if (end < input.length()) {
                int cut = end;
                while (cut > start && !Character.isWhitespace(input.codePointBefore(cut))) {
                    cut -= Character.charCount(input.codePointBefore(cut));
                }
                if (cut > start && !input.substring(start, cut).isBlank()) {
                    end = cut;
                }
            }
            var chunk = input.substring(start, end).strip();
            if (!chunk.isEmpty()) {
                chunks.add(chunk);
            }
            start = end;
        }
        return chunks;
    }

    private boolean nonzero(float[] vector) {
        for (float value : vector) {
            if (value != 0) {
                return true;
            }
        }
        return false;
    }

    public record Identity(String tenant, String username) {}
    public record IndexedDocument(UUID id, String title, int chunks) {}
    public record Source(UUID documentId, String title, int chunk, String excerpt, double score) {}
    record IndexedChunk(int index, String text, String vector) {}
}
