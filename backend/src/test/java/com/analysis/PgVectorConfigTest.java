package com.analysis;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

import org.junit.jupiter.api.Test;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.ai.vectorstore.PgVectorStore;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.jdbc.core.JdbcTemplate;

import com.analysis.config.PgVectorConfig;

class PgVectorConfigTest {

    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
            .withUserConfiguration(PgVectorConfig.class)
            .withBean(JdbcTemplate.class, () -> mock(JdbcTemplate.class))
            .withBean(EmbeddingModel.class, () -> mock(EmbeddingModel.class));

    @Test
    void createsPgVectorStoreOnlyWhenExplicitlyEnabled() {
        contextRunner
                .withPropertyValues(
                        "app.vector-store.enabled=true",
                        "app.vector-store.provider=pgvector")
                .run(context -> {
                    assertThat(context).hasSingleBean(PgVectorStore.class);
                    assertThat(context).hasSingleBean(VectorStore.class);
                });
    }

    @Test
    void doesNotCreateVectorStoreWhenDisabled() {
        contextRunner
                .withPropertyValues(
                        "app.vector-store.enabled=false",
                        "app.vector-store.provider=pgvector")
                .run(context -> assertThat(context).doesNotHaveBean(VectorStore.class));
    }
}
