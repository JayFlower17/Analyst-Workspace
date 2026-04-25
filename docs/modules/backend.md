# 模块说明：backend

## 1. 模块定位

`backend/` 是整个平台的业务中枢。

它负责：

- 暴露 API
- 组织业务流程
- 编排 AI 分析逻辑
- 管理元数据和业务对象
- 协调 DuckDB 与 Python 执行器

---

## 2. 负责什么

后端当前主要承担以下职责：

1. 认证与鉴权
2. 数据集上传和元数据管理
3. 聊天会话管理
4. workspace 管理
5. 分析请求编排
6. 安全校验
7. 结果沉淀

---

## 3. 不负责什么

后端不负责：

- 前端页面交互细节
- Python 沙箱内部执行细节
- 浏览器端状态管理

---

## 4. 当前结构

当前后端已经形成比较清晰的分层：

- `controller/`：API 入口
- `service/`：业务流程
- `ai/`：AI 分析相关能力
- `repository/`：DuckDB 访问
- `security/`：JWT 与 SQL 白名单
- `model/`：实体与 DTO

---

## 5. 当前状态

后端已经不是单纯的“NL2SQL demo”，而是一个具备领域模型的服务端：

- `dataset`
- `chat session`
- `workspace group`
- `dataset relation`
- `analysis artifact`

这说明后端已经具备长期演进基础。

---

## 6. 当前问题

1. 命名还有历史痕迹
2. 一些能力已开接口但实现还不完整
3. artifact 能力已落库，但前端消费还不强
4. 安全边界仍需持续收紧

---

## 7. 后续演进方向

后端后续应继续稳定在“编排层”角色上：

- 前端只管体验
- 后端负责业务对象和分析主流程
- Python 执行器只做受限执行

---

## 8. 相关模块

- `auth`
- `dataset`
- `chat`
- `workspace`
- `analysis`
- `python-executor`
