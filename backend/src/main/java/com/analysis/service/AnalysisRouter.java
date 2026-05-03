package com.analysis.service;

import java.util.Locale;

import org.springframework.stereotype.Service;

import com.analysis.model.dto.AnalysisRequest;
import com.analysis.model.route.AnalysisRouteDecision;
import com.analysis.model.route.AnalysisRouteType;

import lombok.extern.slf4j.Slf4j;

@Slf4j
@Service
public class AnalysisRouter {

    public AnalysisRouteDecision decide(AnalysisRequest request) {
        String query = request != null ? request.getQuery() : "";
        String normalized = query == null ? "" : query.toLowerCase(Locale.ROOT);

        AnalysisRouteDecision decision;
        if (request == null || request.getGroupId() == null) {
            decision = new AnalysisRouteDecision(
                    AnalysisRouteType.LEGACY_DATASET,
                    "No workspace groupId was provided; using legacy dataset analysis.",
                    false,
                    false,
                    true);
        } else if (isDocumentOnlyQuery(normalized)) {
            decision = new AnalysisRouteDecision(
                    AnalysisRouteType.DOCUMENT_EXPLANATION,
                    "The query explicitly asks for document-only or no-SQL answering.",
                    true,
                    true,
                    false);
        } else if (mentionsDocumentSignals(normalized)) {
            decision = new AnalysisRouteDecision(
                    AnalysisRouteType.HYBRID_ANALYSIS,
                    "The query references document, policy, rule, priority, or explanatory context.",
                    true,
                    true,
                    true);
        } else {
            decision = new AnalysisRouteDecision(
                    AnalysisRouteType.STRUCTURED_QUERY,
                    "The query can be handled as a structured workspace analysis.",
                    true,
                    false,
                    true);
        }

        log.info("[AnalysisRouter] route={} allowsSql={} requiresDocument={} reason={}",
                decision.route(), decision.allowsSqlGeneration(), decision.requiresDocumentContext(), decision.reason());
        return decision;
    }

    private boolean isDocumentOnlyQuery(String normalized) {
        boolean explicitNoSql = containsAny(normalized,
                "no sql", "without sql", "do not use sql", "don't use sql", "no table query",
                "document only", "docs only", "only from the document", "only from documents",
                "只需文档", "只看文档", "不需要sql", "不需要 sql", "不要生成sql", "不要 sql", "无需sql", "无需 sql");
        boolean documentQuestion = containsAny(normalized,
                "what does the policy say", "what does the document say", "according to the policy",
                "according to the document", "explain the policy", "explain the document",
                "how are", "how is", "文档怎么说", "政策怎么说", "规则怎么说", "解释文档", "解释规则");
        boolean structuredAction = containsAny(normalized,
                "join", "summarize", "group by", "rank", "revenue by", "count by", "compare",
                "aggregate", "trend", "top", "统计", "汇总", "分组", "排序", "对比", "趋势", "连接");
        return explicitNoSql || (documentQuestion && !structuredAction);
    }

    private boolean mentionsDocumentSignals(String normalized) {
        return containsAny(normalized,
                "according to", "based on", "workspace note", "note", "document", "docs", "policy", "rule",
                "definition", "explain", "why", "meaning", "highlight", "priority", "business context",
                "文档", "说明", "规则", "口径", "根据", "解释", "为什么", "优先", "高亮", "备注");
    }

    private boolean containsAny(String text, String... needles) {
        for (String needle : needles) {
            if (text.contains(needle)) {
                return true;
            }
        }
        return false;
    }
}
