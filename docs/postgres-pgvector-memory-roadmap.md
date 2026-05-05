# Analyst Workspace 数据后端与长期记忆优化路线

本文档用于交接后续架构优化任务。核心目标不是堆叠技术栈，而是拆清系统职责：使用 PostgreSQL 承担业务主库职责，释放 DuckDB 只做数据分析执行；使用 pgvector 替换 Milvus，降低本地部署复杂度，并让向量检索与业务元数据天然对齐；在此基础上，把 artifact 沉淀升级为可检索、可治理、可进入上下文的长期分析记忆。

## 1. 总体判断

当前系统已经具备 workspace、数据集、文档、分析执行、artifact 沉淀和前端工作台等基础能力。但现阶段 DuckDB 承担了两类职责：一类是分析计算，另一类是系统业务数据持久化。前者是 DuckDB 擅长的 OLAP 场景，后者则更适合 PostgreSQL 这类事务型数据库。

后续优化方向应收敛为：

```text
PostgreSQL：系统业务主数据库
DuckDB：本地分析执行引擎
pgvector：RAG / memory / schema / document 的语义向量索引
文件系统：原始上传文件与大文件存储
```

也就是说，PostgreSQL 的加入是为了“解放 DuckDB”，让 DuckDB 回到最适合它的角色：分析执行层。

## 2. 目标架构

```text
用户请求
  -> 前端 Chat / Workspace / Warehouse
  -> Spring Boot 后端
  -> PostgreSQL 读取 workspace、dataset、document、artifact、memory 元数据
  -> pgvector 检索 schema hints、document chunks、artifact memories
  -> WorkspaceContextAssembler 组装统一上下文
  -> AI Agent 生成 SQL / Python / 分析计划
  -> DuckDB 执行结构化数据分析
  -> Python Executor 执行受限 Python 分析
  -> AnalysisReport / Validation / RiskNotice
  -> Artifact 写入 PostgreSQL
  -> 从 Artifact 抽取 Memory
  -> Memory 写入 PostgreSQL + pgvector
```

最终职责划分如下：

| 模块 | 推荐存储 | 说明 |
| --- | --- | --- |
| 用户、登录、会话 | PostgreSQL | 事务型业务数据 |
| workspace、dataset、document、artifact、memory、trace | PostgreSQL | 系统核心元数据 |
| 文档 chunk 向量 | PostgreSQL + pgvector | 文档语义检索 |
| schema / 字段语义向量 | PostgreSQL + pgvector | 字段解释、表关系、业务语义检索 |
| artifact memory 向量 | PostgreSQL + pgvector | 历史分析结论召回 |
| CSV / Excel 导入后的分析表 | DuckDB | 高性能本地 OLAP 分析 |
| 临时分析表、SQL 执行结果 | DuckDB | 分析执行过程数据 |
| 原始上传文件 | 文件系统 | 原文件保留与追溯 |

## 3. 为什么用 PostgreSQL

PostgreSQL 应该接管系统业务主库职责，原因包括：

1. 业务对象会越来越复杂  
   workspace、dataset、document、artifact、memory、context trace、权限、状态流都属于长期系统状态，适合事务数据库管理。

2. DuckDB 不适合作为长期业务主库  
   DuckDB 适合本地分析、批量查询和 OLAP，不适合承担复杂权限、分页、状态管理、审计、并发写入和长期关系维护。

3. 后续 memory 和 artifact 需要更强关系建模  
   artifact 与 workspace、dataset、document、memory、trace 之间存在复杂关系，用 PostgreSQL 更自然。

4. pgvector 可以直接复用 PostgreSQL 元数据过滤  
   向量检索时可以按 `workspace_id`、`dataset_id`、`document_id`、`memory_status` 等字段过滤，不需要在 Milvus 和业务库之间做额外同步。

## 4. 为什么用 pgvector 替换 Milvus

当前项目是本地应用和毕业设计项目，重点是架构清晰、部署简单、链路可解释，而不是海量向量集群性能。因此 pgvector 更合适。

pgvector 的优势：

1. 部署简单  
   PostgreSQL 一个服务即可，不需要 Milvus、etcd、minio 等组件。

2. 元数据过滤方便  
   向量和业务字段可以放在同一张表或关联表中，按 workspace、dataset、document、memory 类型过滤更直接。

3. 事务一致性更好  
   document chunk、artifact memory 和 embedding 可以在同一个数据库体系内维护。

4. 更适合当前规模  
   当前项目的向量数量主要来自文档 chunk、字段描述、artifact memory，pgvector 足够支撑。

Milvus 可以作为未来高并发、大规模向量检索场景的扩展方向，但不建议现阶段继续作为默认依赖。

## 5. 数据迁移与后端重构步骤

### 5.1 引入 PostgreSQL Profile

新增 `application-postgres.yml`，配置：

```yaml
spring:
  datasource:
    url: jdbc:postgresql://localhost:5432/analyst_workspace
    username: analyst
    password: analyst
  flyway:
    enabled: true

app:
  vector-store:
    provider: pgvector
    enabled: true
```

保留当前本地 DuckDB 配置，但只用于分析数据和临时执行结果。

### 5.2 引入 Flyway

建立标准迁移目录：

```text
backend/src/main/resources/db/migration
```

后续 PostgreSQL 表结构全部通过 Flyway 管理，不再依赖 Java 代码中大量 `CREATE TABLE IF NOT EXISTS`。

建议首批迁移脚本：

```text
V1__init_identity_and_sessions.sql
V2__init_workspace_and_dataset.sql
V3__init_document_assets.sql
V4__init_analysis_artifacts.sql
V5__init_artifact_memories.sql
V6__init_context_traces.sql
V7__init_pgvector_indexes.sql
```

### 5.3 迁移系统业务表

优先迁移：

```text
users
chat_sessions
workspaces
workspace_datasets
datasets
dataset_tables
dataset_columns
document_assets
document_chunks
analysis_artifacts
```

DuckDB 中只保留：

```text
用户上传 CSV / Excel 后生成的分析表
临时分析表
SQL 分析中间结果
```

### 5.4 拆分 Repository

当前 `DuckDBRepository` 职责过重，建议逐步拆分：

```text
UserRepository
ChatSessionRepository
WorkspaceRepository
DatasetRepository
DocumentRepository
ArtifactRepository
MemoryRepository
ContextTraceRepository
DuckDbAnalysisRepository
```

其中 `DuckDbAnalysisRepository` 只负责 DuckDB 分析表、查询执行和分析数据读写。

## 6. pgvector 语义索引设计

建议建立三类向量表。

### 6.1 文档 Chunk 向量

```sql
CREATE TABLE document_chunk_embeddings (
    id BIGSERIAL PRIMARY KEY,
    chunk_id BIGINT NOT NULL,
    workspace_id BIGINT,
    document_id BIGINT NOT NULL,
    content TEXT NOT NULL,
    embedding vector(1024),
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);
```

用途：

- 用户问题涉及业务说明、指标口径、规则文档时，召回相关文档片段。
- 进入 `DocumentContext`。

### 6.2 Schema 向量

```sql
CREATE TABLE schema_embeddings (
    id BIGSERIAL PRIMARY KEY,
    dataset_id BIGINT NOT NULL,
    table_name TEXT NOT NULL,
    column_name TEXT,
    semantic_text TEXT NOT NULL,
    embedding vector(1024),
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);
```

用途：

- 自然语言问题匹配相关表、字段、字段含义、表关系。
- 辅助 Text-to-SQL 和多表分析。

### 6.3 Artifact Memory 向量

```sql
CREATE TABLE artifact_memory_embeddings (
    id BIGSERIAL PRIMARY KEY,
    memory_id BIGINT NOT NULL,
    workspace_id BIGINT,
    dataset_id BIGINT,
    memory_type VARCHAR(64),
    summary TEXT NOT NULL,
    embedding vector(1024),
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);
```

用途：

- 召回历史分析结论。
- 召回复用过的 SQL / Python 分析模式。
- 召回用户确认过的业务口径。

## 7. Artifact 沉淀升级

当前 artifact 已经是分析资产雏形，后续需要从“历史结果”升级为“可复用分析资产”。

### 7.1 analysis_artifacts

建议 PostgreSQL 中保留完整 artifact：

```text
id
workspace_id
session_id
dataset_id
user_query
analysis_type
generated_code_or_sql
summary
result_preview_json
analysis_report_json
evidence_summary_json
execution_logs_json
validation_report_json
risk_notices_json
artifact_status
created_at
updated_at
```

这个表回答的是：某一次分析完整发生了什么。

### 7.2 artifact_memories

新增 `artifact_memories`：

```text
id
artifact_id
workspace_id
dataset_id
memory_type
scope
content
summary
importance
confidence
status
created_at
updated_at
last_used_at
use_count
```

这个表回答的是：这次分析中有哪些内容值得长期复用。

### 7.3 Memory 类型

建议类型：

```text
SCHEMA_FINDING      字段、表关系、数据结构发现
ANALYSIS_FINDING    历史分析结论
BUSINESS_RULE       业务规则、指标口径
QUERY_PATTERN       可复用 SQL / Python 分析模式
RISK_NOTICE         数据质量、口径冲突、统计风险
USER_PREFERENCE     用户偏好的分析方式
```

### 7.4 Memory 作用域

建议作用域：

```text
SESSION_LOCAL       只在当前会话有效
WORKSPACE_LOCAL     当前工作区长期有效
DATASET_LOCAL       围绕某个数据集有效
USER_GLOBAL         用户全局偏好
```

## 8. Artifact 到 Memory 的链路

每次分析完成后执行：

```text
AnalysisResult
  -> save analysis_artifacts
  -> ArtifactMemoryExtractor
  -> save artifact_memories
  -> embedding summary
  -> save artifact_memory_embeddings
```

第一版可以先用规则抽取，不必马上使用大模型：

```text
如果 artifact 有 summary -> 生成 ANALYSIS_FINDING
如果 validation_report 有 warning -> 生成 RISK_NOTICE
如果 generated_code_or_sql 可复用 -> 生成 QUERY_PATTERN
如果 evidence_summary 提到字段/表关系 -> 生成 SCHEMA_FINDING
```

后续再加入 LLM extractor。

## 9. WorkspaceContextAssembler 升级

当前上下文装配器应该升级为统一上下文编排器。

输入：

```text
用户问题
workspace_id
session_id
dataset_id
```

检索来源：

```text
workspace 基础信息
dataset schema
table relations
schema_embeddings
document_chunk_embeddings
artifact_memory_embeddings
recent chat messages
```

输出：

```text
UnifiedAnalysisContext
```

推荐流程：

```text
用户问题
  -> AnalysisRouter 判断问题类型
  -> 检索 schema hints
  -> 检索 document chunks
  -> 检索 artifact memories
  -> 去重、排序、压缩
  -> 写 context_traces
  -> 输入 AI Agent
```

## 10. Context Trace

新增 `context_traces`：

```text
id
workspace_id
session_id
query
selected_schema_ids
selected_document_chunk_ids
selected_memory_ids
filtered_items_json
packed_context
created_at
```

它的价值是：

- 解释本次分析为什么用了这些上下文。
- 调试 RAG 和 agent 输出。
- 给论文和答辩提供工程支撑。
- 后续评估上下文质量。

## 11. Memory 检索排序策略

不要把所有历史 artifact 都塞进 prompt。建议评分：

```text
score =
  semantic_similarity * 0.35
  + workspace_match * 0.20
  + dataset_match * 0.15
  + importance * 0.15
  + recency * 0.10
  + use_count * 0.05
```

上下文预算建议：

```text
schema hints: 最多 8 条
document chunks: 最多 6 条
artifact memories: 最多 5 条
recent chat messages: 最多 3 条
```

## 12. Memory 治理

长期来看，memory 会越来越多，必须治理。

建议状态：

```text
ACTIVE
ARCHIVED
SUPERSEDED
DELETED
```

建议关系：

```text
duplicates
conflicts_with
supersedes
derived_from
```

第一版可以只做：

- 归档
- 软删除
- 使用次数统计
- 最近使用时间
- 手动标记重要

第二版再做：

- 重复检测
- 冲突检测
- 新结论替代旧结论
- memory 自动压缩

## 13. 前端配套改造

前端不需要把 memory 做得很重，但要让用户知道系统确实在沉淀和复用。

### 13.1 数据仓

数据仓中展示：

```text
数据集
表
字段
文档
工作区
分析结果 artifact
长期记忆 memory
```

### 13.2 Workspace 分析页

分析结果区域展示：

```text
本次结论
执行代码
数据证据
引用文档
使用的历史记忆
校验结果
风险提示
```

### 13.3 Artifact Detail

Artifact detail 应该像实验报告：

```text
问题
方法
SQL / Python
结果
证据
校验
风险
沉淀 memory
```

### 13.4 Context Trace 展示

可以在分析结果里放一个“上下文来源”按钮，点击后展示：

```text
使用了哪些字段说明
使用了哪些文档片段
使用了哪些历史记忆
为什么被选中
```

## 14. 推荐执行顺序

最小可落地路线：

```text
1. PostgreSQL + Flyway 基础接入
2. 把 users / sessions / workspaces / datasets / documents / artifacts 迁到 PostgreSQL
3. DuckDBRepository 拆分，让 DuckDB 只负责分析执行
4. 接入 pgvector，替换 Milvus 默认路线
5. 建立 schema_embeddings 和 document_chunk_embeddings
6. 建立 artifact_memories 和 artifact_memory_embeddings
7. 分析完成后从 artifact 抽取 memory
8. WorkspaceContextAssembler 检索 artifact memory
9. 新增 context_traces
10. 前端展示 artifact detail、memory、上下文来源
```

## 15. 验收标准

## 15.0 当前已落地的第一批优化

本轮已经先完成“可选 PostgreSQL / pgvector 基础接入”，不改变当前默认本地运行路径：

- 新增 `backend/src/main/resources/application-postgres.yml`
- 新增 Flyway 迁移目录 `backend/src/main/resources/db/migration`
- 新增首批迁移脚本：
  - `V1__init_identity_and_sessions.sql`
  - `V2__init_workspace_and_dataset.sql`
  - `V3__init_document_assets.sql`
  - `V4__init_analysis_artifacts.sql`
  - `V5__init_artifact_memories.sql`
  - `V6__init_context_traces.sql`
  - `V7__init_pgvector_indexes.sql`
- 新增 `docker-compose-postgres.yml`，使用 `pgvector/pgvector:pg16`
- 后端新增 PostgreSQL、Flyway、Spring AI pgvector 依赖
- Milvus starter 与旧配置已移除，项目向量路线收敛为 pgvector；默认 profile 不自动启用向量库，PostgreSQL profile 显式启用。

本地启动 PostgreSQL / pgvector：

```powershell
docker compose -f docker-compose-postgres.yml up -d
```

使用 PostgreSQL profile 启动后端：

```powershell
mvn -f backend/pom.xml spring-boot:run -Dspring-boot.run.profiles=postgres
```

当前这一步的定位是“架构铺轨”：PostgreSQL profile 和表结构已具备，但业务 repository 尚未切换到 PostgreSQL，DuckDB 仍承担当前既有业务元数据路径。下一步应优先拆分 repository，并把 artifact / memory 作为第一批迁移对象。

本轮继续补齐了第一版 artifact 到 memory 的当前链路：

- 新增 `ArtifactMemory` 领域对象。
- 新增 `ArtifactMemoryExtractor`，先用规则抽取：
  - summary -> `ANALYSIS_FINDING`
  - generated SQL / Python -> `QUERY_PATTERN`
  - evidence dataset/document summary -> `SCHEMA_FINDING`
  - risk notices -> `RISK_NOTICE`
- 当前 DuckDB 兼容层新增 `artifact_memories` 表和 `seq_artifact_memory`。
- `AnalysisService` 保存 artifact 后会同步抽取并保存 memories。
- `ArtifactDetailResponse` 增加 `memories` 字段，artifact detail 可以回放本次沉淀出的长期记忆。
- `frontend-next` 类型定义增加 `ArtifactMemory`。

这一版仍未接 embedding 和 pgvector 检索，定位是先让“记忆对象”进入主分析闭环。下一步应把 `artifact_memories` 迁移到 PostgreSQL，并为 `artifact_memory_embeddings` 补写入链路。

本轮继续让 memory 开始参与下一次分析：

- 新增 `MemoryContext`，作为 `UnifiedAnalysisContext` 的第四类上下文。
- `UnifiedContextSummary` 增加 memory 来源、策略、topK、预算、数量和 prompt 字符数。
- `DuckDBRepository` 增加 workspace 级 active memory 查询。
- `WorkspaceContextAssembler` 会按 query 对 workspace 内 artifact memories 做轻量词法排序，最多选择 5 条，打包进 prompt。
- `UnifiedAnalysisContext.businessContextPrompt()` 会把 retrieved artifact memories 拼进 business context，供 Agent 在下一次 workspace 分析中参考。
- `frontend-next` 类型定义补齐 `UnifiedContextSummary` 的 memory 字段。

当前 memory 召回策略为 `LEXICAL_RECENT`，用于打通产品闭环；后续会被 pgvector semantic similarity 替换或增强。

本轮继续补齐 context trace 的当前链路：

- 新增 `ContextTrace` 领域对象。
- 当前 DuckDB 兼容层新增 `context_traces` 表和 `seq_context_trace`。
- `DuckDBRepository` 增加 context trace 保存和按 id 回读。
- `WorkspaceContextAssembler` 在统一上下文组装完成后保存 trace。
- trace 当前记录：
  - workspace / group id
  - 原始 query
  - selected schema dataset ids
  - selected document chunk refs
  - selected memory ids
  - context summary 与 focusDatasetIds
  - packed context snapshot

这一版先解决“系统知道自己用了哪些上下文”的工程闭环。下一步可以新增 API 和前端入口，让 artifact detail 或分析结果页能打开“上下文来源”。

本轮把 context trace 从后台记录推进到产品可读链路：

- `AnalysisResponse` 增加 `contextTraceId`。
- `analysis_artifacts` 增加 `context_trace_id`，新 artifact 会关联本次 context trace。
- `ArtifactDetailResponse` 增加 `contextTraceId` 和 `contextTrace`。
- 新增 `ContextTraceService`。
- 新增 `ContextTraceController`：
  - `GET /api/context-traces/{id}`
  - `GET /api/context-traces/recent?groupId=&limit=`
- 前端 `ArtifactDetail` 类型增加 `contextTrace`。
- 前端 workspace artifact detail 增加“上下文来源”面板，展示：
  - schema dataset ids
  - document chunk refs
  - memory ids
  - packed context snapshot

这一步让 artifact detail 更接近“实验报告”：不仅能看结果和代码，也能回看本次分析使用了哪些上下文。

本轮继续补齐了第一版 memory 治理与复用统计：

- `WorkspaceContextAssembler` 在 artifact memories 真正进入 prompt 后，会回写 `use_count` 和 `last_used_at`。
- 当前 DuckDB 兼容层新增 memory usage 回写、按 id 查询、按 workspace/status 列表查询、状态更新和重要性更新能力。
- 新增 `ArtifactMemoryService`。
- 新增 `ArtifactMemoryController`：  
  - `GET /api/artifact-memories/{id}`
  - `GET /api/artifact-memories/recent?groupId=&limit=&status=`
  - `PATCH /api/artifact-memories/{id}/archive`
  - `PATCH /api/artifact-memories/{id}/restore`
  - `PATCH /api/artifact-memories/{id}/supersede`
  - `PATCH /api/artifact-memories/{id}/importance?importance=`
  - `DELETE /api/artifact-memories/{id}`
- 前端 API client 新增 `artifactMemoryApi`，为后续数据仓或 workspace 中的 memory 管理面板预留调用入口。

这一步让长期记忆从“只会被抽取和召回”推进到“可以被观察和治理”。后续可以在前端数据仓中加入 memory 列表，并把 `use_count`、`last_used_at`、`importance` 作为排序和人工治理入口。

本轮继续把 memory 治理推进到前端产品界面：

- workspace 主页面新增“长期记忆”面板。
- 工作区资产弹窗新增“长期记忆”tab。
- 前端支持按 memory 状态筛选：
  - `ACTIVE`
  - `ARCHIVED`
  - `SUPERSEDED`
  - `DELETED`
- 前端支持搜索 memory 的类型、作用域、摘要、正文和关联 artifact/dataset。
- 前端支持对 memory 执行：
  - 归档
  - 恢复
  - 标记为替代
  - 软删除
  - 提高 / 降低重要性
- memory 卡片展示：
  - memory type
  - scope
  - summary/content
  - importance
  - confidence
  - use_count
  - last_used_at

这一步让长期记忆进入“可见、可筛选、可治理”的产品状态。后续可以继续把 memory 管理页从 workspace 中拆到独立数据仓视图，或进一步接入 pgvector 后展示语义召回分数。

本轮继续把 memory 召回从纯词法推进到可插拔语义检索：

- 新增 `ArtifactMemoryVectorService`。
- artifact memory 保存后会尝试写入当前 Spring AI `VectorStore`：
  - 默认 profile 下不自动启用向量库，避免本地 H2 / DuckDB 开发路径被 pgvector 绑定。
  - PostgreSQL profile 下可写入 pgvector 对应的 `vector_store` 通用表。
- memory vector metadata 当前包含：
  - `type=artifact_memory`
  - `memoryId`
  - `artifactId`
  - `groupId`
  - `datasetId`
  - `memoryType`
  - `scope`
  - `status`
- `WorkspaceContextAssembler` 的 memory 召回策略升级为：
  - 优先 `SEMANTIC_VECTOR`
  - 若语义结果不足，则 `SEMANTIC_VECTOR_LEXICAL_FILL`
  - 若语义检索不可用或无结果，则回退 `LEXICAL_RECENT`
  - 若 query 无匹配，则回退 `RECENT_FALLBACK`
- 语义召回返回的 memory 会再次通过 `AnalysisArtifactStore.findArtifactMemoryById` 回读当前状态，只有 `ACTIVE` memory 会进入 prompt。
- `app.memory.semantic-retrieval.enabled` 控制语义 memory 检索开关。

这一步让 pgvector 路线有了实际进入 memory 检索的接点。后续可以继续把独立的 `artifact_memory_embeddings` 专表写入链路补齐，或将通用 `vector_store` 的 Spring AI 检索结果扩展为携带 similarity score 的前端解释。

本轮继续补齐 memory 语义召回的可解释闭环：

- `ArtifactMemory` 增加本次召回解释字段：
  - `retrievalMode`
  - `retrievalScore`
  - `retrievalReason`
- `ArtifactMemoryVectorService` 从 VectorStore 命中结果中读取 `distance`，并标记 `semantic_vector`。
- `WorkspaceContextAssembler` 对不同来源的 memory 标记召回模式：
  - `semantic_vector`
  - `lexical_fill`
  - `lexical_recent`
  - `recent_fallback`
- memory prompt block 中会带上 retrieval mode / score。
- context trace 的 `filtered_items_json` 增加 `selectedMemories` 明细，包含：
  - memory id
  - memory type
  - scope
  - retrieval mode
  - retrieval score
  - retrieval reason
  - importance
  - confidence
  - clipped summary
- 前端 Artifact Detail 的“上下文来源”面板新增 Memory Retrieval 展示区，可以回看本次分析为什么召回这些长期记忆。

这一步让系统不仅“使用了长期记忆”，还能够解释“用了哪条、通过什么方式召回、分数是多少”。这对后续 RAG 质量调试、毕业设计答辩和论文工程论证都很关键。

本轮开始推进业务主库迁移中的第一块 repository 解耦：

- 新增 `AnalysisArtifactStore` 门面，覆盖 artifact / memory / context trace 的核心读写方法。
- 新增 `DuckDbAnalysisArtifactStore`，默认配置下继续委托当前 DuckDB 兼容层，保证本地现有运行路径不变。
- 新增 `PostgresAnalysisArtifactStore`，在 `app.persistence.artifact-store=postgres` 时使用主 `JdbcTemplate` 写入 PostgreSQL。
- `application-postgres.yml` 增加：
  - `app.persistence.artifact-store: postgres`
- 以下服务已从直接依赖 `DuckDBRepository` 切换到 `AnalysisArtifactStore`：
  - `AnalysisService`
  - `ArtifactService`
  - `ArtifactMemoryService`
  - `ArtifactMemoryVectorService`
  - `ContextTraceService`
  - `WorkspaceContextAssembler`
- PostgreSQL store 当前覆盖：
  - `analysis_artifacts`
  - `artifact_memories`
  - `context_traces`
- 字段映射兼容当前领域模型：
  - `workspace_id` <-> `groupId`
  - `analysis_type` <-> `mode`
  - JSONB 字段通过 `CAST(? AS JSONB)` 写入

这一步是 DuckDB 释放业务主库职责的关键开口。当前默认仍是 DuckDB，PostgreSQL profile 下 artifact / memory / context trace 已有可切换实现；后续应继续把 workspace / dataset / document repository 也迁到同样的门面结构。

本轮继续推进 workspace / dataset 元数据迁移和 pgvector 路线收敛：

- 新增 `WorkspaceCatalogStore` 门面，覆盖 workspace、dataset group、dataset metadata、dataset columns、schema context 所需读写方法。
- 新增 `DuckDbWorkspaceCatalogStore`，默认配置下继续委托 DuckDB 兼容层。
- 新增 `PostgresWorkspaceCatalogStore`，在 `app.persistence.catalog-store=postgres` 时使用 PostgreSQL 读写 workspace / dataset 元数据。
- `application-postgres.yml` 增加：
  - `app.persistence.catalog-store: postgres`
- 以下服务已从直接读取 DuckDB 元数据切换到 `WorkspaceCatalogStore`：
  - `DatasetGroupService`
  - `DatasetService`
  - `MetadataService`
  - `WorkspaceSchemaService`
  - `DocumentService` 的 group 校验路径
- `docker-compose-postgres.yml` 支持通过 `POSTGRES_PORT` 覆盖宿主端口，便于和本机已有 PostgreSQL 共存。
- 移除 Milvus starter 与 `MilvusConfig`，并在启动类中排除 Spring AI 的向量 store 自动配置，统一由项目内 `PgVectorConfig` 按 `app.vector-store.enabled=true` 与 `provider=pgvector` 显式创建。
- 新增 gated Postgres profile 冒烟测试 `PostgresProfileSmokeTest`，通过 `RUN_POSTGRES_SMOKE=true` 才连接本地 PostgreSQL 容器执行，避免普通单测依赖 Docker。
- `DatasetService.deleteDataset` 已拆分为 DuckDB 分析表删除 + 当前 profile catalog metadata 删除，PostgreSQL profile 下不再依赖 DuckDB 元数据影子表。

本轮继续完成 document asset / chunk 的业务主库迁移：

- 新增 `DocumentStore` 门面，覆盖 document asset 与 document chunk 的保存、状态更新、查询、删除。
- 新增 `DuckDbDocumentStore`，默认配置下继续委托 DuckDB 兼容层。
- 新增 `PostgresDocumentStore`，在 `app.persistence.document-store=postgres` 时读写 PostgreSQL 的 `document_assets` / `document_chunks`。
- `application-postgres.yml` 增加：
  - `app.persistence.document-store: postgres`
- `DocumentService` 已从直接依赖 `DuckDBRepository` 切换到 `DocumentStore`，包括：
  - 文档上传后的 asset 保存
  - 文档解析状态更新
  - chunk 删除与保存
  - 文档列表 / 详情 / chunk 查询
  - 文档删除
  - 词法文档检索 fallback
- `PostgresProfileSmokeTest` 增加 workspace + document asset + document chunk 的真实 PostgreSQL round-trip，覆盖 Postgres profile 下文档元数据链路。
- 本地密钥配置已规范化：
  - 仓库内配置使用 `DEEPSEEK_API_KEY`、`DASHSCOPE_API_KEY`、`JWT_SECRET` 等环境变量。
  - `.env.example` 补充 PostgreSQL / DuckDB 相关环境变量。
  - 本地真实密钥已移到桌面 PowerShell 环境脚本，避免进入 GitHub。

本轮验证结果：

```powershell
mvn -f backend\pom.xml -DskipTests compile
$env:RUN_POSTGRES_SMOKE='true'; mvn -f backend\pom.xml "-Dtest=PostgresProfileSmokeTest" test
mvn -f backend\pom.xml test
npm run lint
npm run build
```

验证结论：

- 后端编译通过，Milvus 类型引用已清空。
- Postgres 冒烟测试通过，7 个 Flyway migration 可在 `pgvector/pgvector:pg16` 空库上完整执行，并能装配 `PostgresAnalysisArtifactStore`、`PostgresWorkspaceCatalogStore`、`PostgresDocumentStore`。
- Postgres 冒烟测试已覆盖 document asset / chunk 的真实写入、查询、状态更新与清理。
- 52 个后端测试执行通过，其中 gated Postgres 测试在普通回归中正确跳过；默认 DuckDB 路径、artifact、memory、context trace、dataset / document 链路未被 pgvector 收敛破坏。
- 前端 `npm run lint` 与 `npm run build` 均通过。

当前剩余迁移重点：

- chat session / user 仍依赖现有 JPA / repository 路径，需要作为最终 profile 验收项继续覆盖。
- `DuckDBRepository` 仍保留大量兼容方法，后续可以继续瘦身到只负责分析表、SQL 执行、临时表和 DuckDB OLAP 能力。
- 真正的端到端人工验收仍建议跑一遍：登录 -> 创建 workspace -> 上传 dataset -> 上传 document -> 发起分析 -> 查看 artifact/memory/context trace。

第一阶段验收：

- 使用 PostgreSQL 启动后，登录、会话、workspace、数据仓、artifact API 正常。
- DuckDB 只用于分析执行，不再承担核心业务主库职责。
- pgvector 可替换 Milvus，系统不再默认依赖 Milvus stack。

第二阶段验收：

- 数据集导入后能生成 schema embedding。
- 文档解析后能生成 document chunk embedding。
- 分析完成后能保存 artifact。
- artifact 能抽取 memory。
- memory 能进入 pgvector。

第三阶段验收：

- 下一次分析可以召回历史 memory。
- context trace 能显示本次使用了哪些上下文。
- 前端能看到 artifact detail 和 memory 来源。
- 历史 memory 不会无限进入 prompt，有明确数量和排序控制。

## 16. 最终形态

优化完成后，系统应从：

```text
一次性自然语言数据分析工具
```

升级为：

```text
以 workspace 为中心，能够组织数据、理解文档、沉淀分析结果、复用历史经验的 AI 数据分析工作台
```

这也是该项目最有价值的方向：不是单纯做 Text-to-SQL，而是做结构化数据、非结构化文档、历史分析结果和长期记忆共同参与的交互式数据分析系统。
