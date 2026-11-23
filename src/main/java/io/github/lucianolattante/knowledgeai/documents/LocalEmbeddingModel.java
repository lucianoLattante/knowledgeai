package io.github.lucianolattante.knowledgeai.documents;

import java.util.ArrayList;

import org.springframework.ai.document.Document;
import org.springframework.ai.embedding.Embedding;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.ai.embedding.EmbeddingRequest;
import org.springframework.ai.embedding.EmbeddingResponse;
import org.springframework.stereotype.Component;

@Component
class LocalEmbeddingModel implements EmbeddingModel {
    static final int DIMENSIONS = 256;

    @Override
    public float[] embed(Document document) {
        return vector(document.getText());
    }

    @Override
    public EmbeddingResponse call(EmbeddingRequest request) {
        var output = new ArrayList<Embedding>();
        for (var input : request.getInstructions()) {
            output.add(new Embedding(vector(input), output.size()));
        }
        return new EmbeddingResponse(output);
    }

    @Override
    public int dimensions() {
        return DIMENSIONS;
    }

    private float[] vector(String input) {
        var counts = new float[DIMENSIONS];
        TextTerms.terms(input).forEach(term -> counts[Math.floorMod(term.hashCode(), DIMENSIONS)]++);
        return counts;
    }
}
