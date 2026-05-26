# Light Schedule (轻排程)

排程管理应用。前端 Vue 3 + 后端 Spring Boot，前后端分离架构。

## 技术栈

| 层级 | 技术 |
|------|------|
| 前端 | Vue 3, Vue Router, Vite, TypeScript |
| 前端测试 | Vitest (单元), Playwright (E2E), Vue Test Utils |
| 前端包管理 | pnpm (通过 corepack) |
| 后端 | Spring Boot 3.3.9, Java 21 |
| 数据访问 | MyBatis Plus 3.5.6 |
| 数据库 | SQL Server (mssql-jdbc 13.4.0) |
| 迁移 | Flyway |
| 构建 | Maven (多模块) |

## 项目结构

```
排程/
├── frontend/          # Vue 3 前端
│   ├── src/
│   │   ├── api/       # API 接口层
│   │   ├── components/# 组件
│   │   ├── router/    # 路由
│   │   ├── stores/    # Pinia/Vuex 状态
│   │   ├── views/     # 页面视图
│   │   ├── App.vue
│   │   └── main.ts
│   ├── tests/         # Playwright E2E 测试
│   └── package.json
├── backend/           # Spring Boot 后端
│   └── src/main/java/
├── pom.xml            # 根 Maven POM
└── CLAUDE.md          # 本文件
```

## 常用命令

### 前端 (frontend/)

```bash
# 开发服务器
cd frontend && corepack pnpm dev

# 单元测试
cd frontend && corepack pnpm test

# E2E 测试
cd frontend && corepack pnpm test:e2e

# 构建
cd frontend && corepack pnpm build

# 全量验证 (test + e2e + typecheck + build)
cd frontend && corepack pnpm verify
```

### 后端 (backend/)

```bash
# 运行 Spring Boot
mvn -pl backend spring-boot:run

# 后端单元测试
mvn -pl backend test

# 运行指定测试类
mvn -pl backend -Dtest=PlannerApiTest test

# 运行指定方法
mvn -pl backend -Dtest=PlannerApiTest#shouldUseWebLayerResponseDtosForPlannerControllers test
```

### 全量验证

```bash
# 后端测试 + 前端全量验证 (verify 阶段会触发前端 pnpm verify)
mvn verify
```

## 开发约定

- **测试优先**: 核心功能采用 TDD，先写失败测试再实现
- **前端测试**: 单元测试用 Vitest (`.spec.ts`)，集成/E2E 用 Playwright
- **后端测试**: Spring Boot Test，控制器层测试使用 `@WebMvcTest`
- **分支**: 使用 worktree 隔离功能开发
- **提交**: 通过 Claude Code `/commit` skill 创建 commit

## 关键文件

- `frontend/vite.config.ts` — Vite 配置
- `frontend/playwright.config.ts` — E2E 测试配置
- `backend/pom.xml` — 后端依赖与构建配置
- 根 `pom.xml` — Java 21, Spring Boot 3.3.9, MyBatis Plus, SQL Server
