package com.analysis.config;

import io.milvus.client.MilvusServiceClient;
import io.milvus.param.ConnectParam;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.ai.embedding.TokenCountBatchingStrategy;
import org.springframework.ai.vectorstore.MilvusVectorStore;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Primary;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
@ConditionalOnProperty(prefix = "app.vector-store", name = "enabled", havingValue = "true", matchIfMissing = true)
public class MilvusConfig {

        @Value("${app.vector-store.host:localhost}")
        private String host;

        @Value("${app.vector-store.port:19530}")
        private int port;

        @Value("${app.vector-store.collection-name:analysis_knowledge_final}")
        private String collectionName;

        @Value("${app.vector-store.embedding-dimension:1024}")
        private int embeddingDimension;

        @Bean
        public MilvusServiceClient milvusServiceClient() {
                return new MilvusServiceClient(
                                ConnectParam.newBuilder()
                                                .withHost(host)
                                                .withPort(port)
                                                .build());
        }

        @Primary
        @Bean
        public MilvusVectorStore milvusVectorStore(MilvusServiceClient milvusClient, EmbeddingModel embeddingModel) {

                MilvusVectorStore.MilvusVectorStoreConfig config = MilvusVectorStore.MilvusVectorStoreConfig.builder()
                                .withCollectionName(collectionName)
                                .withEmbeddingDimension(embeddingDimension)
                                .build();

                return new MilvusVectorStore(
                                milvusClient,
                                embeddingModel,
                                config,
                                true,
                                new TokenCountBatchingStrategy() // 🌟 新增的第 5 个参数在这里！
                );
        }
}
