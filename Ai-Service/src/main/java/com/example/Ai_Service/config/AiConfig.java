package com.example.Ai_Service.config;

import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.ai.vectorstore.pgvector.PgVectorStore;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.jdbc.core.JdbcTemplate;

@Configuration
public class AiConfig {
    @Bean
    public ChatClient chatclient(ChatClient.Builder ChatClientBuilder) {
        return ChatClientBuilder.build();
    }

    @Bean
    public VectorStore vectorStore(JdbcTemplate jdbcTemplate, EmbeddingModel embeddingModel) {
        return PgVectorStore.builder(jdbcTemplate, embeddingModel)
             // since we are configure manually so we need to set these values manually here.
                .dimensions(3072)
                .distanceType(PgVectorStore.PgDistanceType.COSINE_DISTANCE)
                .initializeSchema(false)
                //resolved incidents data will be stored in this table
                .vectorTableName("resolved_incidents_vectors")
                .build();

    }


}