# Phase 5 Closeout

Phase 5 将 `harness/` 从一个可运行 benchmark 脚本整理成了后续 Phase 6 可复用的回归安全网。它现在可以按 case、tag、suite 选择用例，输出可归因的报告摘要，并对两次 run report 做差异对比。

## 完成范围

### P5-01 Case metadata 与标签筛选

- benchmark case 增加 `description` 和非空 `tags`
- `run_benchmarks.py` 支持 `--tag`
- list/report 输出 case metadata 和 `selected_tags`

### P5-02 Case registry 与格式说明

- 新增 [case-registry.json](F:\data-analysis-platform\harness\benchmarks\case-registry.json)
- 新增 [benchmarks/README.md](F:\data-analysis-platform\harness\benchmarks\README.md)
- registry 记录 route、executor、source、assertion 和 tags 覆盖范围

### P5-03 Phase 4 regression suite

- 新增 [phase4-regression.json](F:\data-analysis-platform\harness\suites\phase4-regression.json)
- `run_benchmarks.py` 支持 `--suite`
- suite report 文件名使用 suite id

### P5-04 Phase 4 输出断言

- 支持 `analysis_report_evidence_exact`
- 支持 `analysis_report_evidence_min`
- 支持 `validation_report_exact`
- 支持 `risk_notices_exact`
- Phase 4 regression case 已补齐 evidence、validation、risk 断言

### P5-05 失败归因字段

- `CaseResult` 和 report result 增加 `failure_stage`
- 当前阶段覆盖 `api`、`result`、`summary`、`context`、`retrieval`、`route`、`plan`、`execution`、`report`、`validation`、`risk`、`cleanup`

### P5-06 报告摘要增强

- report 顶层新增 `summary.byCategory`
- report 顶层新增 `summary.byTag`
- report 顶层新增 `summary.byFailureStage`

### P5-07 Report diff 对比

- 新增 [compare_reports.py](F:\data-analysis-platform\harness\compare_reports.py)
- 支持文本输出和 `--json`
- 对比 pass/fail、聚合 bucket、case 状态、耗时、route、plan、execution、validation、evidence、risk
- 检测到 pass/fail regression 时退出码为 `2`

### P5-08 使用文档与收尾

- [harness/README.md](F:\data-analysis-platform\harness\README.md) 增加 Phase 5 日常回归流程
- 本文档收束 Phase 5 功能、验证命令、报告入口和已知限制
- [phase5-6-task-plan.md](F:\data-analysis-platform\docs\phase5-6-task-plan.md) 记录 P5-08 完成状态

## 常用工作流

### 1. 启动后端

benchmark 需要本地 backend 可访问。常用端口是 `8080` 或本地调试端口 `18080`，命令示例：

```powershell
mvn -Dspring-boot.run.profiles=local spring-boot:run
```

如果服务跑在 `18080`，后续命令使用：

```powershell
--base-url http://127.0.0.1:18080/api
```

### 2. 查看 suite 覆盖

```powershell
python harness\run_benchmarks.py --suite phase4-regression --list-cases
```

### 3. 运行 Phase 4 regression

```powershell
python harness\run_benchmarks.py --base-url http://127.0.0.1:18080/api --suite phase4-regression
```

报告会写入：

```text
harness/runs/phase4-regression-run-*.json
```

### 4. 对比两次报告

```powershell
python harness\compare_reports.py harness\runs\phase4-regression-run-20260503-193032.json harness\runs\phase4-regression-run-20260503-194831.json
```

需要给自动化或 CI 读取时：

```powershell
python harness\compare_reports.py --json harness\runs\phase4-regression-run-20260503-193032.json harness\runs\phase4-regression-run-20260503-194831.json
```

### 5. 定位失败

- 先看 report 顶层 `summary.byFailureStage`
- 再看失败 case 的 `failure_stage` 和 `reason`
- route / plan / report / validation / risk 类失败通常对应 Phase 4 输出契约变化
- api / cleanup 类失败通常优先检查服务状态、登录、测试资源创建和清理

## 当前验证记录

已完成的关键验证命令：

```powershell
python -m py_compile harness\run_benchmarks.py
python -m py_compile harness\compare_reports.py
python harness\run_benchmarks.py --suite phase4-regression --list-cases
python harness\run_benchmarks.py --base-url http://127.0.0.1:18080/api --suite phase4-regression
python harness\compare_reports.py harness\runs\phase4-regression-run-20260503-193032.json harness\runs\phase4-regression-run-20260503-194831.json
python harness\compare_reports.py --json harness\runs\phase4-regression-run-20260503-193032.json harness\runs\phase4-regression-run-20260503-194831.json
git diff --check
```

最近一次完整 Phase 4 regression 报告：

- [phase4-regression-run-20260503-194831.json](F:\data-analysis-platform\harness\runs\phase4-regression-run-20260503-194831.json)

结果：

- `5 / 5` 通过
- report 顶层包含 `summary.byCategory`、`summary.byTag`、`summary.byFailureStage`
- compare report 未检测到 pass/fail regression

## 已知限制

- workspace/hybrid case 依赖本地 `groupId=1`、样例 dataset 和本地登录用户数据。
- LLM summary 文案和执行耗时有天然波动，当前 diff 工具只把稳定结构字段作为关键 excerpt 对比。
- harness 目前仍是本地回归工具，还没有接入正式 CI。
- report JSON 会持续累积在 `harness/runs/`，需要周期性挑选基线报告，避免目录变成临时结果堆。
- Phase 5 没有实现 artifact 持久化验证；这会进入 Phase 6。

## Phase 6 交接建议

Phase 6 做 artifact schema、analysis report 持久化、证据来源持久化、执行过程回放和前端 artifact detail 时，建议把 Phase 5 harness 当成每个小任务的默认回归步骤：

1. 改动前保留一个 baseline run report。
2. 改动后运行 `phase4-regression` suite。
3. 用 `compare_reports.py` 对比 baseline/current。
4. 如果新增 artifact API 或前端回放字段，补充新的 Phase 6 suite 或 tag，不要挤进 Phase 4 最小回归集。
