package com.analysis.service;

import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import com.analysis.model.entity.ColumnMetadata;
import com.analysis.persistence.WorkspaceCatalogStore;
import com.analysis.repository.DuckDBRepository;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Service
@RequiredArgsConstructor
// 提取DuckDB中的数据，并把这些信息转化为大模型LLM容易理解的格式，并存储到向量数据库中供后续检索
public class MetadataService {

    private final DuckDBRepository duckDBRepository;
    private final WorkspaceCatalogStore workspaceCatalogStore;
    private final ObjectProvider<org.springframework.ai.vectorstore.VectorStore> vectorStoreProvider;
    @Value("${app.vector-store.enabled:false}")
    private boolean vectorStoreEnabled;

    /**
     * 将表的元数据转化为自然语言文档，存入向量数据库以供 RAG 检索
     */
    // 向量数据库的工作原理是把文本转成向量，通过语义相似度来检索
    public void saveMetadataToVectorStore(String tableName, Long datasetId, List<ColumnMetadata> columns) {
        if (!vectorStoreEnabled) {
            log.info("Vector Store is disabled by config; skipping metadata indexing for table: {}", tableName);
            return;
        }
        org.springframework.ai.vectorstore.VectorStore vectorStore = vectorStoreProvider.getIfAvailable();
        if (vectorStore == null) {
            log.info("Vector Store is disabled; skipping metadata indexing for table: {}", tableName);
            return;
        }

        List<org.springframework.ai.document.Document> documents = new ArrayList<>();

        for (ColumnMetadata col : columns) {
            // 组装自然语言描述。因为向量模型更擅长理解连贯的自然语言而不是json
            String description = String.format("在真实的业务表 '%s' 中，存在一个字段名为 '%s'。它的数据类型是 '%s'。可不可以为空：%s。",
                    tableName, col.getColumnName(), col.getDataType(),
                    col.getNullable() != null && col.getNullable() ? "是" : "否");

            if (col.getSampleValues() != null && !col.getSampleValues().isEmpty()) {
                description += String.format(" 该字段的一些抽样数据长这样: %s。", col.getSampleValues());
            }
            if (col.getMinValue() != null && col.getMaxValue() != null) {
                description += String.format(" 数据的取值范围大约是从 %s 到 %s。", col.getMinValue(), col.getMaxValue());
            }

            // 将 datasetId 作为元数据附在向量里，方便未来做隔离检索
            Map<String, Object> metadata = Map.of(
                    "datasetId", datasetId,
                    "tableName", tableName,
                    "columnName", col.getColumnName(),
                    "type", "schema_definition");

            documents.add(new org.springframework.ai.document.Document(description, metadata));
        }
        // document是ai处理数据的最小单位，可以理解为content + metadata，如果太长的话还会切分，防止超过大模型token的限制

        if (!documents.isEmpty()) {
            log.info("Saving {} schema documents to Vector Store for table: {}", documents.size(), tableName);
            vectorStore.add(documents);// 把向量存入向量数据库，这里就是调用embedding大模型，将description字符串转换为一堆密集浮点数
            // 然后将这段文本、metadata 字典以及浮点向量写入 pgvector 中
        }
    }

    // 拿到duckdb中某张真实表的特征信息，不同于上传数据生成元数据(服务于duckdb)，这里的元数据是业务级别的统计元数据，用于大模型理解数据
    public List<ColumnMetadata> extractColumnMetadata(String tableName, Long datasetId) throws SQLException {
        List<ColumnMetadata> columns = new ArrayList<>();

        String schemaSql = String.format("DESCRIBE %s", tableName);
        List<Map<String, Object>> schemaResult = duckDBRepository.executeQuery(schemaSql);

        for (Map<String, Object> row : schemaResult) {
            String columnName = row.get("column_name").toString();
            String dataType = row.get("column_type").toString();
            String nullable = row.getOrDefault("null", "YES").toString();
            // 三个关键属性，列名、数据类型、是否允许为空
            ColumnMetadata col = new ColumnMetadata();
            col.setDatasetId(datasetId);
            col.setColumnName(columnName);
            col.setDataType(dataType);
            col.setNullable("YES".equalsIgnoreCase(nullable));

            try {
                String statsSql = String.format(
                        "SELECT COUNT(DISTINCT \"%s\") as distinct_count, " +
                                "MIN(\"%s\")::VARCHAR as min_val, " +
                                "MAX(\"%s\")::VARCHAR as max_val " +
                                "FROM %s",
                        columnName, columnName, columnName, tableName);
                List<Map<String, Object>> statsResult = duckDBRepository.executeQuery(statsSql);
                if (!statsResult.isEmpty()) {
                    Map<String, Object> stats = statsResult.get(0);
                    col.setDistinctCount(((Number) stats.get("distinct_count")).longValue());
                    col.setMinValue(stats.get("min_val") != null ? stats.get("min_val").toString() : null);
                    col.setMaxValue(stats.get("max_val") != null ? stats.get("max_val").toString() : null);
                }
                // 算出列中不重复的值，算出最大最小值，并强制转换成字符串
                String sampleSql = String.format(
                        "SELECT DISTINCT \"%s\"::VARCHAR as val FROM %s WHERE \"%s\" IS NOT NULL LIMIT 5",
                        columnName, tableName, columnName);
                // 过滤空值，去重抓取5条真实数据作为样本
                List<Map<String, Object>> sampleResult = duckDBRepository.executeQuery(sampleSql);
                List<String> samples = new ArrayList<>();
                for (Map<String, Object> sample : sampleResult) {
                    if (sample.get("val") != null) {
                        samples.add(sample.get("val").toString());
                    }
                }
                col.setSampleValues(String.join(", ", samples));

            } catch (Exception e) {
                log.warn("Failed to extract stats for column {}: {}", columnName, e.getMessage());
            }

            columns.add(col);
        }

        return columns;
    }

    // 这一部分的核心作用是将指定数据集的表字段信息，拼接城一个标准的Markdown格式的表格字符串，这个字符串会直接注入LLM的prompt中
    // 这种适用于表格内容比较少的情况，如果表格内容太多，会超过大模型的token限制
    public String generateMetadataPrompt(Long datasetId) throws SQLException {
        List<ColumnMetadata> columns = workspaceCatalogStore.findColumnsByDatasetId(datasetId);

        StringBuilder sb = new StringBuilder();
        sb.append("表结构信息:\n");
        sb.append("| 列名 | 类型 | 唯一值数 | 最小值 | 最大值 | 示例 |\n");
        sb.append("|------|------|----------|--------|--------|------|\n");

        for (ColumnMetadata col : columns) {
            sb.append(String.format("| %s | %s | %d | %s | %s | %s |\n",
                    col.getColumnName(),
                    col.getDataType(),
                    col.getDistinctCount(),
                    col.getMinValue() != null ? col.getMinValue() : "N/A",
                    col.getMaxValue() != null ? col.getMaxValue() : "N/A",
                    col.getSampleValues() != null ? col.getSampleValues() : "N/A"));
        }

        return sb.toString();
    }
}
