package com.analysis;

import org.junit.jupiter.api.Test;
import org.springframework.ai.document.Document;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.ai.embedding.EmbeddingResponse;
import org.springframework.ai.embedding.EmbeddingRequest;
import org.springframework.ai.embedding.Embedding;
import org.springframework.ai.embedding.EmbeddingOptions;
import org.springframework.ai.vectorstore.MilvusVectorStore;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;

import java.util.List;
import java.util.ArrayList;

import static org.junit.jupiter.api.Assertions.assertNotNull;

import com.analysis.config.MilvusConfig;
import org.springframework.context.annotation.Import;

@SpringBootTest
@Import(MilvusConfig.class)
public class MilvusConnectionTest {

    @Autowired
    private org.springframework.ai.vectorstore.VectorStore vectorStore;

    @Test
    public void testMilvusConnection() {
        assertNotNull(vectorStore, "VectorStore should not be null");

        // Create a simple document
        Document document = new Document("Hello World - Testing Milvus connection");

        // Add to Milvus (This will now use the mock EmbeddingModel)
        vectorStore.add(List.of(document));

        // Perform a similarity search
        List<Document> results = vectorStore.similaritySearch("Testing connection");

        assertNotNull(results);
        System.out.println("Milvus connection successful! Found: " + results.size() + " documents");
    }
}
