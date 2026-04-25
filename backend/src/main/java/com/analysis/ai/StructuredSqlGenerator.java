package com.analysis.ai;

import java.util.List;

import org.springframework.stereotype.Component;

import lombok.extern.slf4j.Slf4j;

/**
 * SQL 工具生成器
 *
 * 提供数据预览和计数等通用 SQL 工具方法。
 * 主分析链路中的 SQL 由 AI Agent（AnalysisPlanGenerator）自主生成。
 */
@Slf4j
@Component
public class StructuredSqlGenerator {

    /**
     * 生成数据预览 SQL
     *
     * @param tableName 表名
     * @param limit     行数限制
     * @return 预览 SQL
     */
    public String generatePreviewSql(String tableName, int limit) {
        return String.format("SELECT * FROM %s LIMIT %d", quoteIdentifier(tableName), limit);
    }

    /**
     * 生成计数 SQL
     *
     * @param tableName 表名
     * @return 计数 SQL
     */
    public String generateCountSql(String tableName) {
        return String.format("SELECT COUNT(*) as total FROM %s", quoteIdentifier(tableName));
    }

    /**
     * 生成带条件的计数 SQL
     *
     * @param tableName  表名
     * @param conditions 过滤条件列表
     * @return 带过滤的计数 SQL
     */
    public String generateCountSql(String tableName, List<String> conditions) {
        StringBuilder sql = new StringBuilder();
        sql.append("SELECT COUNT(*) as total FROM ").append(quoteIdentifier(tableName));
        if (conditions != null && !conditions.isEmpty()) {
            sql.append(" WHERE ").append(String.join(" AND ", conditions));
        }
        return sql.toString();
    }

    /**
     * 用双引号包裹标识符，防止列名/表名与 SQL 保留字冲突
     */
    private String quoteIdentifier(String identifier) {
        if (identifier == null || identifier.isEmpty()) {
            return "\"\"";
        }
        if ("*".equals(identifier)) {
            return identifier;
        }
        return "\"" + identifier.replace("\"", "\"\"") + "\"";
    }
}
