# Task 9 测试与验收报告

## 范围

本报告对应 `docs/superpowers/plans/2026-04-23-light-scheduling-mvp.md` 中的 Task 9，目标是完成 MVP 基线整体验证，并将前后端验证链路统一到可重复执行的命令上。

本次验收覆盖：
- 后端单元测试
- 前端单元测试
- 前端端到端测试
- 前端构建
- 前端统一验证命令
- 后端 `verify` 阶段联动前端验证

## 本次调整文件

- `backend/pom.xml`
- `frontend/package.json`
- `frontend/tsconfig.json`
- `frontend/vite.config.ts`

## 红灯现象、根因与修复

### 1. 后端测试命令失败

**现象**
- `./mvnw -pl backend test` 执行失败。

**根因**
- 当前仓库没有 Maven Wrapper，根目录不存在 `./mvnw`。

**修复**
- 将验证入口统一为系统 Maven：`mvn -pl backend test` 与 `mvn -pl backend verify`。
- 不新增 wrapper，保持当前仓库最小改动。

### 2. 前端单测误收集 e2e 用例

**现象**
- `corepack pnpm -C frontend test` 执行时，Vitest 把 `tests/e2e/*.spec.ts` 也当成单测收集。
- 触发 Playwright 报错：`Playwright Test did not expect test() to be called here`。

**根因**
- `frontend/vite.config.ts` 中的 Vitest 配置未限制测试收集范围。

**修复**
- 将配置入口切换为 `vitest/config`。
- 明确限制 Vitest 只收集 `src/**/*.spec.ts`。
- 明确排除 `tests/e2e/**`。

### 3. 前端构建失败

**现象**
- `corepack pnpm -C frontend build` 执行失败。
- TypeScript 报错涉及 `node:*` 模块、`Buffer`、`NodeJS`、`setImmediate`、`clearImmediate` 等类型缺失，以及与当前 Vite/Vitest 生态不匹配的解析行为。

**根因**
- 缺少 `@types/node`。
- `frontend/tsconfig.json` 仍使用较旧的 `moduleResolution: "Node"`。
- `types` 与 `lib` 配置不足。

**修复**
- 新增前端开发依赖：`@types/node`。
- 将 `moduleResolution` 调整为 `"Bundler"`。
- 在 `types` 中加入 `"node"`。
- 在 `lib` 中加入 `"ESNext"`。

### 4. 前端统一校验脚本首次失败

**现象**
- 首次写入的 `verify` 脚本使用嵌套 `pnpm` 调用时失败。
- Windows 环境下提示：`'pnpm' 不是内部或外部命令，也不是可运行的程序或批处理文件`。

**根因**
- 当前环境里，脚本内再次解析 `pnpm` 命令不稳定。

**修复**
- 将 `frontend/package.json` 中的 `verify` 改为直接调用可执行命令：
  - `vitest run`
  - `playwright test`
  - `vue-tsc --noEmit`
  - `vite build`

### 5. 后端 verify 未覆盖前端整体验证

**现象**
- 原有 Maven 构建不会自动串联前端验证。

**根因**
- `backend/pom.xml` 只有 Spring Boot 打包插件，没有在 `verify` 阶段挂接前端校验。

**修复**
- 在 `backend/pom.xml` 中新增 `exec-maven-plugin`。
- 绑定 `verify` 阶段执行 `corepack pnpm verify`，工作目录指向 `../frontend`。

## 最终验证命令

### 后端

```bash
mvn -pl backend test
mvn -pl backend verify
```

### 前端

```bash
corepack pnpm -C frontend test
corepack pnpm -C frontend test:e2e
corepack pnpm -C frontend build
corepack pnpm -C frontend verify
```

## 最终验证结果

### 后端测试

```bash
mvn -pl backend test
```

结果：通过。

其中本轮追加验证：

```bash
mvn -pl backend test -Dtest=LightscheduleApplicationTest,PlannerApiTest
```

结果：通过，确认：
- 后端在未显式开启 Flyway 时可无数据库启动。
- `/api/task-pool` 与 `/api/schedules/draft` 可返回最小非空样例数据。

### 前端单元测试

```bash
corepack pnpm -C frontend test
```

结果：通过。

### 前端端到端测试

```bash
corepack pnpm -C frontend test:e2e
```

结果：通过。

其中本轮追加验证：

```bash
corepack pnpm -C frontend exec playwright test tests/e2e/planner-workflow.spec.ts
corepack pnpm -C frontend exec playwright test tests/e2e/dashboard.spec.ts
```

结果：通过，包含：
- `计划员可以查看任务池、插入急单并打开发布确认`
- `计划员可以通过真实后端接口看到最小排程数据`
- `老板驾驶舱显示负荷总览`
- `老板驾驶舱可以通过真实后端接口看到负荷摘要`

### 前端构建

```bash
corepack pnpm -C frontend build
```

结果：通过。

### 前端统一验证命令

```bash
corepack pnpm -C frontend verify
```

结果：通过。

本轮 fresh 运行结果：
- Vitest：7 个测试文件、34 个测试全部通过。
- Playwright：4 条 e2e 全部通过。
- `vue-tsc --noEmit`：通过。
- `vite build`：通过。

### 后端 verify 串联前端验证

```bash
mvn -pl backend verify
```

结果：通过，且能够在后端 `verify` 生命周期中联动执行前端验证链路。

本轮 fresh 运行结果：
- 后端 JUnit：11 个测试全部通过。
- Maven `verify` 成功打包 `backend-0.0.1-SNAPSHOT.jar`。
- `exec-maven-plugin` 已成功联动执行前端 `verify`，其中包含：
  - Vitest：34/34 通过
  - Playwright：4/4 通过
  - 前端生产构建成功

### 真实联调补充说明

本轮在真实后端联调中额外完成了以下修复与确认：
- 将 `backend/src/main/resources/application.yml` 中的 `spring.flyway.enabled` 改为环境变量控制，避免默认启动时强依赖本地 SQL Server。
- 为启动链路补充 `LightscheduleApplicationTest` 回归测试。
- 为 `/api/task-pool`、`/api/schedules/draft` 与 `/api/dashboard/capacity-summary` 补充 `PlannerApiTest` 回归断言，锁定最小非空契约与驾驶舱摘要契约。
- 将 `TaskPoolController` 与 `ScheduleController` 的样例返回调整为最小非空数据，使 `/planner` 在不依赖 e2e route mock 时也能展示任务池与排程画板。
- 为 `frontend/playwright.config.ts` 补充回归测试，并将 Playwright `webServer` 调整为同时自举后端与前端，降低真实接口 e2e 对会话外 8080 进程的依赖。
- 处理了本地 8080 旧 Java 进程占用导致的“代码已修复但页面仍为空”问题，并在重启后用真实接口再次确认返回值。

## 验收结论

Task 9 目标已完成：
- 前后端验证链路可重复执行。
- 前端单测、e2e、构建全部打通。
- 后端 `verify` 已接入前端统一验证。
- `/planner` 已能通过真实后端接口展示最小非空任务池与排程数据。
- `/dashboard` 已能通过真实后端接口展示负荷摘要，且真实接口链路已被后端回归与前端 e2e 同时覆盖。
- 当前 MVP 基线达到可验证、可回归的冻结状态。

## 补充说明

- 根项目 `pom.xml` 中的 `mssql-jdbc.version` 继续保持为 `13.4.0.jre11`，本次未改动。
- 本次修复遵循最小改动原则，未额外引入与 Task 9 无关的重构。
