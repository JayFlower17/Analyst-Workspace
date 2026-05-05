# frontend-next 迁移文档

状态：已完成。旧 `frontend/` Vue 版本已经下线并从仓库移除，当前只保留 `frontend-next/` 作为唯一前端主线。以下内容作为迁移过程和决策背景保留。

## 1. 目标

本项目的前端主线为 `frontend-next/`，已替代旧 `frontend/`。

迁移不是简单地把 Vue 重写成 Next.js，而是借这次切换完成三件事：

1. 统一产品主线：以 `chat -> workspace -> artifact` 为核心流程。
2. 统一前端架构：只保留一套持续演进的前端实现。
3. 统一用户心智：页面命名、导航结构、术语和操作路径保持一致。

当前结论：

- `frontend/`：旧 Vue 版本，已下线。
- `frontend-next/`：当前唯一前端主线。

因此采用策略：

**迁移结论：Next 版已成为唯一主线，Vue 版不再保留。**

---

## 2. 为什么要迁移到 frontend-next

### 2.1 当前 Vue 版的优势

`frontend/` 目前已经接好了较多后端能力，包含：

- 登录注册
- 数据集上传与管理
- 工作区创建
- 工作区描述编辑
- 数据表描述编辑
- 数据预览
- 会话聊天分析
- 升级为 workplace

对应文件：

- `frontend/src/api/index.js`
- `frontend/src/views/Login.vue`
- `frontend/src/views/Datasets.vue`
- `frontend/src/views/Chat.vue`
- `frontend/src/views/Analysis.vue`

### 2.2 当前 Next 版的优势

`frontend-next/` 更符合项目未来想成为的“智能数据分析工作台”：

- 路由结构更清晰
- 页面职责划分更自然
- UI 风格更产品化
- `chat / datasets / workplace` 三条主路径已经显式成型
- 更适合作为后续长期演进底座

对应文件：

- `frontend-next/src/app/(dashboard)/chat/page.tsx`
- `frontend-next/src/app/(dashboard)/datasets/page.tsx`
- `frontend-next/src/app/(dashboard)/workplace/page.tsx`
- `frontend-next/src/lib/api/client.ts`

### 2.3 为什么现在还不能立刻删掉 Vue 版

Next 版还存在这些缺口：

1. 登录页未完成  
   `frontend-next/src/app/login/page.tsx` 当前只是重定向。

2. 数据治理能力未补齐  
   例如数据表描述编辑、工作区描述编辑、数据预览。

3. 工作区关系管理未补齐  
   后端已有 group relation 接口，但 Next 前端还没有完整操作入口。

4. 功能覆盖仍低于 Vue 版  
   Vue 已经是“全链路可用”，Next 还更像“新主线雏形”。

---

## 3. 最终产品主线

迁移完成后，前端应该围绕下面这条主线组织：

1. **Chat**
   - 创建会话
   - 上传数据
   - 自然语言提问
   - 快速获得分析结果

2. **Workspace**
   - 将会话中的数据集升级为工作区
   - 查看工作区内数据表
   - 维护表描述、业务描述、关系
   - 发起多表分析

3. **Artifacts / Analysis History**
   - 沉淀分析结果
   - 可回看摘要、代码/SQL、结果预览
   - 为后续报表化、复用、协作做准备

4. **Datasets**
   - 上传
   - 浏览
   - 预览
   - 删除
   - 描述维护

---

## 4. 迁移原则

### 4.1 先补能力，再切流量

不要先删 Vue 再补 Next。  
正确顺序是：

1. 在 Next 中补齐关键能力
2. 用 Next 跑通完整主流程
3. 再下线 Vue

### 4.2 迁移过程中只允许一个“未来主线”

从现在开始，功能新增默认优先写到 `frontend-next/`。  
`frontend/` 只做：

- 必要 bug 修复
- 阻塞使用的小修
- 不做新的大功能扩展

### 4.3 术语统一

迁移过程中必须统一这些命名：

- `workspace` / `workplace` 二选一
- 页面标题、按钮文案、接口调用语义保持一致
- 不要在新旧界面里混用两套术语

建议统一用：**workspace**

原因：

- 与后端 `group` 概念更容易建立映射
- 行业内更常见
- 比 `workplace` 更贴近“分析工作区”语义

如果后端暂时不改接口命名，前端显示文案也应先统一为 `workspace`。

---

## 5. 迁移范围

### 5.1 必须迁移的能力

#### A. 认证

- 登录
- 注册
- token 持久化
- 401 失效处理

#### B. Chat

- 会话列表
- 会话切换
- 会话内消息加载
- 会话内上传数据
- 指定数据表分析
- 升级为 workspace

#### C. Dataset 管理

- 数据集列表
- 按 workspace 过滤
- 上传
- 删除
- 数据预览
- 数据表描述编辑

#### D. Workspace 管理

- 工作区列表
- 新建工作区
- 工作区描述编辑
- 工作区内数据集查看
- relation 列表查看
- relation 创建/编辑/删除

#### E. Analysis

- 单表分析
- 多表分析
- focusDatasetIds 选择
- 结果表格展示
- 图表展示
- SQL / 代码展示
- 摘要展示
- 执行耗时展示

#### F. Artifact 能力

- 至少展示最近一次结果
- 后续支持分析历史和结果回看

### 5.2 可后置迁移的能力

- 国际化进一步完善
- 视觉细节打磨
- 高级图表配置
- artifact 历史中心
- 深度结果复用

---

## 6. 分阶段迁移计划

## 阶段一：补齐基础可用能力

目标：让 `frontend-next/` 成为“可以独立跑主流程”的版本。

### 本阶段任务

1. 补全登录/注册页面
2. 校验 token 恢复和未登录跳转逻辑
3. 校验首页路由和 dashboard 路由可用性
4. 确保 chat、datasets、workspace 三个页面都能进入
5. 统一 API 错误提示和 loading 状态

### 验收标准

- 新用户可注册并登录
- 老用户刷新页面后仍保持登录
- 未登录访问受保护页面会被正确拦截
- Chat 页面可创建会话、发送消息、上传文件

---

## 阶段二：补齐数据治理与工作区能力

目标：让 Next 版覆盖 Vue 版最关键的“管理能力”。

### 本阶段任务

1. 数据表描述编辑
2. 工作区描述编辑
3. 数据预览
4. 数据集删除
5. relation 查看
6. relation 创建/编辑/删除
7. 术语统一为 `workspace`

### 验收标准

- 不需要打开 Vue，也能完成数据集管理闭环
- 不需要打开 Vue，也能完成 workspace 管理闭环
- 用户可以在 Next 中完成“上传 -> 描述 -> 关系 -> 分析”的完整链路

---

## 阶段三：补齐分析沉淀能力

目标：让 Next 版不只是“能查”，而是“能沉淀”。

### 本阶段任务

1. 最近一次分析结果展示优化
2. artifact 列表页或侧边历史区
3. 回看摘要、结果预览、生成 SQL/代码
4. 结果状态空态和失败态统一

### 验收标准

- 用户能方便回看最近分析结果
- 分析结果不是一次性消费，而是可复用资产

---

## 阶段四：切换主前端

目标：正式停止维护 Vue 版。

### 本阶段任务

1. 确认 Next 功能覆盖达到替代标准
2. 更新 README 和启动说明
3. 将团队约定改为“默认只维护 frontend-next”
4. 将 Vue 标记为 deprecated
5. 最终移除 `frontend/`

### 验收标准

- 所有日常使用路径均可在 Next 完成
- Vue 不再承担任何关键流程
- 新功能只进入 Next

---

## 7. 推荐优先级

如果接下来只做最重要的几件事，建议顺序如下：

1. 完成 Next 登录/注册页
2. 补数据表描述编辑
3. 补工作区描述编辑
4. 补数据预览
5. 补 relation 管理
6. 补 artifact/history 展示
7. 统一术语与导航

这是最划算的一条路，因为它直接把 Next 从“好看的新壳”推进到“可接班主前端”。

---

## 8. 前后端协同建议

为了让迁移更顺，建议同步处理这些问题：

### 8.1 文案和领域命名统一

前端展示统一使用：

- Chat
- Datasets
- Workspace
- Analysis
- Artifacts

后端内部如果仍保留 `group`，前端可通过类型和 API 封装做语义转换。

### 8.2 API 封装收口

`frontend-next/src/lib/api/client.ts` 应继续扩展为唯一 API 入口，覆盖：

- auth
- dataset
- workspace
- relations
- chat
- analysis
- artifacts

避免页面里散落 fetch 逻辑。

### 8.3 类型先行

优先补齐这些类型：

- Relation
- DatasetMetadata
- DatasetPreviewRow
- Artifact
- WorkspaceDetail

这样后续页面开发会稳很多。

---

## 9. 删除 Vue 版前的检查清单

只有当下面这些条件都满足时，才能删除 `frontend/`：

- [ ] Next 已支持登录注册
- [ ] Next 已支持数据集上传、删除、预览
- [ ] Next 已支持数据表描述编辑
- [ ] Next 已支持工作区创建与描述编辑
- [ ] Next 已支持 relation 管理
- [ ] Next 已支持 chat 主流程
- [ ] Next 已支持 workspace 分析主流程
- [ ] README 已切换到 Next 启动方式
- [ ] 团队已确认 Vue 不再接收新功能

---

## 10. 当前决策

当前项目决策如下：

1. `frontend-next/` 是未来唯一主线。
2. `frontend/` 在迁移完成前继续保留。
3. 新增功能默认优先进入 `frontend-next/`。
4. `frontend/` 只做维持可用的小修，不再承担长期演进。

---

## 11. 下一步建议

建议从一个很具体的迁移小目标开始：

**先完成 `frontend-next` 的登录/注册和鉴权闭环。**

原因：

- 它是所有页面接班的前提
- 完成后你就能真正只在 Next 里继续补功能
- 这是从“双前端并存”迈向“单主线迁移”的第一步
