# Phase 6 Artifact Management Design

P6-06 的目标是把 workspace 内的历史分析结果从“最近记录展示”推进到“可管理的分析资产”。P6-06 先完成前端可用的刷新、搜索、schema 筛选和详情入口优化；最终收尾阶段已补齐归档、恢复和软删除的后端接口与前端入口。

## Current Implementation

已落地能力：

- `GET /api/artifacts/recent?groupId=...&limit=...` 继续作为 workspace 历史分析列表来源。
- `GET /api/artifacts/{id}` 继续作为详情回放来源。
- workspace 页面历史结果区支持：
  - 手动刷新列表。
  - 搜索 query、summary、generated code、chart type、created time 和 schema label。
  - 按 `all` / `Phase 6` / `Legacy` 筛选。
  - 明确的“查看详情”入口。
- “工作区资产 > 分析结果”复用同一套列表筛选和详情入口。
- 最近 artifact 拉取数量从 8 提升到 12，给筛选留出更实用的管理范围。
- 收尾阶段新增状态管理：
  - `ACTIVE` / `ARCHIVED` / `DELETED` 状态列。
  - 列表支持按状态筛选。
  - 详情弹窗支持归档、恢复、软删除。
  - 软删除不物理删除 artifact detail，仍可从删除状态恢复。

## Archive / Delete API

当前不直接硬删除历史分析结果，而是引入状态字段：

| Field | Type | Purpose |
| --- | --- | --- |
| `artifact_status` | `VARCHAR` | `ACTIVE` / `ARCHIVED` / `DELETED` |
| `archived_at` | `TIMESTAMP` | 归档时间 |
| `deleted_at` | `TIMESTAMP` | 软删除时间 |
| `updated_at` | `TIMESTAMP` | 管理状态变更时间 |

兼容策略：

- 旧行状态为空时按 `ACTIVE` 处理。
- `/recent` 默认只返回 `ACTIVE`。
- 归档和删除都不改动 `analysis_report_json`、evidence、execution logs、validation report 或 risk notices。
- 真正物理删除仅保留给后续维护命令或管理员接口。

已实现接口：

```text
GET /api/artifacts/recent?groupId=...&limit=...&status=ACTIVE&query=...
GET /api/artifacts/{id}
PATCH /api/artifacts/{id}/archive
PATCH /api/artifacts/{id}/restore
DELETE /api/artifacts/{id}
```

接口语义：

- `archive`：将 `artifact_status` 置为 `ARCHIVED`，设置 `archived_at` 和 `updated_at`。
- `restore`：将 `ARCHIVED` 或 `DELETED` 恢复为 `ACTIVE`，清空对应时间字段或保留为审计字段，二者需在实现前二选一。
- `DELETE`：默认软删除，将 `artifact_status` 置为 `DELETED`，设置 `deleted_at` 和 `updated_at`。
- detail endpoint 对 `ARCHIVED` 仍可读；对 `DELETED` 是否可读应由权限策略决定。

## Frontend Follow-Up

后续仍可在当前管理入口上扩展：

- 将列表筛选下沉到服务端 query 搜索、排序和分页。
- artifact card 上增加快捷归档入口。
- 对管理操作增加 workspace membership / ownership 校验说明。
- 为 deleted detail 是否可读增加权限策略。

## Verification Checklist

当前前端体验和后端状态流验证：

```powershell
npm run lint
npm run build
mvn -f backend\pom.xml -DskipTests compile
mvn -f backend\pom.xml '-Dtest=ArtifactServiceTest,DuckDBRepositoryArtifactTest' test
python harness\run_benchmarks.py --suite phase4-regression --list-cases
git diff --check
```

已覆盖：

- 默认 recent 不返回 archived/deleted。
- status 筛选可返回 archived。
- archive / restore / delete 不破坏 detail 回读。
- legacy artifact 状态为空时仍按 active 显示。
