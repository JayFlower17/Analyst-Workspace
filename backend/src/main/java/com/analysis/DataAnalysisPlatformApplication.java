package com.analysis;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication(excludeName = {
        "org.springframework.ai.autoconfigure.vectorstore.milvus.MilvusVectorStoreAutoConfiguration",
        "org.springframework.ai.autoconfigure.vectorstore.pgvector.PgVectorStoreAutoConfiguration"
})
public class DataAnalysisPlatformApplication {

    public static void main(String[] args) {
        SpringApplication.run(DataAnalysisPlatformApplication.class, args);
    }
}
