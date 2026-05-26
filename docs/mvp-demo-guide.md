# MVP 试点与演示说明

## 适用对象

本说明面向试点演示、业务沟通和内部交接，帮助快速说明当前 MVP 能做什么、怎么演示、还缺什么。

## 当前可以演示的内容

### 1. 排程工作台
入口：`/planner`

当前展示重点：
- 待排任务池区域
- 排程画板区域
- 风险与建议区域
- 急单插入弹窗入口
- 回写确认弹窗入口
- 基于真实后端最小样例接口的数据加载结果

### 2. 老板驾驶舱
入口：`/dashboard`

当前展示重点：
- 资源负荷总览
- 真实后端摘要加载结果

### 3. 能力分析页
入口：`/capacity-analysis`

当前展示重点：
- 资源负荷趋势
- 同组差异
- 高负荷时段
- 真实后端概览加载结果

## 推荐演示流程

1. 启动后端：

```bash
mvn -pl backend spring-boot:run
```

默认演示启动不会启用 Flyway，因此不要求本地先准备 SQL Server 迁移环境；如果需要显式执行迁移，可先设置环境变量 `LIGHT_SCHEDULE_FLYWAY_ENABLED=true`。

2. 启动前端：

```bash
corepack pnpm -C frontend dev
```

3. 打开排程工作台：
- 访问 `http://127.0.0.1:5173/planner`
- 展示任务池、画板、风险侧栏
- 点击“插入急单”展示急单入口
- 点击“确认回写”展示发布入口

4. 打开老板驾驶舱：
- 访问 `http://127.0.0.1:5173/dashboard`
- 展示资源负荷总览与当前负荷率

5. 打开能力分析页：
- 访问 `http://127.0.0.1:5173/capacity-analysis`
- 展示资源负荷趋势、同组差异与高负荷时段

## 验证命令

默认入口：

```bash
mvn -pl backend test
mvn -pl backend verify
corepack pnpm -C frontend verify
```

当前环境下优先使用的一键验证入口：

```bash
node ./verify-mvp.mjs
```

如果需要在成功后保留后端 / 前端启动日志，便于演示前排障，可以加：

```bash
node ./verify-mvp.mjs --keep-logs
```

如果在 Bash 包装器下只想查看可用参数说明，也可以运行：

```bash
./verify-mvp.sh --help
```

如果只想查看可用参数说明，可以运行：

```bash
node ./verify-mvp.mjs --help
```

如果只想定向跑某些 Playwright 用例，也可以把额外参数直接透传给 Playwright，例如：

```bash
node ./verify-mvp.mjs --grep smoke-check
```

这条命令适合只跑名称匹配 `smoke-check` 的 Playwright 用例，而不必重跑整套 e2e。

如果需要明确把后面的参数都交给 Playwright，也可以直接传：

```bash
node ./verify-mvp.mjs -- --grep smoke-check
```

如果需要带着浏览器窗口观察用例执行过程，也可以直接传：

```bash
node ./verify-mvp.mjs --headed
```

这条命令适合本地排查 Playwright 交互问题时，直接查看浏览器实际行为。

如果需要把项目范围和用例过滤组合起来，也可以直接传：

```bash
node ./verify-mvp.mjs --project chromium --grep smoke-check
```

如果只想在 Chromium 项目里保留日志，也可以直接传：

```bash
node ./verify-mvp.mjs --project chromium --keep-logs
```

这条命令适合只验证 Chromium 项目里匹配 `smoke-check` 的用例。

等价 Bash 入口，例如：

```bash
./verify-mvp.sh --grep smoke-check
```

这条命令适合在 Bash 环境下只跑匹配 `smoke-check` 的 Playwright 用例。

如果在 Bash 下需要带着浏览器窗口观察执行过程，也可以直接传：

```bash
./verify-mvp.sh --headed
```

如果在 Bash 下只想在 Chromium 项目里带着浏览器窗口观察执行过程，也可以直接传：

```bash
./verify-mvp.sh --project chromium --headed
```

如果在 Bash 下也需要组合项目范围和用例过滤，可以直接传：

```bash
./verify-mvp.sh --project chromium --grep smoke-check
```

如果在 Bash 下只想在 Chromium 项目里保留日志，也可以直接传：

```bash
./verify-mvp.sh --project chromium --keep-logs
```

如果在 Bash 下需要明确把后面的参数都交给 Playwright，也可以直接传：

```bash
./verify-mvp.sh -- --grep smoke-check
```

Windows 命令行也可以直接运行，例如：

```bat
verify-mvp.cmd
```

这条命令适合作为 Windows 命令行下的一键默认验证入口。

如果只想验证 Chromium 项目配置是否正常，也可以直接传：

```bat
verify-mvp.cmd --project chromium
```

这条命令适合只验证 Chromium 项目配置是否正常，而不必同时覆盖其他 Playwright project。

如果在 Windows 命令行里需要带着浏览器窗口观察执行过程，也可以直接传：

```bat
verify-mvp.cmd --headed
```

如果在 Windows 命令行里也需要组合项目范围和用例过滤，可以直接传：

```bat
verify-mvp.cmd --project chromium --grep smoke-check
```

如果在 Windows 命令行里需要明确把后面的参数都交给 Playwright，也可以直接传：

```bat
verify-mvp.cmd -- --grep smoke-check
```

这个 `.cmd` 入口会直接调用 `node ./verify-mvp.mjs`。

除脚本自带的 `--keep-logs` 和 `--help` 外，其余参数都会透传给 Playwright，并追加在 `playwright test` 后面；因此也可以从仓库根直接传 `--grep`、`--project`、`--headed` 这类 Playwright 参数。

脚本内部会执行本轮已经 fresh 跑通的替代命令：
- 后端 `mvn -f "/d/工作文件夹/code/排程/pom.xml" -pl backend -DforkCount=0 -Dexec.skip=true verify`
- 前端 `vitest`
- 前端 `playwright`
- 前端 `vue-tsc --noEmit`
- 前端 `vite build`

说明：默认 Maven / pnpm 验证链在当前 shell 下可能被 `cmd.exe` 740 拦住；`./verify-mvp.sh` 已把稳定替代链路收成一键入口，适合作为演示前检查命令。

详细验证结果见 [docs/superpowers/task-9-test-report.md](superpowers/task-9-test-report.md)。

## 当前不包含的内容

- 完整数据库持久化业务闭环
- 完整 Kingdee 双向写回
- 生产级鉴权、审计、发布流程
- 完整业务主数据接入与现场化配置

## 交付基线说明

当前版本适合作为：
- 业务流程评审基线
- 前后端真实联调起点
- 下一轮需求开发的骨架版本

不建议直接作为生产版本投放。
