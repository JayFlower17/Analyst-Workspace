# Phase 2 Kickoff

## 1. 目标

Phase 2 的目标是：

**让系统开始把非结构化文档当成 workspace 内的一等资产，而不再只理解表格数据。**

这一阶段不追求一步做到文档解析、切块、向量化全闭环，先把最小骨架立起来，并先打通文本类文档的第一条解析链路：

1. 文档对象建模
2. workspace 级文档上传入口
3. 文档列表 / 单条查询 / 删除入口
4. 文档处理状态字段
5. 后续解析、切块、索引的挂点
6. TXT / Markdown 解析与 chunk 持久化的第一版
7. PDF / DOCX 正文提取与 chunk 持久化的第一版
8. workspace 级文档检索接口与本地回退检索
9. workspace 分析时注入文档上下文的最小闭环
10. 向量模式 profile 与真实向量检索验证
11. 检索排序 / 去重 / 相邻 chunk 合并优化
12. 轻量 context assembler 与文档预算控制
13. 混合特征 rerank
14. query intent / document role 感知 rerank
15. rerank 信号回流到 context assembler
16. 联合分析 hybrid benchmark

---

## 2. 本轮已完成内容

### 2.1 新增文档资产实体

- [DocumentAsset.java](F:\data-analysis-platform\backend\src\main\java\com\analysis\model\entity\DocumentAsset.java)
- [DocumentChunk.java](F:\data-analysis-platform\backend\src\main\java\com\analysis\model\entity\DocumentChunk.java)

当前字段覆盖了第一批 Phase 2 最关键的元数据：

- 文档 ID
- workspace 归属
- 文档名称
- 原始文件名
- 存储路径
- 文件类型
- MIME type
- 文件大小
- 处理状态
- 处理错误
- chunk 数量

### 2.2 新增 DuckDB 元数据表

在 [DuckDBRepository.java](F:\data-analysis-platform\backend\src\main\java\com\analysis\repository\DuckDBRepository.java) 中新增：

- `meta_documents`
- `meta_document_chunks`

并补了对应 sequence：

- `seq_document_asset`
- `seq_document_chunk`

### 2.3 新增文档服务层

- [DocumentService.java](F:\data-analysis-platform\backend\src\main\java\com\analysis\service\DocumentService.java)

当前能力：

- 校验 workspace 是否存在
- 限制首批文档类型
- 保存文档文件到 `uploads/documents/`
- 保存文档元数据到 DuckDB
- 查询 workspace 下文档
- 删除文档及其 chunk 元数据

### 2.4 新增文档接口

- [DocumentController.java](F:\data-analysis-platform\backend\src\main\java\com\analysis\controller\DocumentController.java)

当前接口：

- `POST /api/documents/upload`
- `GET /api/documents?groupId=...`
- `GET /api/documents/{id}`
- `GET /api/documents/{id}/chunks`
- `GET /api/documents/search?groupId=...&query=...`
- `DELETE /api/documents/{id}`

### 2.5 已接通 TXT / Markdown 解析链路

当前 `DocumentService` 已支持：

- `TXT / MD` 文件正文提取
- 文档状态流转：
  - `UPLOADED`
  - `PARSING`
  - `PARSED`
  - `FAILED`
- chunk 数量持久化
- 第一版 chunk 元数据持久化

当前 chunk 策略：

- 优先按空行分段
- 尽量将完整段落拼成一个 chunk
- 超长段落再做硬切
- 暂不做 overlap

### 2.6 已接通 PDF / DOCX 解析链路

当前 `DocumentService` 已新增：

- `DOCX` 文本提取
- `PDF` 文本提取
- 上传后直接进入统一的解析与 chunk 持久化流程

当前实现方式：

- `DOCX`：基于 Apache POI `XWPFDocument`
- `PDF`：基于 Apache PDFBox `PDFTextStripper`

### 2.7 已接通 workspace 级文档检索接口

当前 `DocumentService` 已新增第一版检索能力：

- 优先走向量检索
- 当前本地 `local` profile 下，向量库关闭时自动回退到词项匹配检索
- 检索结果会返回：
  - `documentId`
  - `documentName`
  - `fileType`
  - `chunkIndex`
  - `chunkText`
  - `score`
  - `retrievalMode`

当前设计目标不是一步做完“真正的联合上下文层”，而是先让 document chunk 具备可召回能力，给后续 agent 上下文组装留出稳定挂点。

### 2.8 已接通分析侧文档上下文注入

当前 `AnalysisService` 在 workspace 分析路径下，已经会：

- 根据用户问题召回相关 document chunks
- 将召回结果拼接为 `Workspace Retrieved Document Context`
- 一起注入 `AnalysisPlanGenerator.runWorkspaceAnalysis(...)`

这意味着当前系统已经具备最小版的：

- 结构化表 schema
- workspace 关系
- workspace 业务描述
- 文档检索结果

共同进入同一次分析 prompt 的能力。

### 2.9 已补齐向量模式 profile

当前新增了：

- [application-local-vector.yml](F:\data-analysis-platform\backend\src\main\resources\application-local-vector.yml)

用途是：

- 在保留 `local` 作为默认稳定开发环境的同时
- 允许通过 `local,local-vector` 组合 profile 打开真实 Milvus / embedding 检索

同时，`MilvusConfig` 已改为：

- 支持通过配置读取 host / port / collection name / dimension
- 自定义 `MilvusVectorStore` 标记为 `@Primary`

这样可以避免旧的默认自动配置混入 `vector_store` 集合名。

### 2.10 已做检索结果质量优化

当前 `DocumentService` 检索后处理新增了三件事：

- 同一 `documentId + chunkIndex` 的重复结果去重
- 同一文档最多保留有限条结果，避免单篇文档霸榜
- 相邻 chunk 自动合并成更完整的上下文块

当前合并策略：

- 只合并同一文档内相邻的 chunk
- 单次最多合并 `2` 个连续 chunk
- 返回结果新增：
  - `endChunkIndex`
  - `sourceChunkCount`

这样做的目标是：

- 减少重复结果
- 避免同一文档刷屏
- 让模型读到更完整的语义块，而不是过碎的片段

### 2.11 已接入轻量 context assembler

当前新增了：

- [WorkspaceContextAssembler.java](F:\data-analysis-platform\backend\src\main\java\com\analysis\service\WorkspaceContextAssembler.java)

当前它负责做三件事：

1. 判断这道问题要不要带文档上下文
2. 决定文档召回的 `topK` 和字符预算
3. 把结果裁剪成适合 prompt 的文档块

当前策略分成三档：

- `SKIP`
  - 对明显纯结构化聚合问题，不带文档
- `LIGHT`
  - 对一般问题，少量带文档
- `HEAVY`
  - 对“根据说明 / 规则 / note / 解释 / 为什么 / 优先级”这类问题，更多带文档

当前预算：

- `LIGHT`：`topK = 2`，字符预算约 `1600`
- `HEAVY`：`topK = 4`，字符预算约 `2600`

这一步的目标是：

- 不让每条分析请求都盲目带文档
- 让文档真正作为“按需上下文”参与分析

### 2.12 已接入混合特征 rerank

当前在检索后处理阶段，新增了一个轻量 rerank：

- 保留原始检索分 `score`
- 新增综合排序分 `rerankScore`

当前 `rerankScore` 主要叠加这些信号：

- query 词项在 chunk 文本中的覆盖度
- query 整句是否直接命中 chunk
- 文档名是否命中 query 词项
- 标题块的小幅加权
- 合并后的相邻 chunk 小幅加权
- 过长或过短 chunk 的轻微惩罚

目标不是把排序做成复杂学习系统，而是先让：

- 真正覆盖 query 关键词的 chunk 更靠前
- 标题明确、语义更完整的块更靠前
- “只因原始 score 碰巧高一点”的块不至于一直压在前面

### 2.13 已接入 query intent / document role 感知

当前 rerank 在混合特征基础上，又补了两层语义判断：

- `QueryIntent`
  - `RULE_HEAVY`
  - `DEFINITION_HEAVY`
  - `STRUCTURED`
  - `GENERAL`

- `DocumentRole`
  - `RULE_NOTE`
  - `GUIDE`
  - `REFERENCE`
  - `GENERIC`

当前会根据：

- query 是否像“根据说明 / 规则 / note / 优先级”
- chunk / 文档名 / 标题是否更像规则说明、指南、参考定义

给不同块不同加权。

同时，返回结果新增：

- `rerankNotes`

它会告诉你当前排序主要受哪些信号影响，比如：

- `coverage=0.91`
- `heading-block`
- `intent=RULE_HEAVY`
- `role=RULE_NOTE`

### 2.14 已让 rerank 信号回流到 context assembler

当前 `WorkspaceContextAssembler` 已开始直接使用检索结果中的：

- `queryIntent`
- `documentRole`

来影响 prompt 装配顺序。

当前规则：

- `HEAVY` 问题：
  - `RULE_NOTE` 优先
  - `REFERENCE` 次之
  - `GUIDE` 再后
- `LIGHT` 问题：
  - `REFERENCE` 优先
  - `GUIDE` 次之
  - `RULE_NOTE` 再后

同时，文档块 header 里现在也会带：

- `role=...`
- `intent=...`

这样分析 prompt 中的上下文块不仅有内容本身，也有“这块为什么被选中”的轻量语义标签。

### 2.15 已补联合分析 benchmark

当前 `harness` 已新增：

- `hybrid` benchmark 分类
- 文档型 setup 资源创建 / 清理
- summary 级断言

新增用例：

- [workspace_orders_users_note_priority.json](F:\data-analysis-platform\harness\benchmarks\hybrid\workspace_orders_users_note_priority.json)

当前该用例会：

1. 为 `groupId = 1` 临时上传 `workspace-analysis-note.md`
2. 发起 orders + users 的 workspace 联表分析
3. 验证结果至少返回一行
4. 验证结果列中包含分段 / 订单类字段
5. 验证 summary 中体现文档规则驱动的高优先级分段表达

当前 `harness` 已支持：

- `structured`
- `workspace`
- `hybrid`

三类 benchmark 一起作为结构化分析与联合文档分析的最小自动化回归入口。

---

## 3. 当前支持范围

当前首批支持的文档类型：

- `.pdf`
- `.docx`
- `.txt`
- `.md`

注意：

**当前已经完成 TXT / Markdown / PDF / DOCX 的第一版正文提取与 chunk 持久化，补上了 workspace 级检索接口，实现了分析侧最小版文档上下文注入，并验证了可切换到真实向量检索；但默认 `local` 仍保持无向量依赖。**

当前已实现的状态包括：

- `PARSING`
- `PARSED`
- `FAILED`

后续阶段再继续补：

- `INDEXED`

---

## 4. 已完成验证

### 4.1 后端编译

已验证通过：

```powershell
cd F:\data-analysis-platform\backend
mvn -DskipTests compile
```

### 4.2 文档接口实测

已实际验证：

1. 文档列表接口可返回空数组
2. 文档上传接口可成功写入 Markdown / DOCX / PDF 文档
3. 文档详情接口可读回刚上传的记录
4. workspace 文档列表接口可查到上传结果
5. Markdown / DOCX / PDF 上传后状态可进入 `PARSED`
6. Markdown / DOCX / PDF 上传后可读回 chunk 列表
7. 文档检索接口可返回相关 chunk
8. workspace 分析请求可引用文档说明影响摘要表达
9. `local,local-vector` 模式下文档检索可返回 `retrievalMode = vector`
10. 检索结果会返回合并后的 chunk 范围和来源数量
11. 纯结构化问题会跳过文档召回，解释型问题会走更重的文档预算
12. 检索结果会同时返回 `score` 和 `rerankScore`
13. 检索结果会返回 `rerankNotes`，帮助解释排序原因
14. 检索结果会返回 `queryIntent / documentRole`
15. hybrid benchmark 可自动上传 / 清理临时说明文档并验证 summary 是否体现文档规则

本轮实测样例：

- workspace：`groupId = 1`
- Markdown：`docs/phase2-kickoff.md`
  - 上传后状态：`PARSED`
  - chunk 数量：`3`
- DOCX：`temp-samples/phase2-sample.docx`
  - 上传后状态：`PARSED`
  - chunk 数量：`1`
- PDF：`temp-samples/phase2-sample.pdf`
  - 上传后状态：`PARSED`
  - chunk 数量：`1`
- 检索：
  - 查询：`PDF 正文提取`
  - 返回模式：`lexical`
  - 可返回命中的 Markdown chunk
- 联合分析：
  - 上传文档：`temp-samples/workspace-analysis-note.md`
  - 分析请求：要求“按 workspace note 说明高亮 segment”
  - 实际结果：摘要明确给出 `L3` 应优先强调
- 向量检索：
  - profile：`local,local-vector`
  - 查询：`highest-priority customer cohort`
  - 返回模式：`vector`
  - 命中文档：`Vector Retrieval Note`
- 检索质量优化：
  - 上传文档：`docs/phase2-kickoff.md`
  - 返回结果：`chunkIndex=0, endChunkIndex=1, sourceChunkCount=2`
  - 说明：相邻 chunk 已被合并，不再直接返回整篇或碎片刷屏
- 上下文编排：
  - 纯统计问题：日志显示 `Skipping document retrieval`
  - 带 `workspace note` 的解释型问题：日志显示 `Included 1 document context blocks using HEAVY strategy`
- rerank：
  - 检索返回中可见 `rerankScore`
  - 排序优先按 `rerankScore`，再按原始 `score`
- intent / role 感知：
  - 对 `workspace note` 类 query，命中的 `Workspace Analysis Note`
  - `rerankNotes` 可见：`intent=RULE_HEAVY, role=RULE_NOTE`
- context assembler：
  - prompt 头信息里会显式带 `role` 和 `intent`
  - `HEAVY` 问题会优先装入 `RULE_NOTE`
- hybrid benchmark：
  - 命令：`python harness/run_benchmarks.py --category hybrid`
  - 结果：`Ran 1 cases / Passed: 1 / Failed: 0`
  - 报告：[hybrid-run-20260426-200722.json](F:\data-analysis-platform\harness\runs\hybrid-run-20260426-200722.json)
  - 摘要明确提到：`L3` 应被优先关注

---

## 5. 本地验证步骤

## 5.1 启动后端

```powershell
cd F:\data-analysis-platform\backend
mvn spring-boot:run -Dspring-boot.run.profiles=local
```

## 5.2 获取登录 token

可直接使用已经验证过的本地账号：

- username: `phase1user`
- password: `phase1pass`

## 5.3 验证文档列表

```powershell
$login = Invoke-RestMethod -NoProxy -Uri 'http://127.0.0.1:8080/api/auth/signin' `
  -Method Post -ContentType 'application/json' `
  -Body '{"username":"phase1user","password":"phase1pass"}'

$headers = @{ Authorization = "Bearer $($login.token)" }

Invoke-RestMethod -NoProxy -Uri 'http://127.0.0.1:8080/api/documents?groupId=1' `
  -Headers $headers
```

## 5.4 验证文档上传

```powershell
$form = @{
  file = Get-Item 'F:\data-analysis-platform\docs\README.md'
  groupId = '1'
  name = 'phase2-local-check'
}

Invoke-RestMethod -NoProxy -Uri 'http://127.0.0.1:8080/api/documents/upload' `
  -Method Post -Headers $headers -Form $form
```

## 5.5 验证单条详情

```powershell
$documentId = <把上传接口返回的 data.id 填进这里>

Invoke-RestMethod -NoProxy -Uri "http://127.0.0.1:8080/api/documents/$documentId" `
  -Headers $headers
```

## 5.6 验证 chunks

```powershell
Invoke-RestMethod -NoProxy -Uri "http://127.0.0.1:8080/api/documents/$documentId/chunks" `
  -Headers $headers
```

## 5.7 验证 DOCX / PDF

```powershell
$docxForm = @{
  file = Get-Item 'F:\data-analysis-platform\temp-samples\phase2-sample.docx'
  groupId = '1'
  documentName = 'phase2-docx-check'
}

$pdfForm = @{
  file = Get-Item 'F:\data-analysis-platform\temp-samples\phase2-sample.pdf'
  groupId = '1'
  documentName = 'phase2-pdf-check'
}

Invoke-RestMethod -NoProxy -Uri 'http://127.0.0.1:8080/api/documents/upload' `
  -Method Post -Headers $headers -Form $docxForm

Invoke-RestMethod -NoProxy -Uri 'http://127.0.0.1:8080/api/documents/upload' `
  -Method Post -Headers $headers -Form $pdfForm
```

## 5.8 验证文档检索

```powershell
Invoke-RestMethod -NoProxy -Uri 'http://127.0.0.1:8080/api/documents/search?groupId=1&query=PDF%20%E6%AD%A3%E6%96%87%E6%8F%90%E5%8F%96&topK=3' `
  -Headers $headers
```

## 5.9 验证“文档参与分析”

```powershell
$form = @{
  file = Get-Item 'F:\data-analysis-platform\temp-samples\workspace-analysis-note.md'
  groupId = '1'
  name = 'Workspace Analysis Note'
}

Invoke-RestMethod -NoProxy -Uri 'http://127.0.0.1:8080/api/documents/upload' `
  -Method Post -Headers $headers -Form $form

$body = @{
  groupId = 1
  query = 'Join the orders and users tables, summarize order counts by user segment, and mention which segment should be highlighted first according to the workspace note.'
} | ConvertTo-Json

Invoke-RestMethod -NoProxy -Uri 'http://127.0.0.1:8080/api/analysis/query' `
  -Method Post -Headers $headers -ContentType 'application/json' -Body $body
```

## 5.10 验证真实向量检索

先用组合 profile 启动后端：

```powershell
cd F:\data-analysis-platform\backend
mvn spring-boot:run -Dspring-boot.run.profiles=local,local-vector
```

然后调用检索：

```powershell
Invoke-RestMethod -NoProxy -Uri 'http://127.0.0.1:8080/api/documents/search?groupId=1&query=highest-priority%20customer%20cohort&topK=3' `
  -Headers $headers
```

## 5.11 验证检索去重和相邻 chunk 合并

```powershell
Invoke-RestMethod -NoProxy -Uri 'http://127.0.0.1:8080/api/documents/search?groupId=1&query=workspace%20document%20retrieval%20interface%20vector%20profile&topK=5' `
  -Headers $headers
```

## 5.12 验证 context assembler 策略

先上传说明文档，然后分别发两类分析请求：

```powershell
$form = @{
  file = Get-Item 'F:\data-analysis-platform\temp-samples\workspace-analysis-note.md'
  groupId = '1'
  name = 'Workspace Analysis Note'
}

Invoke-RestMethod -NoProxy -Uri 'http://127.0.0.1:8080/api/documents/upload' `
  -Method Post -Headers $headers -Form $form
```

纯结构化问题：

```powershell
$body = @{
  groupId = 1
  query = 'Summarize order counts by user segment.'
} | ConvertTo-Json

Invoke-RestMethod -NoProxy -Uri 'http://127.0.0.1:8080/api/analysis/query' `
  -Method Post -Headers $headers -ContentType 'application/json' -Body $body
```

## 5.13 验证 rerank 输出

```powershell
Invoke-RestMethod -NoProxy -Uri 'http://127.0.0.1:8080/api/documents/search?groupId=1&query=workspace%20document%20retrieval%20interface%20vector%20profile&topK=5' `
  -Headers $headers
```

观察返回字段中的：

- `score`
- `rerankScore`

## 5.14 验证 intent / role 感知 rerank

```powershell
Invoke-RestMethod -NoProxy -Uri 'http://127.0.0.1:8080/api/documents/search?groupId=1&query=according%20to%20the%20workspace%20note%20which%20segment%20should%20be%20highlighted%20first&topK=3' `
  -Headers $headers
```

重点看返回中的：

- `rerankNotes`
- `queryIntent`
- `documentRole`

文档解释型问题：

```powershell
$body = @{
  groupId = 1
  query = 'Join the orders and users tables, summarize order counts by user segment, and mention which segment should be highlighted first according to the workspace note.'
} | ConvertTo-Json

Invoke-RestMethod -NoProxy -Uri 'http://127.0.0.1:8080/api/analysis/query' `
  -Method Post -Headers $headers -ContentType 'application/json' -Body $body
```

## 5.15 验证联合分析 benchmark

```powershell
cd F:\data-analysis-platform
python harness\run_benchmarks.py --category hybrid
```

当前已覆盖两类联合分析样例：

- `orders + users + workspace note`
  - 验证用户分层分析会采纳 workspace note 中的优先级规则
- `orders + products + merchandising note`
  - 验证商品类目营收分析会采纳 merchandising note 中的战略类目规则

最近一次验证：

- 时间：`2026-05-01 11:05`
- 结果：`2 / 2` 通过
- 报告：[hybrid-run-20260501-110504.json](F:\data-analysis-platform\harness\runs\hybrid-run-20260501-110504.json)

### 5.16 Harness 诊断增强

当前 `harness` 已补充几项用于自动化回归的诊断能力：

- `--case-id`
  - 可只运行一个或多个指定 case
- `--request-timeout`
  - 可按需覆盖单次 HTTP 请求超时
- `--list-cases`
  - 可在不连接后端的情况下列出当前可运行 case
- 报告诊断字段：
  - `request_timeout_seconds`
  - `duration_seconds`
  - `case_path`
  - `request_excerpt`
  - `setup_documents_requested`
  - `setup_resources_created`
  - `cleanup_succeeded`
  - `cleanup_errors`

最近几次验证：

- `2026-05-01 11:09`
  - workspace benchmark：`2 / 2` 通过
  - 报告：[workspace-run-20260501-110931.json](F:\data-analysis-platform\harness\runs\workspace-run-20260501-110931.json)
- `2026-05-01 18:01`
  - 单 case：`workspace_traffic_orders_join_signal`
  - 结果：`1 / 1` 通过
  - 报告：[workspace-run-20260501-180146.json](F:\data-analysis-platform\harness\runs\workspace-run-20260501-180146.json)
- `2026-05-01 19:06`
  - 单 case：`hybrid_workspace_orders_products_merchandising_priority`
  - 结果：`1 / 1` 通过
  - 报告：[hybrid-run-20260501-190624.json](F:\data-analysis-platform\harness\runs\hybrid-run-20260501-190624.json)

本轮生成的 `harness/runs/` 报告文件保留，用作 Phase 2 回归记录。

---

## 6. 这还不是什么

为了避免误判，当前 Phase 2 进度 **还不等于**：

- 完整的多来源统一 `ContextAssembler`
- 成熟的文档 rerank / reretrieve / compression 体系
- 更大规模、更多业务域的联合分析评测集

这些仍然是后续任务，不是当前阶段已经完全做完的能力。

---

## 7. 下一步建议

建议按这个顺序继续推进：

1. 扩展联合分析 benchmark 到更多问题类型
2. schema / relation / document 的统一上下文预算
3. 更成熟的检索 rerank / reretrieve / compression
4. 结构化 + 文档联合上下文组装继续增强

也就是说，下一刀最合适的是：

**优先把 Phase 2 的联合分析评测继续补齐，并逐步收敛统一上下文组装。**
