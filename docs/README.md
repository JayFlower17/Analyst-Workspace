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

- `frontend-next-migration.md`  
  前端迁移路线，说明为什么保留 `frontend/`、为什么未来转向 `frontend-next/`。

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
