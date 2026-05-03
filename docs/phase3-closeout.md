# Phase 3 Closeout

## 1. 阶段结论

Phase 3 已完成核心目标：

**系统现在可以围绕同一个 workspace query，同时组织 structured、document、semantic 三类上下文，并通过 benchmark 验证上下文是否被正确带入。**

当前 Phase 3 覆盖：

- 统一上下文模型
- workspace context assembler 接入
- document retrieval 触发 / 跳过策略
- prompt budget 和 topK 摘要
- context source marker
- benchmark report 中的 `contextSummary`
- 表 + 文档联合问题集
- 文档口径影响分析结论
- document-only 不强行生成 SQL 的分流

---

## 2. 已完成任务

### 2.1 Context Model

已新增：

- [StructuredContext.java](F:\data-analysis-platform\backend\src\main\java\com\analysis\model\context\StructuredContext.java)
- [DocumentContext.java](F:\data-analysis-platform\backend\src\main\java\com\analysis\model\context\DocumentContext.java)
- [SemanticContext.java](F:\data-analysis-platform\backend\src\main\java\com\analysis\model\context\SemanticContext.java)
- [UnifiedAnalysisContext.java](F:\data-analysis-platform\backend\src\main\java\com\analysis\model\context\UnifiedAnalysisContext.java)
- [UnifiedContextSummary.java](F:\data-analysis-platform\backend\src\main\java\com\analysis\model\context\UnifiedContextSummary.java)

对应 roadmap：

- `3.1.1` StructuredContext
- `3.1.2` DocumentContext
- `3.1.3` SemanticContext
- `3.1.4` UnifiedAnalysisContext

### 2.2 Context Assembler

[WorkspaceContextAssembler.java](F:\data-analysis-platform\backend\src\main\java\com\analysis\service\WorkspaceContextAssembler.java) 已负责组装 `UnifiedAnalysisContext`。

当前组装内容：

- structured context：workspace datasets、relations、schema prompt、relation prompt
- semantic context：workspace / dataset business context prompt
- document context：document strategy、topK、budget、included chunks、rendered prompt

对应 roadmap：

- `3.2.1` 新建上下文装配服务骨架
- `3.2.2` structured context 组装
- `3.2.3` semantic context 组装
- `3.2.4` document context 组装
- `3.2.5` 统一 prompt 载荷

### 2.3 Retrieval Strategy

当前策略：

- SQL-only / structured aggregation query 会跳过 document retrieval
- policy / rule / document / explanation query 会使用 HEAVY document strategy
- 默认轻量问题可使用 LIGHT strategy
- document context 有 topK 和 prompt char budget
- structured / document / semantic context 都有 source marker

对应 roadmap：

- `3.3.1` query 到 document retrieval 的触发规则
- `3.3.2` structured retrieval 由 focusDatasetIds / workspace 范围控制
- `3.3.3` topK 和截断策略
- `3.3.4` 每类上下文来源标记

### 2.4 联合问题验证

当前 hybrid benchmark 覆盖：

- [workspace_orders_users_note_priority.json](F:\data-analysis-platform\harness\benchmarks\hybrid\workspace_orders_users_note_priority.json)
- [workspace_orders_products_merchandising_priority.json](F:\data-analysis-platform\harness\benchmarks\hybrid\workspace_orders_products_merchandising_priority.json)
- [workspace_orders_products_revenue_policy.json](F:\data-analysis-platform\harness\benchmarks\hybrid\workspace_orders_products_revenue_policy.json)
- [workspace_revenue_policy_document_only.json](F:\data-analysis-platform\harness\benchmarks\hybrid\workspace_revenue_policy_document_only.json)

覆盖场景：

- 表 + 文档联合分析
- 文档优先级影响 summary
- 文档 policy 影响 SQL 口径
- 只需文档时不生成 SQL

对应 roadmap：

- `3.4.1` 首批“表 + 文档”联合问题集
- `3.4.2` 文档口径影响分析结论
- `3.4.3` 只需文档、不需 SQL 的场景分流

---

## 3. 验证记录

本轮 closeout 执行：

```powershell
mvn -DskipTests compile
mvn -Dtest=UnifiedAnalysisContextTest test
python -m py_compile harness\run_benchmarks.py
python harness\run_benchmarks.py --category hybrid --list-cases
python harness\run_benchmarks.py --base-url http://127.0.0.1:18080/api --category workspace --case-id workspace_traffic_orders_join_signal --request-timeout 120
python harness\run_benchmarks.py --base-url http://127.0.0.1:18080/api --category hybrid --request-timeout 180
```

结果：

- backend compile：通过
- `UnifiedAnalysisContextTest`：2 个测试通过
- harness py_compile：通过
- hybrid case list：4 个 case 可加载
- workspace smoke：1 / 1 通过
- hybrid smoke：4 / 4 通过

真实报告：

- [workspace-run-20260502-192039.json](F:\data-analysis-platform\harness\runs\workspace-run-20260502-192039.json)
- [hybrid-run-20260502-192147.json](F:\data-analysis-platform\harness\runs\hybrid-run-20260502-192147.json)

---

## 4. 已知边界

- document-only 分流目前是轻量规则识别，不是完整 intent classifier。
- document-only 摘要直接基于 retrieved chunks 拼接，后续可升级为更自然的 LLM summarizer。
- 当前 benchmark 依赖本地 groupId=1 和 stage2 数据集存在。
- 真实 agent 输出仍受模型波动影响，benchmark 使用 context summary 和关键短语降低随机性。

---

## 5. Phase 4 入口建议

优先进入 Phase 4：升级 Agent 编排系统。

建议第一批任务：

1. 把 query intent 分类从 `AnalysisService` 里的轻量规则抽成独立 service
2. 将 document-only / structured-only / hybrid analysis 做成明确 route
3. 为每条 route 记录 route decision 和 context summary
4. 让 benchmark report 展示 route decision，方便调试 agent 行为

---

## 6. Phase 4 起步记录

Phase 4 已从问题路由层开始推进：

- 新增 `AnalysisRouteType`
- 新增 `AnalysisRouteDecision`
- 新增 `AnalysisRouter`
- `AnalysisService` 开始通过 route decision 判断 document-only 分流
- `AnalysisResponse` 和 benchmark `response_excerpt` 开始输出 `routeDecision`
- 新增 `AnalysisRouterTest`

对应 roadmap：

- `4.1.1` 定义问题类型分类
- `4.1.2` 实现最小 intent router
- `4.1.3` 为 router 增加日志记录
