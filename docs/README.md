# 项目文档

本目录用于沉淀项目的长期说明文档，替代原先零散的 `Plan/` 内容。

目标不是写一堆大而全材料，而是保留对后续开发真正有帮助的文档：

- 项目方向和产品主线
- 前端迁移决策
- 模块职责边界
- 后续重构和协作时的统一语义

---

## 目录结构

### 总览类

- `phase1-closeout.md`  
  Phase 1 正式收尾文档，记录已完成项、验证结果和本地运行方式。

- `phase2-kickoff.md`  
  Phase 2 起步文档，记录文档资产建模、上传接口和本地验证方式。

- `phase3-kickoff.md`
  Phase 3 起步文档，记录统一上下文模型、workspace context assembler 接入和下一步演进入口。

- `phase3-closeout.md`
  Phase 3 收尾文档，记录统一上下文层、联合问题验证、benchmark 结果和 Phase 4 入口建议。

- `phase4-kickoff.md`
  Phase 4 起步文档，记录问题路由层、route decision 响应和 benchmark 断言。

- `phase4-closeout.md`
  Phase 4 收尾文档，记录 Agent 编排、执行日志、质量防线、统一输出层和验证结果。

- `phase5-closeout.md`
  Phase 5 收尾文档，记录 harness 回归能力、常用命令、report diff、验证结果和 Phase 6 交接建议。

- `phase6-artifact-schema-design.md`
  Phase 6 artifact schema 设计文档，记录分析报告、证据、执行过程、校验和风险提示的持久化方案。

- `phase6-artifact-management-design.md`
  Phase 6 artifact 管理设计文档，记录历史分析列表管理、筛选、详情入口和后续归档/删除接口方案。

- `phase6-closeout.md`
  Phase 6 收尾文档，记录 artifact 持久化、详情回放、历史分析管理、回归报告和已知限制。

- `phase5-6-task-plan.md`
  Phase 5 和 Phase 6 的 15 个小任务拆分，作为后续连续推进的任务清单。

- `frontend-next-migration.md`  
  前端迁移路线，说明为什么保留 `frontend/`、为什么未来转向 `frontend-next/`。

- `frontend-redesign-blueprint.md`  
  前端改版草图，定义问答、工作区、数据仓三个模块的页面职责、结构和文案精简原则。

### 模块说明

- `modules/platform.md`
- `modules/frontend.md`
- `modules/backend.md`
- `modules/auth.md`
- `modules/dataset.md`
- `modules/chat.md`
- `modules/workspace.md`
- `modules/analysis.md`
- `modules/python-executor.md`

---

## 文档使用原则

每个模块说明尽量回答 5 个问题：

1. 这个模块负责什么
2. 这个模块不负责什么
3. 它依赖谁、服务谁
4. 当前已经做到哪一步
5. 后续准备怎么演进

---

## 当前建议

后续新增说明文档，优先放在 `docs/modules/` 下，保持每个模块一页。

如果某个主题跨多个模块，再单独补总览文档，不要把模块说明写成会议纪要或任务堆积区。
