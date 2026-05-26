# 轻排程 Light Schedule

轻排程是一个面向制造排程场景的 MVP 项目，当前聚焦于“从待排任务池进入排程工作台，完成初排、急单重排、风险查看与确认回写”的最小闭环。

当前版本已经完成 MVP 基线验证，可作为试点演示和下一轮真实联调开发的基础版本。

## 当前 MVP 范围

已覆盖能力：
- 待排任务池
- 自动初排
- 硬约束校验基础能力
- 急单重排建议
- 回写确认入口
- 负荷摘要与老板驾驶舱页面
- 能力分析概览页面
- 前后端统一验证命令

当前限制：
- 当前接口以样例数据和内存闭环为主，重点在于打通排程工作台、驾驶舱和能力分析页的最小产品链路
- 尚未完成真实数据库持久化闭环
- 尚未完成完整 Kingdee 双向真实写回
- 前端部分页面仍处于从静态壳子向真实接口联调演进的阶段

## 技术栈

### 后端
- Java 21
- Spring Boot 3.3
- Maven
- SQL Server
- Flyway
- MyBatis-Plus
- JUnit 5 / AssertJ

### 前端
- Vue 3
- Vue Router
- Vite
- TypeScript
- Vitest
- Playwright
- pnpm（通过 Corepack）

## 目录结构

- `backend/`：Spring Boot 后端
- `frontend/`：Vue 前端
- `docs/`：计划、测试报告、交付说明
- `轻排程.md`：原始业务说明

## 环境要求

- JDK 21
- Maven 3.9+
- Node.js 20+
- Corepack
- SQL Server（仅在需要执行迁移或接真实数据库联调时准备）

## 本地启动

### 启动后端

```bash
mvn -pl backend spring-boot:run
```

默认演示启动不会启用 Flyway，因此不会因为本地未准备 SQL Server 迁移环境而阻塞启动；如果需要显式执行迁移，可先设置环境变量 `LIGHT_SCHEDULE_FLYWAY_ENABLED=true`。

后端默认配置位于 [backend/src/main/resources/application.yml](backend/src/main/resources/application.yml)，默认接口基址为 `http://127.0.0.1:8080/api`。

### 启动前端

```bash
corepack pnpm -C frontend dev
```

启动后可通过 `http://127.0.0.1:5173` 访问页面。

## 验证命令

### 后端

默认入口：

```bash
mvn -pl backend test
mvn -pl backend verify
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

脚本内部会执行：
- `mvn -f "/d/工作文件夹/code/排程/pom.xml" -pl backend -DforkCount=0 -Dexec.skip=true verify`
- 前端 `vitest`
- 前端 `playwright`
- 前端 `vue-tsc --noEmit`
- 前端 `vite build`

如果 `18080` 后端或 `4174` 前端已经在本地运行，脚本会直接复用；否则会临时拉起并在结束后自动清理。

说明：当前环境里，Surefire fork 和 `exec-maven-plugin` 在默认 `verify` 链路上会调用 `cmd.exe`，可能触发 `CreateProcess error=740`；上面的脚本基于本轮 fresh 跑通的替代命令封装，可作为演示前的一键验证入口。

### 前端

默认入口：

```bash
corepack pnpm -C frontend test
corepack pnpm -C frontend test:e2e
corepack pnpm -C frontend build
corepack pnpm -C frontend verify
```

当前 Windows shell 下也可以直接单独运行：

```bash
cd "/d/工作文件夹/code/排程/frontend"
./node_modules/.bin/vitest --config ./vite.config.ts run
PLAYWRIGHT_DISABLE_WEBSERVER=1 ./node_modules/.bin/playwright test
./node_modules/.bin/vue-tsc --noEmit
./node_modules/.bin/vite build
```

其中 `mvn -pl backend verify` 会在 Maven `verify` 阶段联动执行前端 `verify`；如果当前 shell 下默认链路被 `cmd.exe` 拦住，优先使用仓库根目录下的 `./verify-mvp.sh`。

## 主要页面入口

- 排程工作台：`http://127.0.0.1:5173/planner`
- 老板驾驶舱：`http://127.0.0.1:5173/dashboard`
- 能力分析页：`http://127.0.0.1:5173/capacity-analysis`

前端路由定义见 [frontend/src/router/index.ts](frontend/src/router/index.ts)。

## 关键文档

- 实施计划：[docs/superpowers/plans/2026-04-23-light-scheduling-mvp.md](docs/superpowers/plans/2026-04-23-light-scheduling-mvp.md)
- Task 9 测试与验收报告：[docs/superpowers/task-9-test-report.md](docs/superpowers/task-9-test-report.md)
- 试点/演示交付说明：[docs/mvp-demo-guide.md](docs/mvp-demo-guide.md)

## 当前代码基线说明

后端已经具备以下接口雏形：
- 任务池：[backend/src/main/java/com/lightschedule/web/TaskPoolController.java](backend/src/main/java/com/lightschedule/web/TaskPoolController.java)
- 初排草稿：[backend/src/main/java/com/lightschedule/web/ScheduleController.java](backend/src/main/java/com/lightschedule/web/ScheduleController.java)
- 急单重排：[backend/src/main/java/com/lightschedule/web/ReplanController.java](backend/src/main/java/com/lightschedule/web/ReplanController.java)
- 回写发布：[backend/src/main/java/com/lightschedule/web/WritebackController.java](backend/src/main/java/com/lightschedule/web/WritebackController.java)
- 驾驶舱摘要：[backend/src/main/java/com/lightschedule/web/DashboardController.java](backend/src/main/java/com/lightschedule/web/DashboardController.java)
- 能力分析概览：[backend/src/main/java/com/lightschedule/web/CapacityAnalysisController.java](backend/src/main/java/com/lightschedule/web/CapacityAnalysisController.java)

前端当前主要入口：
- 工作台页面：[frontend/src/views/PlannerWorkbenchView.vue](frontend/src/views/PlannerWorkbenchView.vue)
- 驾驶舱页面：[frontend/src/views/BossDashboardView.vue](frontend/src/views/BossDashboardView.vue)
- 能力分析页面：[frontend/src/views/CapacityAnalysisView.vue](frontend/src/views/CapacityAnalysisView.vue)

## 下一步建议

推荐下一轮优先推进“真实联调”的剩余薄弱点：
- 继续深化排程工作台的数据闭环与交互细节
- 将当前样例/内存接口逐步替换为更贴近真实业务的数据来源
- 在已打通的工作台、驾驶舱、能力分析页基础上，继续补强联调测试与交付材料
