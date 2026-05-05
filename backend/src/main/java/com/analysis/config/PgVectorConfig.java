package com.analysis.config;

import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.ai.vectorstore.PgVectorStore;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnExpression;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.jdbc.core.JdbcTemplate;

@Configuration
@ConditionalOnExpression("'${app.vector-store.enabled:false}' == 'true' && '${app.vector-store.provider:pgvector}' == 'pgvector'")
public class PgVectorConfig {

    @Value("${spring.ai.vectorstore.pgvector.dimensions:1024}")
    private int dimensions;

    @Value("${spring.ai.vectorstore.pgvector.initialize-schema:false}")
    private boolean initializeSchema;

    @Value("${spring.ai.vectorstore.pgvector.schema-validation:false}")
    private boolean schemaValidation;

    @Value("${spring.ai.vectorstore.pgvector.table-name:vector_store}")
    private String tableName;

    @Primary
    @Bean
    public PgVectorStore pgVectorStore(JdbcTemplate jdbcTemplate, EmbeddingModel embeddingModel) {
        return new PgVectorStore.Builder(jdbcTemplate, embeddingModel)
                .withVectorTableName(tableName)
                .withDimensions(dimensions)
                .withInitializeSchema(initializeSchema)
                .withVectorTableValidationsEnabled(schemaValidation)
                .withDistanceType(PgVectorStore.PgDistanceType.COSINE_DISTANCE)
                .withIndexType(PgVectorStore.PgIndexType.HNSW)
                .build();
    }
}
