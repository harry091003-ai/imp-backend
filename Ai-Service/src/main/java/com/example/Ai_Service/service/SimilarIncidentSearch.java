package com.example.Ai_Service.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.document.Document;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.ai.vectorstore.filter.Filter;
import org.springframework.ai.vectorstore.filter.FilterExpressionBuilder;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.List;

// AI plan phase 1: the search for similar past incidents, in ONE place (it was copied in 2 services).
//
//   same category + service, similarity >= threshold
//        | nothing found
//        v
//   any category + service, the SAME threshold      (the filter is relaxed, the quality bar is not)
//
// The threshold used to go UP from 0.6 to 0.7 on the fallback, so the fallback found less, not more.
// 0.6 is still a guess until it is measured on real incidents (phase 4), so it is a setting.
@Component
public class SimilarIncidentSearch {

    private static final Logger log = LoggerFactory.getLogger(SimilarIncidentSearch.class);

    public record Result(List<Document> documents, boolean sameCategoryAndService) {
    }

    private final VectorStore vectorStore;
    private final AiCallGuard guard;
    private final double similarityThreshold;

    public SimilarIncidentSearch(VectorStore vectorStore, AiCallGuard guard,
                                 @Value("${ai.search.similarity-threshold:0.6}") double similarityThreshold) {
        this.vectorStore = vectorStore;
        this.guard = guard;
        this.similarityThreshold = similarityThreshold;
    }

    public Result search(String title, String description, String category, String serviceName, int topK) {
        String queryText = title + " " + description;
        Filter.Expression filter = buildFilter(category, serviceName);

        if (filter != null) {
            List<Document> filtered = find(queryText, filter, topK);
            log.info("Filtered search found {} documents", filtered.size());
            if (!filtered.isEmpty()) {
                return new Result(filtered, true);
            }
        }
        // No filter was asked for, or it found nothing. Each search is a paid embedding call, so this only runs when needed.
        List<Document> unfiltered = find(queryText, null, topK);
        log.info("Unfiltered search found {} documents", unfiltered.size());
        return new Result(unfiltered, false);
    }

    private List<Document> find(String queryText, Filter.Expression filter, int topK) {
        SearchRequest.Builder request = SearchRequest.builder()
                .query(queryText)
                .topK(topK)
                .similarityThreshold(similarityThreshold);
        if (filter != null) {
            request.filterExpression(filter);
        }
        return guard.call("similar incident search", () -> vectorStore.similaritySearch(request.build()));
    }

    private static Filter.Expression buildFilter(String category, String service) {
        FilterExpressionBuilder builder = new FilterExpressionBuilder();
        if (category != null && service != null) {
            return builder.and(builder.eq("category", category), builder.eq("service", service)).build();
        } else if (category != null) {
            return builder.eq("category", category).build();
        } else if (service != null) {
            return builder.eq("service", service).build();
        }
        return null;
    }
}
