package com.example.Ai_Service;

import org.springframework.ai.document.Document;
import org.springframework.ai.embedding.Embedding;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.ai.embedding.EmbeddingRequest;
import org.springframework.ai.embedding.EmbeddingResponse;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * A pretend embedding model for tests: no Gemini, no network, no cost. Texts that share words get similar vectors, so an
 * in-memory vector store behaves like the real thing. It also counts its calls, because each real call costs money.
 */
public class FakeEmbeddingModel implements EmbeddingModel {

    private static final int SIZE = 512;

    public final AtomicInteger calls = new AtomicInteger();

    @Override
    public EmbeddingResponse call(EmbeddingRequest request) {
        calls.incrementAndGet();
        List<Embedding> embeddings = new ArrayList<>();
        for (int i = 0; i < request.getInstructions().size(); i++) {
            embeddings.add(new Embedding(vectorOf(request.getInstructions().get(i)), i));
        }
        return new EmbeddingResponse(embeddings);
    }

    @Override
    public float[] embed(Document document) {
        return vectorOf(document.getText());
    }

    @Override
    public int dimensions() {
        return SIZE;
    }

    private static float[] vectorOf(String text) {
        float[] vector = new float[SIZE];
        for (String word : text.toLowerCase().split("\\W+")) {
            if (!word.isBlank()) {
                vector[Math.floorMod(word.hashCode(), SIZE)] += 1;
            }
        }
        return vector;
    }
}
