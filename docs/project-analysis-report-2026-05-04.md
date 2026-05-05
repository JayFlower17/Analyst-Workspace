# 数据智能分析平台全面分析报告

生成日期：2026-05-04  
分析范围：根目录说明文档、`docs/` 阶段文档、后端源码、`frontend-next` 主线前端、旧 Vue 前端定位、Python 执行器、harness 评测体系与最近回归报告。

---

## 1. 执行摘要

本项目已经从早期“自然语言转 SQL + 图表展示”的数据分析工具，演进成一个以 workspace 为中心的智能分析工作台。当前核心主线是：用户在 workspace 中组织结构化数据集、业务描述、表关系、非结构化文档和历史分析结果，由后端通过路由、上下文装配、LLM 生成、SQL/Python 执行、结果校验和 artifact 持久化形成可追踪的分析闭环。

项目总体成熟度可以概括为：

- 产品方向清晰：围绕 workspace、agent 编排、artifact 沉淀持续推进。
- 后端主流程较完整：已经有路由、规划、执行器、统一上下文、统一输出、校验、风险提示和 artifact detail 回放。
- 前端主线明确：`frontend-next/` 是未来主线，`frontend/` 是迁移期 fallback。
- 工程验证已起步：`harness/` 已具备按 suite/tag/case 运行、失败归因和报告 diff 的能力。
- 仍存在明显平台化缺口：权限边界、前端大页面复杂度、CI 化、服务端分页搜索、文档引用精度、多表 Python、生产级迁移体系仍需加强。

我对项目当前状态的判断是：它已经不是 demo，而是一个处在“工程化平台雏形”阶段的智能分析系统。下一阶段最值得投入的不是继续堆新能力，而是收敛边界、拆分复杂页面、补权限与回归自动化，让已有主线稳定地长出来。

---

## 2. 项目定位与目标形态

项目 README 对当前目标的定义是“Analyst Workspace”：一个把结构化数据、业务上下文和 agent-driven analysis 放在同一个工作台里的智能分析系统。

从 `docs/task-roadmap.md` 和 Phase 1-6 closeout 来看，目标形态已经比较明确：

1. 以 workspace 统一组织资产。
2. 资产包含数据集、表关系、业务描述、文档和历史分析。
3. 用户用自然语言发起分析请求。
4. 后端构建结构化、语义和文档联合上下文。
5. Agent 生成 SQL 或 Python。
6. 系统执行、校验、总结并保存 artifact。
7. 用户可以回看、复用、管理历史分析结果。

这条路线比传统 BI 更偏“分析工作流平台”，也比简单 RAG 问答更重视可执行结果、证据来源和工程回归。

---

## 3. 仓库结构与模块职责

当前仓库主要模块如下：

| 模块 | 当前定位 | 技术栈 |
|---|---|---|
| `backend/` | 平台业务中枢、API、分析编排、元数据、artifact、认证 | Spring Boot 3.2.5, Spring AI, DuckDB, H2, Milvus |
| `frontend-next/` | 未来前端主线，承载新产品形态 | Next.js 16, React 19, TypeScript, Tailwind, shadcn 风格组件 |
| `frontend/` | 旧 Vue 前端，迁移期保留 | Vue 3, Vite, Element Plus, ECharts |
| `python-executor/` | 受限 Python 执行服务 | FastAPI, Pandas, NumPy, DuckDB |
| `harness/` | 本地 benchmark 与回归验证 | Python scripts + JSON case |
| `docs/` | 阶段设计、closeout、模块说明、路线图 | Markdown |
| `test-data/` | 示例数据集 | CSV 等 |
| `docker-compose-milvus.yml` | 向量检索依赖 | Milvus stack |

代码组织方向合理：后端是编排层，前端是体验层，Python 执行器是隔离执行层，harness 是评测层。模块边界已经形成，但后端 `DuckDBRepository` 和前端 workspace 页面仍承担过多职责。

---

## 4. 后端架构分析

### 4.1 技术栈

后端依赖显示当前核心技术为：

- Spring Boot 3.2.5
- Java 17
- Spring AI OpenAI starter 1.0.0-M3
- Spring AI Milvus vector store
- H2：用户认证等 JPA 侧数据
- DuckDB：分析数据、workspace 元数据、artifact、文档元数据
- Apache POI：Excel/DOCX 处理
- PDFBox：PDF 文本提取
- JSqlParser：SQL 白名单解析
- Spring Security + JWT

配置上，默认 `application.yml` 使用 DeepSeek chat model、DashScope embedding model、DuckDB 本地文件、Python executor `http://localhost:8000`。

### 4.2 API 边界

当前后端 controller 包含：

- `AuthController`
- `DatasetController`
- `DatasetGroupController`
- `DocumentController`
- `AnalysisController`
- `ArtifactController`
- `ChatController`
- `TestController`

主要 API 能力：

- `/api/auth/**`：登录注册
- `/api/datasets/**`：上传、列表、详情、元数据、描述编辑、删除
- `/api/groups/**`：workspace 创建、列表、详情、描述、数据集、关系管理
- `/api/documents/**`：workspace 文档上传、列表、chunk、检索、删除
- `/api/analysis/query`：统一分析入口
- `/api/analysis/preview/{datasetId}`：数据预览
- `/api/artifacts/recent`：最近 artifact
- `/api/artifacts/{id}`：artifact detail
- `/api/artifacts/{id}/archive|restore|DELETE`：artifact 管理

整体 API 已经覆盖主流程，但很多返回仍是 `Map<String,Object>` envelope，类型契约不够强。后续如果要平台化，建议逐步替换为明确 response DTO。

### 4.3 数据模型

后端 `model/entity` 当前包含：

- 用户和认证：`User`
- 数据集：`Dataset`, `ColumnMetadata`
- workspace：`DatasetGroup`, `DatasetRelation`
- 文档：`DocumentAsset`, `DocumentChunk`
- 聊天：`ChatSession`, `ChatMessage`
- 分析历史：`AnalysisHistory`, `AnalysisArtifact`

Phase 6 之后，`analysis_artifacts` 已支持：

- `artifact_schema_version`
- `analysis_report_json`
- `evidence_summary_json`
- `execution_logs_json`
- `validation_report_json`
- `risk_notices_json`
- `artifact_status`
- `archived_at`
- `deleted_at`
- `updated_at`

这说明 artifact 已经成为一等对象，但当前 `context_summary_json`、`route_decision_json`、`analysis_plan_json` 仍停留在设计阶段，没有完整持久化。

### 4.4 持久化实现

`DuckDBRepository` 是当前 DuckDB 的集中访问层，负责：

- 初始化表结构和序列
- 兼容旧库补列迁移
- 数据集、列元数据、workspace、关系、文档、chat、artifact 的 CRUD
- 执行 SQL 查询和更新

优点：

- 单一入口便于快速迭代。
- DuckDB 本地分析体验简单直接。
- 通过 `ensureColumn` 方式做了兼容式 schema migration。

风险：

- 文件超过千行，聚合了过多领域对象。
- 迁移逻辑嵌在 repository 初始化中，长期会难以追踪版本。
- 部分查询和删除仍依赖应用层保证权限与范围。
- `executeQuery` 是通用执行口，必须依赖上层 SQL 白名单严格把关。

建议后续拆成 `DatasetRepository`、`WorkspaceRepository`、`DocumentRepository`、`ArtifactRepository`，并引入轻量 migration 版本表。

---

## 5. 分析主流程

当前核心分析入口是 `AnalysisService.analyze()`。流程如下：

1. 接收 `AnalysisRequest`。
2. 使用 `AnalysisRouter` 判断 route。
3. 使用 `AnalysisPlanner` 生成结构化计划。
4. 如果有 `groupId`，进入 workspace 模式。
5. `WorkspaceSchemaService` 组装数据表 schema 和关系。
6. `WorkspaceContextAssembler` 组装统一上下文。
7. 根据 route 执行 document-only 或 agent 分析。
8. Agent 返回 SQL/Python 与摘要。
9. SQL 走 `SqlExecutor`；Python 走 `PythonExecutor`。
10. 执行日志进入 `ToolExecutionLog`。
11. `AnalysisResultValidator` 做结果一致性和证据校验。
12. `RiskNoticeBuilder` 生成风险提示。
13. `AnalysisReportBuilder` 生成统一输出。
14. `saveAnalysisArtifactSafely` 保存 artifact。

当前 route 类型：

- `STRUCTURED_QUERY`
- `DOCUMENT_EXPLANATION`
- `HYBRID_ANALYSIS`
- `LEGACY_DATASET`

当前 plan step 类型包括：

- `ROUTE_DECISION`
- `BUILD_CONTEXT`
- `RETRIEVE_DOCUMENTS`
- `GENERATE_SQL`
- `EXECUTE_SQL`
- `ANSWER_FROM_DOCUMENTS`
- `SUMMARIZE_RESULT`

这条链路是项目最有价值的部分：它把“LLM 生成 SQL”包装成了一个可观察、可验证、可沉淀的分析流程。

---

## 6. AI 与 Agent 编排

`AnalysisPlanGenerator` 使用 Spring AI `ChatClient`，通过 function calling 调用：

- `executeQuery`
- `getSchema`
- `executePython`

Agent prompt 明确限制：

- 最多 10 次 SQL 查询
- 最多 5 次 schema lookup
- 最多 3 次 Python 执行
- 优先单条 SQL
- 只有复杂数据科学任务才用 Python
- 最终输出严格 JSON：`chartType`、`summaryText`、`generatedCodeOrSql`

为了增强鲁棒性，解析 AI 返回时有多级策略：

1. 直接 JSON 解析
2. 提取 Markdown JSON 代码块
3. 从首尾花括号截取
4. 失败时把文本截断作为 summary

优点：

- 对 LLM 输出格式波动有防护。
- 已经把工具调用次数和执行边界写入 prompt。
- workspace 模式能注入业务上下文、schema、relation、document context。

不足：

- router/planner 仍是规则型，不是状态机或强约束 LLM planner。
- Agent 最终输出仍依赖自由文本 JSON，缺少 schema-guided decoding。
- summary 校验只做浅层矛盾识别，不能保证数值逐项一致。
- 多表 Python 目前只是产品提示级支持，执行器仍以单表 DataFrame 为主。

---

## 7. 统一上下文与文档检索

项目已经实现 `UnifiedAnalysisContext`，包含：

- `StructuredContext`：workspace schema、datasets、relations
- `DocumentContext`：检索到的 document chunks、策略、预算
- `SemanticContext`：workspace 描述、表描述等业务语义

`WorkspaceContextAssembler` 会根据 query 做文档需求分类：

- `SKIP`：明显结构化聚合问题，跳过文档检索
- `LIGHT`：默认轻量检索
- `HEAVY`：提到 document、policy、rule、definition、解释、口径等时加强检索

文档检索策略：

1. 若 Milvus / VectorStore 可用，先做向量检索，并按 `groupId` 过滤。
2. 如果向量检索失败或无结果，回退到词法搜索。
3. 对结果去重、合并相邻 chunk、rerank。
4. 根据文档角色和 query intent 调整优先级。

文档处理支持：

- PDF
- DOCX
- TXT
- Markdown

chunk 默认最大约 1200 字符，prompt 预算为 LIGHT 1600 字符、HEAVY 2600 字符。

判断：文档接入不是玩具级实现，已经有检索回退、rerank、workspace 隔离和 prompt 预算。但引用质量仍偏“存在性校验”，还未达到严肃 citation 或证据强度评分。

---

## 8. SQL 与 Python 执行安全

### 8.1 SQL 安全

SQL 由 `DuckDbSqlExecutor` 统一执行，执行前经过 `SqlWhitelist`：

- 禁止 `DROP`、`DELETE`、`UPDATE`、`INSERT`、`TRUNCATE`、`ALTER`、`CREATE` 等关键词。
- 拒绝危险 pattern，如分号后接 DDL/DML、注释等。
- 使用 JSqlParser 解析。
- 只允许单条语句。
- 只允许 `SELECT`。

这是合理的基础防线。需要注意：

- 只靠关键词和 parser 不等于完整数据访问控制。
- 表级/列级访问范围仍需要结合 workspace membership 做校验。
- LLM 生成 SQL 仍可能访问 workspace 外表，如果 schema prompt 或安全层没限制住，会有越界风险。

### 8.2 Python 执行安全

`python-executor` 使用 FastAPI 暴露 `/execute`，当前只支持 `task_type=python`。

安全机制：

- 代码先经过白名单校验。
- 禁止危险 import：`os`、`subprocess`、`socket`、`pickle`、`ctypes` 等。
- 限制允许 import：`pandas`、`numpy`、`scipy`、`sklearn`、`math` 等。
- 禁止 `eval`、`exec`、`compile`、`open`、`__import__`、`getattr` 等。
- 禁止 dunder 访问。
- 禁止文件和网络操作 pattern。
- 子进程执行，30 秒超时。
- DuckDB read_only 连接。
- 单表读取最多 50000 行。

优点是安全意识到位，并且用了进程隔离和超时。风险是 Python 沙箱用正则白名单仍不是强安全边界；如果未来面向不可信用户，应考虑容器级隔离、资源配额、seccomp/AppArmor 或独立执行环境。

---

## 9. Artifact 能力

Phase 6 已把 artifact 从“最近一次结果”推进到“可回放分析资产”：

用户可在前端：

- 查看最近历史分析。
- 打开 artifact detail。
- 回看 query、summary、预览数据、证据来源、执行日志、校验结果、风险提示和 SQL/Python。
- 将历史 artifact 加载回当前结果区。
- 搜索、筛选、刷新。
- 归档、恢复、软删除。

后端支持：

- `GET /api/artifacts/recent`
- `GET /api/artifacts/{id}`
- `PATCH /api/artifacts/{id}/archive`
- `PATCH /api/artifacts/{id}/restore`
- `DELETE /api/artifacts/{id}`

当前设计的关键取舍：

- `analysis_report_json` 保存 trimmed report，不重复保存完整结果行。
- 结果预览仍由 `result_preview_json` 承担。
- legacy artifact 兼容展示，但 `reportAvailable=false`。

主要缺口：

- 服务端搜索、分页、排序还不完整。
- workspace membership / ownership 权限说明和测试不足。
- route decision、plan、context summary 尚未作为一等字段持久化。

---

## 10. 前端主线分析

### 10.1 技术栈与结构

`frontend-next` 使用：

- Next.js 16.2.4
- React 19.2.4
- TypeScript 5
- Tailwind 4
- lucide-react
- shadcn/base-ui 风格组件
- sonner toast
- Recharts

目录结构以 App Router 为主：

- `app/login`
- `app/register`
- `app/(dashboard)/chat`
- `app/(dashboard)/datasets`
- `app/(dashboard)/workspace`
- `app/(dashboard)/workplace`
- `components/layout/app-shell.tsx`
- `lib/api/client.ts`
- `lib/types.ts`

API client 已覆盖 chat、workspace、dataset、auth、document、artifact。它会从 `NEXT_PUBLIC_API_BASE` 或 localhost 推断后端地址，并在 localStorage 中读取 token。

### 10.2 产品实现状态

当前 `workspace/page.tsx` 是主产品页面，已经承担：

- workspace 列表和创建
- workspace 描述编辑
- 数据集上传和预览
- 关系管理和自动识别入口
- 文档上传、chunk 查看、检索
- 分析请求
- 分析结果展示
- 证据来源展示
- 执行过程展示
- artifact 列表、筛选、详情、归档、恢复、软删除

这说明前端工作区闭环很完整，但同一个文件已经达到约 1878 行，是明显的维护风险。

### 10.3 前端主要风险

- `workspace/page.tsx` 过大，状态和 UI 混杂。
- `workplace` 与 `workspace` 仍存在命名遗留。
- 前端 artifact 搜索和 schema 筛选是客户端过滤，数据量上来后会吃力。
- 认证恢复依赖 localStorage，dashboard layout 本身不做强保护，更多保护在 AppShell/API 层。
- 缺少前端自动化交互测试。

建议把 workspace 页面拆成：

- `WorkspaceSelector`
- `WorkspaceAssetPanel`
- `DatasetManager`
- `RelationManager`
- `DocumentManager`
- `AnalysisComposer`
- `AnalysisResultPanel`
- `ArtifactHistoryPanel`
- `ArtifactDetailDialog`

---

## 11. Harness 与质量保障

`harness/` 已经从简单脚本演进为本地回归工具。当前能力：

- JSON case 管理
- structured / workspace / hybrid 分类
- tag 筛选
- suite 运行
- setup documents
- cleanup
- report 输出
- failure_stage 归因
- report diff

最新 `phase4-regression-run-20260503-215029.json` 显示：

- total：5
- passed：5
- failed：0
- workspace：1 / 1 通过
- hybrid：4 / 4 通过
- document-retrieval：4 / 4 通过
- policy：2 / 2 通过
- failure stage：无失败

这些 case 覆盖：

- workspace SQL join
- hybrid 表 + 文档 policy
- hybrid 表 + merchandising note
- hybrid 表 + workspace note
- document-only policy answer

这套 harness 对 Phase 4/6 的核心链路很有价值。主要限制：

- 依赖本地服务、固定 groupId、固定样例数据。
- 尚未接入 CI。
- case 数量仍偏少。
- 对数值正确性、citation coverage、权限边界覆盖不足。

---

## 12. 当前项目优点

1. 方向有连续性  
   Phase 文档非常完整，从 Phase 1 到 Phase 6 有清晰的设计、closeout 和验证记录。

2. 核心架构已经成形  
   路由、计划、执行、校验、输出、持久化不是散点功能，而是一条分析工作流。

3. Workspace 抽象正确  
   多表、文档、关系、描述、artifact 都围绕 workspace 收束，符合长期平台化方向。

4. 工程验证意识较强  
   有 harness、suite、report diff、failure stage，这是很多 AI 应用早期缺失的东西。

5. 安全边界有初步设计  
   SQL 白名单、Python 白名单、超时、read_only、JWT 都已具备。

6. Artifact 思路有前瞻性  
   把分析结果变成可回看资产，是从“聊天式分析”走向“工作台”的关键一步。

---

## 13. 主要风险与问题

### 高优先级

1. 权限边界不足  
   当前 API 需要 JWT，但 workspace、dataset、artifact 的 ownership/membership 校验仍不够明确。多用户场景下，这是最高风险。

2. 前端 workspace 页面过大  
   1878 行单页会让后续修改越来越危险，状态、UI、业务操作容易互相牵连。

3. DuckDBRepository 过重  
   单 repository 聚合所有元数据和执行能力，短期方便，长期会限制可维护性和测试粒度。

4. AI 输出可信度仍有限  
   summary 与 SQL 结果一致性只做浅层检查。真实业务分析需要更强的数值引用、字段口径和证据链校验。

5. Python 沙箱不是生产级隔离  
   当前白名单 + 子进程适合本地受控环境，不建议直接暴露给不可信用户。

### 中优先级

1. `context_summary_json`、`route_decision_json`、`analysis_plan_json` 尚未持久化。
2. Artifact recent API 缺少服务端分页、搜索、排序。
3. relation auto-detect 目前只是入口打通，尚未真正规则识别。
4. 多表 Python 仍未完成。
5. 文档检索 citation 粒度不够细。
6. harness 未接 CI。
7. 旧 `frontend/` 仍保留，长期需要明确冻结或退役策略。

### 低优先级

1. API envelope 类型不统一。
2. 中英文文案混用。
3. 默认配置里存在 dummy key 和本地端口假设，生产 profile 需要更严格。
4. `workplace` 命名仍有历史残留。

---

## 14. 建议路线

### 近期 1-2 周

1. 补 workspace / dataset / artifact 权限校验和测试。
2. 拆分 `frontend-next/src/app/(dashboard)/workspace/page.tsx`。
3. 为 artifact 增加服务端分页、搜索、排序。
4. 持久化 route decision、analysis plan、context summary。
5. 增加 Phase 6 artifact 专属 harness suite。

### 中期 1 个月

1. 拆分 `DuckDBRepository`。
2. 建立 DuckDB migration 版本表。
3. 增强 relation auto-detect。
4. 为文档引用增加 citation id、chunk range 和 coverage 检查。
5. 把 harness 接入 CI 或至少接入固定 release checklist。

### 长期

1. 用更强的 planner / workflow state machine 替代纯规则 planner。
2. 为 Python 执行器引入容器级隔离。
3. 建立多租户权限模型。
4. 建立 artifact 资产中心页面，而不只放在 workspace 页面中。
5. 引入更严肃的分析正确性评测，包括数值对齐、口径一致性和引用有效性。

---

## 15. 综合结论

这个项目最有价值的地方，不是“能让 AI 写 SQL”，而是它已经开始把 AI 分析产品必须具备的几个长期能力串起来：上下文、执行、证据、校验、风险、历史沉淀和回归评测。

当前最应该保护的主线是：

> workspace 资产组织 + 统一分析工作流 + artifact 回放 + harness 回归。

只要继续沿着这条线，把权限、拆分、持久化、自动化验证补上，这个项目具备成长为一个真正可用的智能分析工作台的基础。相反，如果下一阶段过早扩展更多模型、更多工具或更多页面，而不先收敛工程边界，复杂度会很快超过当前代码结构的承载能力。

