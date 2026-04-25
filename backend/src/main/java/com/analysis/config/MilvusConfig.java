package com.analysis.config;

import io.milvus.client.MilvusServiceClient;
import io.milvus.param.ConnectParam;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.ai.embedding.TokenCountBatchingStrategy;
import org.springframework.ai.vectorstore.MilvusVectorStore;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class MilvusConfig {

        @Bean
        public MilvusServiceClient milvusServiceClient() {
                return new MilvusServiceClient(
                                ConnectParam.newBuilder()
                                                .withHost("localhost")
                                                .withPort(19530)
                                                .build());
        }

        @Bean
        public MilvusVectorStore milvusVectorStore(MilvusServiceClient milvusClient, EmbeddingModel embeddingModel) {

                MilvusVectorStore.MilvusVectorStoreConfig config = MilvusVectorStore.MilvusVectorStoreConfig.builder()
                                .withCollectionName("analysis_knowledge_final")
                                .withEmbeddingDimension(1024) // 强制指定 1024 维
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
