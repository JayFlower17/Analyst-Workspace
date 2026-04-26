# Phase 1 收尾文档

## 1. 文档目的

本文档用于正式收尾 Phase 1，明确三件事：

1. 当前已经完成了什么
2. 当前已经真实验证了什么
3. 本地应该如何运行和验收

Phase 1 的目标是：

**先把结构化分析主线收稳，让项目成为一个可运行、可验证、可继续演进的 workspace 式分析平台。**

---

## 2. Phase 1 完成情况

### 2.1 前端主线

`frontend-next/` 已经从“新骨架”推进到了“可承担主流程的前端主线候选”。

已完成：

- 登录页
- 注册页
- token 持久化
- 未登录访问受保护页面跳转
- `/workspace` 作为统一主线路由
- `/workplace` 保留兼容跳转
- `workspace` 术语在 Next 主线中完成统一

### 2.2 数据集主流程

已完成：

- 数据集上传
- 数据集列表
- 数据预览
- 数据集删除
- 数据表描述编辑

### 2.3 Workspace 主流程

已完成：

- workspace 创建入口
- workspace 描述编辑
- workspace 内数据集列表
- relation 列表展示
- relation 创建
- relation 编辑
- relation 删除
- relation 自动识别入口接入
- relation 表单字段建议
- workspace 内快速设分析焦点

### 2.4 分析主流程

已完成：

- 单表分析请求闭环
- workspace 多表分析请求闭环
- 分析结果表格展示
- 摘要展示
- SQL / Python 代码展示
- 推荐图表展示
- 执行耗时展示
- 多表 Python 限制提示

### 2.5 Artifact 最小闭环

已完成：

- 后端返回 `artifactId`
- 最近分析结果可回看
- workspace / chat 页面可展示最近 artifact
- 后端最近 artifact 查询接口

### 2.6 Harness 最小版本

已完成：

- `structured` benchmark 目录
- `workspace` benchmark 目录
- 最小批量运行脚本
- 结果报告输出
- 本地认证自动化
- 本地代理绕过
- workspace benchmark 通过数据集名称解析真实 ID

---

## 3. Phase 1 关键代码落点

### 前端

- [frontend-next/src/app/login/page.tsx](F:\data-analysis-platform\frontend-next\src\app\login\page.tsx)
- [frontend-next/src/app/register/page.tsx](F:\data-analysis-platform\frontend-next\src\app\register\page.tsx)
- [frontend-next/src/app/(dashboard)/datasets/page.tsx](F:\data-analysis-platform\frontend-next\src\app\(dashboard)\datasets\page.tsx)
- [frontend-next/src/app/(dashboard)/workspace/page.tsx](F:\data-analysis-platform\frontend-next\src\app\(dashboard)\workspace\page.tsx)
- [frontend-next/src/app/(dashboard)/chat/page.tsx](F:\data-analysis-platform\frontend-next\src\app\(dashboard)\chat\page.tsx)
- [frontend-next/src/lib/api/client.ts](F:\data-analysis-platform\frontend-next\src\lib\api\client.ts)

### 后端

- [backend/src/main/java/com/analysis/service/AnalysisService.java](F:\data-analysis-platform\backend\src\main\java\com\analysis\service\AnalysisService.java)
- [backend/src/main/java/com/analysis/service/ArtifactService.java](F:\data-analysis-platform\backend\src\main\java\com\analysis\service\ArtifactService.java)
- [backend/src/main/java/com/analysis/controller/ArtifactController.java](F:\data-analysis-platform\backend\src\main\java\com\analysis\controller\ArtifactController.java)
- [backend/src/main/java/com/analysis/config/MilvusConfig.java](F:\data-analysis-platform\backend\src\main\java\com\analysis\config\MilvusConfig.java)
- [backend/src/main/java/com/analysis/service/MetadataService.java](F:\data-analysis-platform\backend\src\main\java\com\analysis\service\MetadataService.java)
- [backend/src/main/resources/application-local.yml](F:\data-analysis-platform\backend\src\main\resources\application-local.yml)

### Harness

- [harness/run_benchmarks.py](F:\data-analysis-platform\harness\run_benchmarks.py)
- [harness/benchmarks/structured](F:\data-analysis-platform\harness\benchmarks\structured)
- [harness/benchmarks/workspace](F:\data-analysis-platform\harness\benchmarks\workspace)

---

## 4. 验证结果

### 4.1 前端静态验证

已验证通过：

- `frontend-next` `npm run lint`
- `frontend-next` `npm run build`

### 4.2 后端编译验证

已验证通过：

- `backend` `mvn -DskipTests compile`

### 4.3 Harness 验证

#### Structured

结果：

- 3 / 3 通过

报告：

- [structured-run-20260425-225647.json](F:\data-analysis-platform\harness\runs\structured-run-20260425-225647.json)

#### Workspace

结果：

- 2 / 2 通过

报告：

- [workspace-run-20260426-003434.json](F:\data-analysis-platform\harness\runs\workspace-run-20260426-003434.json)

### 4.4 本轮环境问题与处理结论

Phase 1 收尾过程中，已经确认并解决了这些本地问题：

1. H2 本地库锁冲突  
   通过切换 `local` 数据库路径规避。

2. Milvus 导致本地启动受阻  
   在 `local` 环境下允许关闭向量库依赖，保留无 RAG 结构化分析链路。

3. 系统代理拦截本地接口  
   harness 默认绕过代理。

4. JWT secret 长度不足导致登录失败  
   `local` 环境已使用足够长度的本地 secret。

---

## 5. 本地运行方式

### 5.1 推荐启动方式

当前建议优先使用：

- [start2.bat](F:\data-analysis-platform\start2.bat)

它会以 `frontend-next` 为主前端启动项目。

### 5.2 后端单独启动

如果只想验证后端，建议使用 `local` profile：

```powershell
cd F:\data-analysis-platform\backend
mvn -Dspring-boot.run.profiles=local spring-boot:run
```

### 5.3 前端单独启动

```powershell
cd F:\data-analysis-platform\frontend-next
npm install
npm run dev
```

### 5.4 前端静态检查

```powershell
cd F:\data-analysis-platform\frontend-next
npm run lint
npm run build
```

### 5.5 Harness 运行

Structured：

```powershell
cd F:\data-analysis-platform
python harness\run_benchmarks.py --category structured
```

Workspace：

```powershell
cd F:\data-analysis-platform
python harness\run_benchmarks.py --category workspace
```

---

## 6. 建议的 Phase 1 人工验收路径

建议按下面顺序人工验证：

1. 启动 `start2.bat`
2. 打开 `frontend-next`
3. 注册或登录
4. 进入 `datasets`
5. 验证数据预览、描述编辑、删除
6. 进入 `workspace`
7. 验证 workspace 描述编辑
8. 验证 relation 创建 / 编辑 / 删除
9. 从 workspace 发起一次分析
10. 检查结果表格、摘要、代码、artifact 展示
11. 跑一轮 `structured` harness
12. 跑一轮 `workspace` harness

---

## 7. 对 Phase 1 的结论

当前可以认为：

**Phase 1 的核心目标已经完成。**

更具体地说：

- 结构化分析主线已打通
- `frontend-next` 已具备接班条件
- workspace 主流程已可用
- 最小 harness 已能真实跑通

仍然可继续优化，但这些更接近：

- 体验打磨
- 文档补充
- 迁移收尾

而不是 Phase 1 主线未完成。

---

## 8. 下一阶段建议

Phase 1 收尾后，建议优先进入：

**Phase 2：非结构化资产接入**

下一阶段重点应转向：

- 文档对象建模
- 文档上传与解析
- chunk 与索引
- workspace 内文档资产管理
