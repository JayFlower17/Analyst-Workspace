package com.analysis.security;

import java.util.Set;
import java.util.regex.Pattern;

import org.springframework.stereotype.Component;

import lombok.extern.slf4j.Slf4j;
import net.sf.jsqlparser.parser.CCJSqlParserUtil;
import net.sf.jsqlparser.statement.Statement;
import net.sf.jsqlparser.statement.Statements;
import net.sf.jsqlparser.statement.select.Select;

@Slf4j
@Component
public class SqlWhitelist {

    private static final Set<String> FORBIDDEN_KEYWORDS = Set.of(
            "DROP", "DELETE", "UPDATE", "INSERT", "TRUNCATE", "ALTER", "CREATE", "GRANT", "REVOKE",
            "EXEC", "EXECUTE", "XP_", "SP_", "SHUTDOWN", "BACKUP");

    private static final Pattern STRING_LITERAL_PATTERN = Pattern.compile("'(?:''|[^'])*'");
    private static final Pattern DOUBLE_QUOTED_IDENTIFIER_PATTERN = Pattern.compile("\"(?:\"\"|[^\"])*\"");
    private static final Pattern DANGEROUS_PATTERN = Pattern.compile(
            "(?i)(;\\s*DROP|;\\s*DELETE|;\\s*UPDATE|;\\s*INSERT|--.*|/\\*.*\\*/)",
            Pattern.CASE_INSENSITIVE);

    public boolean validate(String sql) {
        if (sql == null || sql.trim().isEmpty()) {
            log.warn("SQL is empty");
            return false;
        }

        String normalizedSql = stripLiteralsAndQuotedIdentifiers(sql).toUpperCase().trim();

        for (String keyword : FORBIDDEN_KEYWORDS) {
            Pattern keywordPattern = Pattern.compile("\\b" + Pattern.quote(keyword) + "\\b");
            if (keywordPattern.matcher(normalizedSql).find()) {
                log.warn("SQL contains forbidden keyword: {}", keyword);
                return false;
            }
        }

        if (DANGEROUS_PATTERN.matcher(sql).find()) {
            log.warn("SQL contains dangerous pattern");
            return false;
        }

        try {
            Statements statements = CCJSqlParserUtil.parseStatements(sql);
            if (statements.getStatements().size() != 1) {
                log.warn("Only single statement is allowed");
                return false;
            }

            Statement statement = statements.getStatements().get(0);

            if (!(statement instanceof Select)) {
                log.warn("Only SELECT statements are allowed, got: {}", statement.getClass().getSimpleName());
                return false;
            }

            return true;
        } catch (Exception e) {
            log.warn("Failed to parse SQL: {}", e.getMessage());
            return false;
        }
    }

    public String sanitize(String input) {
        if (input == null) {
            return null;
        }
        return input
                .replace("'", "''")
                .replace("\\", "\\\\")
                .replace("\0", "")
                .replace("\n", " ")
                .replace("\r", " ")
                .replace("\t", " ");
    }

    private String stripLiteralsAndQuotedIdentifiers(String sql) {
        String withoutStringLiterals = STRING_LITERAL_PATTERN.matcher(sql).replaceAll("''");
        return DOUBLE_QUOTED_IDENTIFIER_PATTERN.matcher(withoutStringLiterals).replaceAll("\"\"");
    }
}
