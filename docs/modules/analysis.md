# 模块说明：analysis

## 1. 模块定位

`analysis` 模块负责把用户问题转成可执行分析流程，并返回结构化结果。

它是平台最核心的业务能力。

---

## 2. 负责什么

- 接收分析请求
- 组织数据上下文
- 调用 AI 生成 SQL 或 Python
- 做 SQL 白名单校验
- 执行 SQL 或 Python
- 返回结果数据、摘要、图表建议
- 保存 analysis artifact

---

## 3. 不负责什么

- 不负责前端可视化渲染
- 不负责文件上传导入
- 不负责用户登录状态

---

## 4. 当前状态

当前 analysis 已支持两种模式：

### 单表模式

- 基于 `datasetId`
- 适合传统单表查询和分析

### workspace 模式

- 基于 `groupId + focusDatasetIds`
- 由 `WorkspaceSchemaService` 组织上下文
- 更适合多表联合分析

同时支持两类执行路径：

1. SQL 路径
2. Python 路径

---

## 5. 当前问题

1. workspace 下多表 Python 联合执行还未真正完成
2. 前端对 artifact 的展示还比较弱
3. AI 结果虽然已经可落库，但“回看与复用”体验还没形成闭环

---

## 6. 后续演进方向

analysis 模块后续重点不是“再加更多炫功能”，而是把已有链路做稳：

- 上下文更清晰
- 执行更可控
- 结果更可沉淀
- 错误更容易解释

长期看，analysis 应成为：

**把自然语言、业务上下文和执行引擎连接起来的核心编排层。**

---

## 7. 相关模块

- `dataset`
- `workspace`
- `chat`
- `python-executor`
