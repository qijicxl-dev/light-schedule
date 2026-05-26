# 轻排程第一阶段 MVP 实施计划

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**目标：** 交付一套面向入门级离散制造企业的轻排程第一阶段 MVP，覆盖待排任务池、自动初排、人工微调、急单半自动重排、负荷/能力分析、风险提醒、确认回写和老板驾驶舱。

**架构：** 采用前后端分离但保持单仓管理：后端用 Spring Boot 单体应用承载排程领域服务、规则校验、金蝶集成与回写，前端用 Vue 3 承载主工作台、负荷分析页和老板驾驶舱。核心业务按 task-pool、capacity、scheduling、validation、replan、writeback 六个模块拆分，数据层采用 SQL Server 以贴近金蝶常见交付环境，金蝶接口拉取与回写采用独立线程池隔离，再先完成后端规则与接口，最后接入工作台交互与回写闭环。

**技术栈：** Java 21, Spring Boot 3.3, Maven, MyBatis-Plus, SQL Server 2022, Flyway, Vue 3, TypeScript, Vite, Element Plus, Pinia, Vue Router, ECharts, Interact.js, JUnit 5, Vitest, Playwright

---

## 范围判断

这份计划虽然覆盖多个模块，但它们共同服务于“已下达工单排程执行闭环”这一单一交付目标，因此保留为一份实施计划。实施顺序按“先后端规则与接口、再前端工作台与驾驶舱、最后联调验收”推进，确保每个阶段都能独立测试并持续形成可运行结果。

## 金蝶云星空实施口径补充

- 本计划默认以“金蝶云星空负责主数据与业务单据、本系统负责排程草稿、分析判断、确认发布与回写”为实施边界。
- 排程输入对象统一按金蝶常见业务口径理解：生产工单/制令单、工艺路线、资源档案、班次日历、上下级工单/BOM 关系。
- 排程输出对象统一按“确认发布批次”处理，不采用每次拖拽实时回写；回写目标优先落在计划开工时间、计划完工时间、车间/产线/设备、排程序号、排程状态等执行字段。
- 金蝶接口调用与回写统一走独立线程池，避免查询任务、批量回写和主业务线程互相阻塞。
- SQL Server 只承载排程草稿、能力 bucket、风险结果、回写审计等本系统数据，不替代金蝶业务事实。

## 代码与注释约定

- 需求文档没有提出“必须大量写代码注释”的要求。
- 第一阶段真正的硬要求是“可解释”，因此优先把解释能力做在接口返回、风险提示、回写结果和界面文案里，而不是堆在代码注释里。
- 默认不写解释性注释；只有在以下场景保留一行短注释：
  - 金蝶字段映射存在非直观口径时
  - SQL Server / Flyway 兼容写法容易误改时
  - 上下级放行、部分放行等业务规则与直觉不一致时
  - 金蝶线程池隔离、批量回写顺序、幂等控制等实现约束不明显时
- 禁止写任务式注释，如“这里是给轻排程用的”“这里处理急单逻辑”；这类信息应体现在命名、测试和实施说明里。
- 前端允许保留极少量文案注释，但更推荐直接把“不可落位原因”“影响范围”“候选建议”渲染给用户。

## 文件结构

### 一、根目录与工程骨架
- Create: `pom.xml` — Maven 聚合工程，统一管理后端模块构建。
- Create: `backend/pom.xml` — Spring Boot 后端依赖与插件。
- Create: `frontend/package.json` — Vue 前端依赖与脚本。
- Create: `frontend/tsconfig.json` — TypeScript 配置。
- Create: `frontend/vite.config.ts` — Vite 配置。
- Create: `.gitignore` — Java、Node、IDE 产物忽略规则。
- Create: `.env.example` — 前后端环境变量示例。

### 二、后端应用与基础设施
- Create: `backend/src/main/java/com/lightschedule/LightscheduleApplication.java` — Spring Boot 启动入口。
- Create: `backend/src/main/java/com/lightschedule/config/KingdeeProperties.java` — 金蝶接口配置。
- Create: `backend/src/main/java/com/lightschedule/config/MybatisPlusConfig.java` — MyBatis-Plus 配置。
- Create: `backend/src/main/java/com/lightschedule/config/KingdeeExecutorConfig.java` — 金蝶拉取/回写线程池配置。
- Create: `backend/src/main/resources/application.yml` — 后端默认配置（含 SQL Server 数据源与金蝶线程池参数）。
- Create: `backend/src/main/resources/db/migration/V1__init_schedule_tables.sql` — MVP 表结构（SQL Server 版本）。

### 三、后端领域模块
- Create: `backend/src/main/java/com/lightschedule/domain/model/WorkOrder.java` — 生产工单模型。
- Create: `backend/src/main/java/com/lightschedule/domain/model/SchedulingTask.java` — 排程任务模型。
- Create: `backend/src/main/java/com/lightschedule/domain/model/ResourceUnit.java` — 资源/产线模型。
- Create: `backend/src/main/java/com/lightschedule/domain/model/DependencyRelation.java` — 依赖关系模型。
- Create: `backend/src/main/java/com/lightschedule/domain/model/ScheduleDraft.java` — 排程草稿模型。
- Create: `backend/src/main/java/com/lightschedule/integration/kingdee/KingdeeClient.java` — 金蝶 API 客户端。
- Create: `backend/src/main/java/com/lightschedule/integration/kingdee/KingdeeWorkOrderMapper.java` — 金蝶工单、制令单、资源与依赖关系映射。
- Create: `backend/src/main/java/com/lightschedule/integration/kingdee/KingdeeWritebackClient.java` — 金蝶确认发布后的批量回写客户端。
- Create: `backend/src/main/java/com/lightschedule/modules/taskpool/TaskPoolService.java` — 待排任务池服务。
- Create: `backend/src/main/java/com/lightschedule/modules/capacity/CapacityBucketService.java` — bucket 负荷计算。
- Create: `backend/src/main/java/com/lightschedule/modules/capacity/CapacityAssessmentService.java` — 粗能力/细能力判断。
- Create: `backend/src/main/java/com/lightschedule/modules/scheduling/InitialSchedulingService.java` — 自动初排服务。
- Create: `backend/src/main/java/com/lightschedule/modules/validation/ScheduleValidationService.java` — 硬约束/软风险校验。
- Create: `backend/src/main/java/com/lightschedule/modules/replan/UrgentReplanService.java` — 急单重排服务。
- Create: `backend/src/main/java/com/lightschedule/modules/replan/SuggestionService.java` — 结构化建议服务。
- Create: `backend/src/main/java/com/lightschedule/modules/writeback/WritebackService.java` — 确认发布与回写服务。
- Create: `backend/src/main/java/com/lightschedule/modules/writeback/WritebackBatch.java` — 一次确认发布对应的回写批次对象。

### 四、后端接口层
- Create: `backend/src/main/java/com/lightschedule/web/TaskPoolController.java` — 待排任务池接口。
- Create: `backend/src/main/java/com/lightschedule/web/ScheduleController.java` — 自动初排、细能力判断接口。
- Create: `backend/src/main/java/com/lightschedule/web/ReplanController.java` — 急单重排接口。
- Create: `backend/src/main/java/com/lightschedule/web/WritebackController.java` — 发布回写接口。
- Create: `backend/src/main/java/com/lightschedule/web/DashboardController.java` — 驾驶舱与负荷视图接口。

### 五、前端应用与页面
- Create: `frontend/src/main.ts` — 前端入口。
- Create: `frontend/src/App.vue` — 应用壳。
- Create: `frontend/src/router/index.ts` — 路由。
- Create: `frontend/src/stores/scheduleDraft.ts` — 当前排程草稿状态。
- Create: `frontend/src/api/http.ts` — Axios 实例。
- Create: `frontend/src/api/planner.ts` — 排程相关 API。
- Create: `frontend/src/views/PlannerWorkbenchView.vue` — 主工作台。
- Create: `frontend/src/views/CapacityAnalysisView.vue` — 资源负荷/能力分析页。
- Create: `frontend/src/views/BossDashboardView.vue` — 老板驾驶舱。
- Create: `frontend/src/components/planner/TaskPoolPanel.vue` — 待排任务池。
- Create: `frontend/src/components/planner/ScheduleBoard.vue` — 甘特/时间轴工作区。
- Create: `frontend/src/components/planner/RiskSidePanel.vue` — 风险与建议侧栏。
- Create: `frontend/src/components/planner/UrgentOrderDialog.vue` — 急单插入弹窗。
- Create: `frontend/src/components/planner/PublishDialog.vue` — 发布回写弹窗。
- Create: `frontend/src/components/dashboard/LoadSummaryCards.vue` — 负荷总览卡片。

### 六、测试文件
- Create: `backend/src/test/java/com/lightschedule/config/KingdeePropertiesTest.java`
- Create: `backend/src/test/java/com/lightschedule/modules/taskpool/TaskPoolServiceTest.java`
- Create: `backend/src/test/java/com/lightschedule/modules/capacity/CapacityAssessmentServiceTest.java`
- Create: `backend/src/test/java/com/lightschedule/modules/scheduling/InitialSchedulingServiceTest.java`
- Create: `backend/src/test/java/com/lightschedule/modules/replan/UrgentReplanServiceTest.java`
- Create: `backend/src/test/java/com/lightschedule/modules/writeback/WritebackServiceTest.java`
- Create: `frontend/src/components/planner/__tests__/PlannerWorkbenchView.spec.ts`
- Create: `frontend/tests/e2e/planner-workflow.spec.ts`
- Create: `frontend/tests/e2e/dashboard.spec.ts`

---

### Task 1：搭建后端工程骨架

**Files:**
- Create: `pom.xml`
- Create: `backend/pom.xml`
- Create: `backend/src/main/java/com/lightschedule/LightscheduleApplication.java`
- Create: `backend/src/main/java/com/lightschedule/config/KingdeeProperties.java`
- Create: `backend/src/main/java/com/lightschedule/config/KingdeeExecutorConfig.java`
- Create: `backend/src/main/resources/application.yml`
- Test: `backend/src/test/java/com/lightschedule/config/KingdeePropertiesTest.java`

- [ ] **Step 1: 先写失败测试，约束金蝶连接配置不能为空**

```java
package com.lightschedule.config;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

class KingdeePropertiesTest {

    @Test
    void shouldRejectBlankBaseUrl() {
        assertThatThrownBy(() -> new KingdeeProperties("", "demo-app", "demo-secret"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("kingdee.base-url");
    }
}
```

- [ ] **Step 2: 运行测试，确认当前失败**

Run: `./mvnw -pl backend -Dtest=KingdeePropertiesTest test`
Expected: FAIL with `cannot find symbol: class KingdeeProperties`

- [ ] **Step 3: 写最小实现，让配置对象和应用入口先跑起来**

```java
package com.lightschedule.config;

public record KingdeeProperties(String baseUrl, String appId, String appSecret) {

    public KingdeeProperties {
        if (baseUrl == null || baseUrl.isBlank()) {
            throw new IllegalArgumentException("kingdee.base-url must not be blank");
        }
        if (appId == null || appId.isBlank()) {
            throw new IllegalArgumentException("kingdee.app-id must not be blank");
        }
        if (appSecret == null || appSecret.isBlank()) {
            throw new IllegalArgumentException("kingdee.app-secret must not be blank");
        }
    }
}
```

```xml
<project>
    <dependencies>
        <dependency>
            <groupId>org.springframework.boot</groupId>
            <artifactId>spring-boot-starter-web</artifactId>
        </dependency>
        <dependency>
            <groupId>com.baomidou</groupId>
            <artifactId>mybatis-plus-spring-boot3-starter</artifactId>
            <version>3.5.6</version>
        </dependency>
        <dependency>
            <groupId>com.microsoft.sqlserver</groupId>
            <artifactId>mssql-jdbc</artifactId>
            <version>12.6.3.jre11</version>
        </dependency>
        <dependency>
            <groupId>org.flywaydb</groupId>
            <artifactId>flyway-core</artifactId>
        </dependency>
        <dependency>
            <groupId>org.springframework.boot</groupId>
            <artifactId>spring-boot-starter-test</artifactId>
            <scope>test</scope>
        </dependency>
    </dependencies>
</project>
```

```java
package com.lightschedule;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication
public class LightscheduleApplication {

    public static void main(String[] args) {
        SpringApplication.run(LightscheduleApplication.class, args);
    }
}
```

```java
package com.lightschedule.config;

import java.util.concurrent.Executor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

@Configuration
public class KingdeeExecutorConfig {

    @Bean("kingdeeExecutor")
    public Executor kingdeeExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setThreadNamePrefix("kingdee-");
        executor.setCorePoolSize(4);
        executor.setMaxPoolSize(8);
        executor.setQueueCapacity(200);
        executor.initialize();
        return executor;
    }
}
```

```yaml
spring:
  application:
    name: light-schedule
  datasource:
    url: jdbc:sqlserver://127.0.0.1:1433;databaseName=light_schedule;encrypt=true;trustServerCertificate=true
    username: sa
    password: changeMe123!
    driver-class-name: com.microsoft.sqlserver.jdbc.SQLServerDriver
  flyway:
    enabled: true
    locations: classpath:db/migration

kingdee:
  base-url: https://kingdee.example.com
  app-id: demo-app
  app-secret: demo-secret
  executor:
    core-pool-size: 4
    max-pool-size: 8
    queue-capacity: 200
```

- [ ] **Step 4: 再次运行测试，确认通过**

Run: `./mvnw -pl backend -Dtest=KingdeePropertiesTest test`
Expected: PASS with `Tests run: 1, Failures: 0`

- [ ] **Step 5: 提交后端骨架**

```bash
git add pom.xml backend/pom.xml backend/src/main/java/com/lightschedule/LightscheduleApplication.java backend/src/main/java/com/lightschedule/config/KingdeeProperties.java backend/src/main/java/com/lightschedule/config/KingdeeExecutorConfig.java backend/src/main/resources/application.yml backend/src/test/java/com/lightschedule/config/KingdeePropertiesTest.java
git commit -m "feat: 初始化轻排程后端工程"
```

### Task 2：搭建前端工程骨架

**Files:**
- Create: `frontend/package.json`
- Create: `frontend/tsconfig.json`
- Create: `frontend/vite.config.ts`
- Create: `frontend/src/main.ts`
- Create: `frontend/src/App.vue`
- Create: `frontend/src/router/index.ts`
- Test: `frontend/src/components/planner/__tests__/PlannerWorkbenchView.spec.ts`

- [ ] **Step 1: 先写失败测试，要求应用存在工作台入口导航**

```ts
import { describe, expect, it } from 'vitest'
import { mount } from '@vue/test-utils'
import App from '@/App.vue'

describe('App', () => {
  it('显示排程工作台入口', () => {
    const wrapper = mount(App)
    expect(wrapper.text()).toContain('排程工作台')
  })
})
```

- [ ] **Step 2: 运行测试，确认当前失败**

Run: `pnpm --dir frontend vitest run src/components/planner/__tests__/PlannerWorkbenchView.spec.ts`
Expected: FAIL with `Failed to resolve import '@/App.vue'`

- [ ] **Step 3: 写最小前端入口与导航壳**

```ts
import { createApp } from 'vue'
import App from './App.vue'
import router from './router'

createApp(App).use(router).mount('#app')
```

```vue
<template>
  <div>
    <header>
      <nav>
        <span>排程工作台</span>
      </nav>
    </header>
    <router-view />
  </div>
</template>
```

```ts
import { createRouter, createWebHistory } from 'vue-router'

const router = createRouter({
  history: createWebHistory(),
  routes: []
})

export default router
```

- [ ] **Step 4: 再次运行测试，确认通过**

Run: `pnpm --dir frontend vitest run src/components/planner/__tests__/PlannerWorkbenchView.spec.ts`
Expected: PASS

- [ ] **Step 5: 提交前端骨架**

```bash
git add frontend/package.json frontend/tsconfig.json frontend/vite.config.ts frontend/src/main.ts frontend/src/App.vue frontend/src/router/index.ts frontend/src/components/planner/__tests__/PlannerWorkbenchView.spec.ts
git commit -m "feat: 初始化轻排程前端工程"
```

### Task 3：定义领域模型并打通待排任务池
**Files:**
- Create: `backend/src/main/java/com/lightschedule/domain/model/WorkOrder.java`
- Create: `backend/src/main/java/com/lightschedule/domain/model/SchedulingTask.java`
- Create: `backend/src/main/java/com/lightschedule/domain/model/DependencyRelation.java`
- Create: `backend/src/main/java/com/lightschedule/integration/kingdee/KingdeeWorkOrderMapper.java`
- Create: `backend/src/main/java/com/lightschedule/modules/taskpool/TaskPoolService.java`
- Create: `backend/src/main/java/com/lightschedule/web/TaskPoolController.java`
- Test: `backend/src/test/java/com/lightschedule/modules/taskpool/TaskPoolServiceTest.java`

- [ ] **Step 1: 先写失败测试，要求下游任务被依赖阻塞且保留缺料风险**

```java
package com.lightschedule.modules.taskpool;

import static org.assertj.core.api.Assertions.assertThat;

import com.lightschedule.domain.model.WorkOrder;
import java.util.List;
import org.junit.jupiter.api.Test;

class TaskPoolServiceTest {

    @Test
    void shouldMarkChildOrderBlockedAndKeepMaterialRisk() {
        TaskPoolService service = new TaskPoolService();

        var items = service.build(List.of(
                new WorkOrder("MO-1", "released", 100, "2026-04-25T00:00:00Z", "R1", false, List.of(), "ok"),
                new WorkOrder("MO-2", "released", 50, "2026-04-26T00:00:00Z", "R2", true, List.of("MO-1"), "missing")
        ));

        assertThat(items.get(1).readiness()).isEqualTo("blocked_by_dependency");
        assertThat(items.get(1).materialRisk()).isEqualTo("missing");
    }
}
```

- [ ] **Step 2: 运行测试，确认当前失败**

Run: `./mvnw -pl backend -Dtest=TaskPoolServiceTest test`
Expected: FAIL with `cannot find symbol: class TaskPoolService`

- [ ] **Step 3: 写最小领域对象、映射器和任务池服务**

```java
package com.lightschedule.domain.model;

import java.util.List;

public record WorkOrder(
        String workOrderCode,
        String status,
        int quantity,
        String dueAt,
        String routeId,
        boolean urgent,
        List<String> parentWorkOrderCodes,
        String materialRisk
) {
}
```

```java
package com.lightschedule.modules.taskpool;

import com.lightschedule.domain.model.WorkOrder;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

public class TaskPoolService {

    public List<TaskPoolItem> build(List<WorkOrder> workOrders) {
        Set<String> releasedCodes = workOrders.stream()
                .filter(order -> "released".equals(order.status()))
                .map(WorkOrder::workOrderCode)
                .collect(Collectors.toSet());

        return workOrders.stream()
                .filter(order -> "released".equals(order.status()))
                .map(order -> new TaskPoolItem(
                        order.workOrderCode(),
                        order.dueAt(),
                        order.urgent(),
                        order.materialRisk(),
                        order.parentWorkOrderCodes().stream().anyMatch(releasedCodes::contains)
                                ? "blocked_by_dependency"
                                : "ready"
                ))
                .toList();
    }

    public record TaskPoolItem(
            String workOrderCode,
            String dueAt,
            boolean urgent,
            String materialRisk,
            String readiness
    ) {
    }
}
```

```java
package com.lightschedule.web;

import com.lightschedule.modules.taskpool.TaskPoolService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/task-pool")
public class TaskPoolController {

    @GetMapping
    public Object list() {
        return new TaskPoolService().build(java.util.List.of());
    }
}
```

- [ ] **Step 4: 再次运行任务池测试**

Run: `./mvnw -pl backend -Dtest=TaskPoolServiceTest test`
Expected: PASS

- [ ] **Step 5: 提交待排任务池能力**

```bash
git add backend/src/main/java/com/lightschedule/domain/model/WorkOrder.java backend/src/main/java/com/lightschedule/domain/model/SchedulingTask.java backend/src/main/java/com/lightschedule/domain/model/DependencyRelation.java backend/src/main/java/com/lightschedule/integration/kingdee/KingdeeWorkOrderMapper.java backend/src/main/java/com/lightschedule/modules/taskpool/TaskPoolService.java backend/src/main/java/com/lightschedule/web/TaskPoolController.java backend/src/test/java/com/lightschedule/modules/taskpool/TaskPoolServiceTest.java
git commit -m "feat: 新增待排任务池"
```

### Task 4：实现负荷 bucket、粗能力和细能力判断

**Files:**
- Create: `backend/src/main/java/com/lightschedule/domain/model/ResourceUnit.java`
- Create: `backend/src/main/java/com/lightschedule/modules/capacity/CapacityBucketService.java`
- Create: `backend/src/main/java/com/lightschedule/modules/capacity/CapacityAssessmentService.java`
- Create: `backend/src/main/java/com/lightschedule/web/DashboardController.java`
- Test: `backend/src/test/java/com/lightschedule/modules/capacity/CapacityAssessmentServiceTest.java`

- [ ] **Step 1: 先写失败测试，要求输出粗能力超负荷和细能力高负荷**

```java
package com.lightschedule.modules.capacity;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class CapacityAssessmentServiceTest {

    @Test
    void shouldReturnOverloadedForCoarseCapacity() {
        CapacityAssessmentService service = new CapacityAssessmentService();
        assertThat(service.assessCoarse(960, 480).status()).isEqualTo("overloaded");
    }

    @Test
    void shouldReturnHighLoadForFineCapacity() {
        CapacityAssessmentService service = new CapacityAssessmentService();
        assertThat(service.assessFine(60, 120, 70, 0.8).status()).isEqualTo("placeable_high_load");
    }
}
```

- [ ] **Step 2: 运行测试，确认当前失败**

Run: `./mvnw -pl backend -Dtest=CapacityAssessmentServiceTest test`
Expected: FAIL with `cannot find symbol: class CapacityAssessmentService`

- [ ] **Step 3: 写最小能力判断实现**

```java
package com.lightschedule.modules.capacity;

public class CapacityAssessmentService {

    public AssessmentResult assessCoarse(int requiredMinutes, int availableMinutes) {
        double loadRate = (double) requiredMinutes / availableMinutes;
        if (loadRate > 1) {
            return new AssessmentResult("overloaded", loadRate);
        }
        if (loadRate >= 0.85) {
            return new AssessmentResult("tight", loadRate);
        }
        return new AssessmentResult("feasible", loadRate);
    }

    public AssessmentResult assessFine(int requiredMinutes, int availableMinutes, int usedMinutes, double warningLoadRate) {
        double projectedLoadRate = (double) (requiredMinutes + usedMinutes) / availableMinutes;
        if (projectedLoadRate > 1) {
            return new AssessmentResult("not_placeable", projectedLoadRate);
        }
        if (projectedLoadRate >= warningLoadRate) {
            return new AssessmentResult("placeable_high_load", projectedLoadRate);
        }
        return new AssessmentResult("placeable", projectedLoadRate);
    }

    public record AssessmentResult(String status, double loadRate) {
    }
}
```

```java
package com.lightschedule.web;

import com.lightschedule.modules.capacity.CapacityAssessmentService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/dashboard")
public class DashboardController {

    @GetMapping("/capacity-summary")
    public Object capacitySummary() {
        return new CapacityAssessmentService().assessCoarse(480, 480);
    }
}
```

- [ ] **Step 4: 再次运行能力判断测试**

Run: `./mvnw -pl backend -Dtest=CapacityAssessmentServiceTest test`
Expected: PASS

- [ ] **Step 5: 提交能力分析模块**

```bash
git add backend/src/main/java/com/lightschedule/domain/model/ResourceUnit.java backend/src/main/java/com/lightschedule/modules/capacity/CapacityBucketService.java backend/src/main/java/com/lightschedule/modules/capacity/CapacityAssessmentService.java backend/src/main/java/com/lightschedule/web/DashboardController.java backend/src/test/java/com/lightschedule/modules/capacity/CapacityAssessmentServiceTest.java
git commit -m "feat: 新增负荷与能力判断"
```

### Task 5：实现自动初排与硬约束校验

**Files:**
- Create: `backend/src/main/java/com/lightschedule/modules/scheduling/InitialSchedulingService.java`
- Create: `backend/src/main/java/com/lightschedule/modules/validation/ScheduleValidationService.java`
- Create: `backend/src/main/java/com/lightschedule/web/ScheduleController.java`
- Test: `backend/src/test/java/com/lightschedule/modules/scheduling/InitialSchedulingServiceTest.java`

- [ ] **Step 1: 先写失败测试，要求父任务先于子任务且同资源不重叠**

```java
package com.lightschedule.modules.scheduling;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import org.junit.jupiter.api.Test;

class InitialSchedulingServiceTest {

    @Test
    void shouldScheduleParentBeforeChildWithoutOverlap() {
        InitialSchedulingService service = new InitialSchedulingService();

        var result = service.build(new InitialSchedulingService.ScheduleInput(
                List.of(
                        new InitialSchedulingService.Task("T1", "LINE-A", 120, List.of()),
                        new InitialSchedulingService.Task("T2", "LINE-A", 60, List.of("T1"))
                ),
                "2026-04-24T08:00:00Z"
        ));

        assertThat(result.items().get(0).endAt()).isLessThanOrEqualTo(result.items().get(1).startAt());
    }
}
```

- [ ] **Step 2: 运行测试，确认当前失败**

Run: `./mvnw -pl backend -Dtest=InitialSchedulingServiceTest test`
Expected: FAIL with `cannot find symbol: class InitialSchedulingService`

- [ ] **Step 3: 写最小自动初排和硬约束实现**

```java
package com.lightschedule.modules.scheduling;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Comparator;
import java.util.List;

public class InitialSchedulingService {

    public ScheduleResult build(ScheduleInput input) {
        Instant cursor = Instant.parse(input.startAt());
        List<ScheduledItem> items = input.tasks().stream()
                .sorted(Comparator.comparingInt(task -> task.dependencyTaskIds().size()))
                .map(task -> {
                    Instant startAt = cursor;
                    Instant endAt = startAt.plus(task.requiredMinutes(), ChronoUnit.MINUTES);
                    cursor = endAt;
                    return new ScheduledItem(task.taskId(), task.resourceId(), startAt.toString(), endAt.toString());
                })
                .toList();
        return new ScheduleResult(items);
    }

    public record ScheduleInput(List<Task> tasks, String startAt) {
    }

    public record Task(String taskId, String resourceId, int requiredMinutes, List<String> dependencyTaskIds) {
    }

    public record ScheduledItem(String taskId, String resourceId, String startAt, String endAt) {
    }

    public record ScheduleResult(List<ScheduledItem> items) {
    }
}
```

```java
package com.lightschedule.modules.validation;

import com.lightschedule.modules.scheduling.InitialSchedulingService.ScheduledItem;
import java.util.List;

public class ScheduleValidationService {

    public List<String> validateHardRules(ScheduledItem current, List<ScheduledItem> previousItems) {
        boolean overlap = previousItems.stream()
                .anyMatch(item -> item.resourceId().equals(current.resourceId()) && item.endAt().compareTo(current.startAt()) > 0);
        return overlap ? List.of("resource_conflict") : List.of();
    }
}
```

- [ ] **Step 4: 再次运行自动初排测试**

Run: `./mvnw -pl backend -Dtest=InitialSchedulingServiceTest test`
Expected: PASS

- [ ] **Step 5: 提交自动初排能力**

```bash
git add backend/src/main/java/com/lightschedule/modules/scheduling/InitialSchedulingService.java backend/src/main/java/com/lightschedule/modules/validation/ScheduleValidationService.java backend/src/main/java/com/lightschedule/web/ScheduleController.java backend/src/test/java/com/lightschedule/modules/scheduling/InitialSchedulingServiceTest.java
git commit -m "feat: 新增自动初排与约束校验"
```

### Task 6：实现急单重排、结构化建议与确认回写

**Files:**
- Create: `backend/src/main/resources/db/migration/V1__init_schedule_tables.sql`
- Create: `backend/src/main/java/com/lightschedule/domain/model/ScheduleDraft.java`
- Create: `backend/src/main/java/com/lightschedule/modules/replan/SuggestionService.java`
- Create: `backend/src/main/java/com/lightschedule/modules/replan/UrgentReplanService.java`
- Create: `backend/src/main/java/com/lightschedule/modules/writeback/WritebackService.java`
- Create: `backend/src/main/java/com/lightschedule/web/ReplanController.java`
- Create: `backend/src/main/java/com/lightschedule/web/WritebackController.java`
- Test: `backend/src/test/java/com/lightschedule/modules/replan/UrgentReplanServiceTest.java`
- Test: `backend/src/test/java/com/lightschedule/modules/writeback/WritebackServiceTest.java`

- [ ] **Step 1: 先写失败测试，要求急单重排返回影响范围和至少两类建议**

```java
package com.lightschedule.modules.replan;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class UrgentReplanServiceTest {

    @Test
    void shouldReturnAffectedTasksAndSuggestions() {
        UrgentReplanService service = new UrgentReplanService(new SuggestionService());

        var result = service.replan("U1", java.util.List.of("T1", "T2"), java.util.List.of("LINE-A"));

        assertThat(result.affectedTaskIds()).containsExactly("T1", "T2");
        assertThat(result.suggestions().stream().map(SuggestionService.Suggestion::action))
                .containsExactly("reassign_same_group", "move_next_slot", "manual_overtime_review");
    }
}
```

- [ ] **Step 2: 再写失败测试，要求存在硬冲突时禁止发布回写**

```java
package com.lightschedule.modules.writeback;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

class WritebackServiceTest {

    @Test
    void shouldBlockPublishWhenBlockingIssuesExist() {
        WritebackService service = new WritebackService();

        assertThatThrownBy(() -> service.publish("draft-1", java.util.List.of("resource_conflict")))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("resource_conflict");
    }
}
```

- [ ] **Step 3: 运行这两组测试，确认当前失败**

Run: `./mvnw -pl backend -Dtest=UrgentReplanServiceTest,WritebackServiceTest test`
Expected: FAIL with missing service classes

- [ ] **Step 4: 写最小重排、建议和回写实现**

```java
package com.lightschedule.modules.replan;

import java.util.List;

public class SuggestionService {

    public List<Suggestion> build(List<String> overloadedResourceIds) {
        if (overloadedResourceIds.isEmpty()) {
            return List.of();
        }
        return List.of(
                new Suggestion("reassign_same_group", "同组资源仍有剩余能力"),
                new Suggestion("move_next_slot", "下一可用时间窗可承接"),
                new Suggestion("manual_overtime_review", "只有在必须保急单交期时才人工确认加班")
        );
    }

    public record Suggestion(String action, String reason) {
    }
}
```

```java
package com.lightschedule.modules.replan;

import java.util.List;

public class UrgentReplanService {

    private final SuggestionService suggestionService;

    public UrgentReplanService(SuggestionService suggestionService) {
        this.suggestionService = suggestionService;
    }

    public ReplanResult replan(String urgentTaskId, List<String> affectedTaskIds, List<String> overloadedResourceIds) {
        return new ReplanResult(urgentTaskId, affectedTaskIds, suggestionService.build(overloadedResourceIds));
    }

    public record ReplanResult(String urgentTaskId, List<String> affectedTaskIds, List<SuggestionService.Suggestion> suggestions) {
    }
}
```

```java
package com.lightschedule.modules.writeback;

import java.util.List;

public class WritebackService {

    public PublishResult publish(String draftId, List<String> blockingIssues) {
        if (!blockingIssues.isEmpty()) {
            throw new IllegalStateException(String.join(",", blockingIssues));
        }
        return new PublishResult(draftId, "published", "queued");
    }

    public record PublishResult(String draftId, String status, String writebackStatus) {
    }
}
```

```sql
create table schedule_draft (
    id varchar(64) not null primary key,
    version_no int not null,
    status varchar(32) not null,
    draft_payload nvarchar(max) not null,
    created_at datetime2 not null default sysutcdatetime()
);

go

create table writeback_audit (
    id varchar(64) not null primary key,
    draft_id varchar(64) not null,
    status varchar(32) not null,
    message nvarchar(255) not null,
    created_at datetime2 not null default sysutcdatetime()
);

go

create index idx_writeback_audit_draft_id on writeback_audit(draft_id);
```

- [ ] **Step 5: 再次运行重排和回写测试**

Run: `./mvnw -pl backend -Dtest=UrgentReplanServiceTest,WritebackServiceTest test`
Expected: PASS

- [ ] **Step 6: 提交急单重排与回写闭环**

```bash
git add backend/src/main/resources/db/migration/V1__init_schedule_tables.sql backend/src/main/java/com/lightschedule/domain/model/ScheduleDraft.java backend/src/main/java/com/lightschedule/modules/replan/SuggestionService.java backend/src/main/java/com/lightschedule/modules/replan/UrgentReplanService.java backend/src/main/java/com/lightschedule/modules/writeback/WritebackService.java backend/src/main/java/com/lightschedule/web/ReplanController.java backend/src/main/java/com/lightschedule/web/WritebackController.java backend/src/test/java/com/lightschedule/modules/replan/UrgentReplanServiceTest.java backend/src/test/java/com/lightschedule/modules/writeback/WritebackServiceTest.java
git commit -m "feat: 新增急单重排与回写闭环"
```

### Task 7：实现排程主工作台页面

**Files:**
- Create: `frontend/src/api/http.ts`
- Create: `frontend/src/api/planner.ts`
- Create: `frontend/src/stores/scheduleDraft.ts`
- Create: `frontend/src/views/PlannerWorkbenchView.vue`
- Create: `frontend/src/components/planner/TaskPoolPanel.vue`
- Create: `frontend/src/components/planner/ScheduleBoard.vue`
- Create: `frontend/src/components/planner/RiskSidePanel.vue`
- Test: `frontend/src/components/planner/__tests__/PlannerWorkbenchView.spec.ts`

- [ ] **Step 1: 先写失败测试，要求工作台出现三栏核心区域**

```ts
import { describe, expect, it } from 'vitest'
import { mount } from '@vue/test-utils'
import PlannerWorkbenchView from '@/views/PlannerWorkbenchView.vue'

describe('PlannerWorkbenchView', () => {
  it('显示待排任务池、排程画板和风险侧栏', () => {
    const wrapper = mount(PlannerWorkbenchView)
    expect(wrapper.text()).toContain('待排任务池')
    expect(wrapper.text()).toContain('排程画板')
    expect(wrapper.text()).toContain('风险与建议')
  })
})
```

- [ ] **Step 2: 运行测试，确认当前失败**

Run: `pnpm --dir frontend vitest run src/components/planner/__tests__/PlannerWorkbenchView.spec.ts`
Expected: FAIL with `Failed to resolve import '@/views/PlannerWorkbenchView.vue'`

- [ ] **Step 3: 写最小主工作台和三栏组件**

```vue
<template>
  <section class="planner-workbench">
    <TaskPoolPanel />
    <ScheduleBoard />
    <RiskSidePanel />
  </section>
</template>

<script setup lang="ts">
import TaskPoolPanel from '@/components/planner/TaskPoolPanel.vue'
import ScheduleBoard from '@/components/planner/ScheduleBoard.vue'
import RiskSidePanel from '@/components/planner/RiskSidePanel.vue'
</script>
```

```vue
<template>
  <aside>待排任务池</aside>
</template>
```

```vue
<template>
  <main>排程画板</main>
</template>
```

```vue
<template>
  <aside>风险与建议</aside>
</template>
```

- [ ] **Step 4: 再次运行工作台单测**

Run: `pnpm --dir frontend vitest run src/components/planner/__tests__/PlannerWorkbenchView.spec.ts`
Expected: PASS

- [ ] **Step 5: 提交工作台主界面**

```bash
git add frontend/src/api/http.ts frontend/src/api/planner.ts frontend/src/stores/scheduleDraft.ts frontend/src/views/PlannerWorkbenchView.vue frontend/src/components/planner/TaskPoolPanel.vue frontend/src/components/planner/ScheduleBoard.vue frontend/src/components/planner/RiskSidePanel.vue frontend/src/components/planner/__tests__/PlannerWorkbenchView.spec.ts
git commit -m "feat: 新增排程主工作台"
```

### Task 8：实现急单弹窗、发布弹窗、能力分析页和老板驾驶舱

**Files:**
- Create: `frontend/src/views/CapacityAnalysisView.vue`
- Create: `frontend/src/views/BossDashboardView.vue`
- Create: `frontend/src/components/planner/UrgentOrderDialog.vue`
- Create: `frontend/src/components/planner/PublishDialog.vue`
- Create: `frontend/src/components/dashboard/LoadSummaryCards.vue`
- Test: `frontend/tests/e2e/planner-workflow.spec.ts`
- Test: `frontend/tests/e2e/dashboard.spec.ts`

- [ ] **Step 1: 先写失败的端到端测试，要求用户能插入急单并打开发布确认**

```ts
import { test, expect } from '@playwright/test'

test('计划员可以查看任务池、插入急单并打开发布确认', async ({ page }) => {
  await page.goto('/planner')
  await expect(page.getByText('待排任务池')).toBeVisible()
  await page.getByRole('button', { name: '插入急单' }).click()
  await expect(page.getByText('受影响任务')).toBeVisible()
  await page.getByRole('button', { name: '确认回写' }).click()
  await expect(page.getByText('回写前校验')).toBeVisible()
})
```

- [ ] **Step 2: 先写失败的驾驶舱测试，要求出现老板驾驶舱和负荷卡片**

```ts
import { test, expect } from '@playwright/test'

test('老板驾驶舱显示负荷总览', async ({ page }) => {
  await page.goto('/dashboard')
  await expect(page.getByText('老板驾驶舱')).toBeVisible()
  await expect(page.getByText('资源负荷总览')).toBeVisible()
})
```

- [ ] **Step 3: 运行这两组 e2e，确认当前失败**

Run: `pnpm --dir frontend playwright test tests/e2e/planner-workflow.spec.ts tests/e2e/dashboard.spec.ts`
Expected: FAIL because `/planner`、`/dashboard` 页面和对应按钮尚未实现

- [ ] **Step 4: 写最小页面与弹窗实现**

```vue
<template>
  <div>
    <h1>老板驾驶舱</h1>
    <LoadSummaryCards />
  </div>
</template>

<script setup lang="ts">
import LoadSummaryCards from '@/components/dashboard/LoadSummaryCards.vue'
</script>
```

```vue
<template>
  <el-dialog model-value title="急单插入">
    <div>受影响任务</div>
  </el-dialog>
</template>
```

```vue
<template>
  <el-dialog model-value title="回写确认">
    <div>回写前校验</div>
  </el-dialog>
</template>
```

```vue
<template>
  <section>
    <h2>资源负荷总览</h2>
  </section>
</template>
```

- [ ] **Step 5: 再次运行 e2e 测试**

Run: `pnpm --dir frontend playwright test tests/e2e/planner-workflow.spec.ts tests/e2e/dashboard.spec.ts`
Expected: PASS

- [ ] **Step 6: 提交分析页与驾驶舱**

```bash
git add frontend/src/views/CapacityAnalysisView.vue frontend/src/views/BossDashboardView.vue frontend/src/components/planner/UrgentOrderDialog.vue frontend/src/components/planner/PublishDialog.vue frontend/src/components/dashboard/LoadSummaryCards.vue frontend/tests/e2e/planner-workflow.spec.ts frontend/tests/e2e/dashboard.spec.ts
git commit -m "feat: 新增能力分析页和老板驾驶舱"
```

### Task 9：全链路联调并冻结 MVP 基线

**Files:**
- Modify: `backend/pom.xml`
- Modify: `frontend/package.json`
- Test: `backend/src/test/java/**/*.java`
- Test: `frontend/src/components/planner/__tests__/*.spec.ts`
- Test: `frontend/tests/e2e/*.spec.ts`

- [ ] **Step 1: 增加统一校验命令，确保一键执行后端单测、前端单测、前端 e2e 和构建**

```xml
<plugin>
    <groupId>org.codehaus.mojo</groupId>
    <artifactId>exec-maven-plugin</artifactId>
    <version>3.3.0</version>
    <executions>
        <execution>
            <id>verify-frontend</id>
            <phase>verify</phase>
            <goals>
                <goal>exec</goal>
            </goals>
            <configuration>
                <executable>corepack</executable>
                <workingDirectory>${project.basedir}/../frontend</workingDirectory>
                <arguments>
                    <argument>pnpm</argument>
                    <argument>verify</argument>
                </arguments>
            </configuration>
        </execution>
    </executions>
</plugin>
```

```json
{
  "scripts": {
    "test": "vitest run",
    "test:e2e": "playwright test",
    "build": "vue-tsc --noEmit && vite build",
    "verify": "vitest run && playwright test && vue-tsc --noEmit && vite build"
  }
}
```

- [ ] **Step 2: 运行后端单测**

Run: `mvn -pl backend test`
Expected: PASS with all JUnit tests green

- [ ] **Step 3: 运行前端单测与 e2e**

Run: `corepack pnpm -C frontend test && corepack pnpm -C frontend test:e2e`
Expected: PASS

- [ ] **Step 4: 运行整体验证和构建**

Run: `mvn -pl backend verify && corepack pnpm -C frontend build`
Expected: PASS with backend verify green and frontend bundle build success

- [ ] **Step 5: 修复任何失败项后再记录基线提交**

```bash
git add backend/pom.xml frontend/package.json
git commit -m "chore: 冻结轻排程 mvp 基线"
```

---

## 整个落地周期清单

### 周期基线

- 默认按 **8 周 MVP 周期** 组织实施。
- 默认团队配置按 **后端/集成 1 人 + 后端/规则 1 人 + 前端 1 人 + 产品/实施 1 人** 估算；如果实际人力更少，就保持顺序不变、拉长周次，不要并行压缩关键依赖。
- 默认目标不是“一次性做全”，而是先交付一个能从金蝶拉工单、能排、能调、能看风险、能确认回写的可试点版本。

### 阶段划分总览

| 阶段 | 周次 | 核心目标 | 对应任务 | 关键交付物 |
| --- | --- | --- | --- | --- |
| 阶段 1：项目启动与技术底座 | 第 1 周 | 搭好前后端骨架、SQL Server、线程池、基础配置 | Task 1 + Task 2 | 工程骨架、数据库连接、前端入口、基础测试命令 |
| 阶段 2：主数据映射与任务池 | 第 2 周 | 打通金蝶工单映射、待排任务池、领域对象 | Task 3 | 工单映射、任务池接口、依赖阻塞识别 |
| 阶段 3：核心排程判断 | 第 3-4 周 | 完成负荷判断、自动初排、硬约束校验 | Task 4 + Task 5 | 粗能力/细能力、自动初排结果、约束校验接口 |
| 阶段 4：急单与回写闭环 | 第 5 周 | 完成急单重排、建议生成、确认回写 | Task 6 | 急单影响分析、结构化建议、回写批次 |
| 阶段 5：主工作台与驾驶舱 | 第 6-7 周 | 完成工作台、分析页、老板驾驶舱和关键交互 | Task 7 + Task 8 | 三栏工作台、急单弹窗、发布弹窗、驾驶舱 |
| 阶段 6：联调、试点与基线冻结 | 第 8 周 | 完成联调、验收、基线冻结 | Task 9 | 全链路验证报告、发布基线、试点演示版本 |

### 第 1 周：项目启动与技术底座

**目标：** 把工程骨架、SQL Server 数据源、Flyway、金蝶线程池和前端壳先搭起来，让项目具备可运行基础。

**执行清单：**
- [ ] 完成 Task 1：后端工程骨架
- [ ] 完成 Task 2：前端工程骨架
- [ ] 准备本地 SQL Server 开发库并验证连接串
- [ ] 准备金蝶测试环境连接参数或模拟参数
- [ ] 约定开发环境变量命名和 `.env.example` 内容
- [ ] 跑通后端单测与前端单测最小命令

**本周交付物：**
- `backend/` 可启动
- `frontend/` 可启动
- SQL Server 数据源可连接
- 金蝶线程池参数可配置
- 基础测试命令可以执行

**前置依赖：**
- SQL Server 开发实例
- 金蝶测试环境地址、应用标识、密钥

**本周验收标准：**
- `./mvnw -pl backend -Dtest=KingdeePropertiesTest test` 可通过
- `pnpm --dir frontend vitest run src/components/planner/__tests__/PlannerWorkbenchView.spec.ts` 可执行
- `application.yml` 中 SQL Server 与金蝶配置项齐全

### 第 2 周：主数据映射与任务池

**目标：** 先把“从金蝶拉什么、怎么变成待排任务”定义清楚，打通待排任务池闭环。

**执行清单：**
- [ ] 完成 Task 3：领域模型与待排任务池
- [ ] 明确金蝶侧输入字段：工单号、数量、交期、工艺路线、上下级关系、急单标记、齐套风险
- [ ] 明确本系统维护字段：任务状态、锁定状态、风险状态、排程草稿版本
- [ ] 确认待排任务池 readiness 口径：`ready` / `blocked_by_dependency`
- [ ] 确认缺料只作为风险标识，不作为硬拦截

**本周交付物：**
- `WorkOrder`、`SchedulingTask`、`DependencyRelation` 模型
- `KingdeeWorkOrderMapper`
- `TaskPoolService`
- `TaskPoolController`

**前置依赖：**
- 第 1 周技术底座完成
- 金蝶测试数据样例至少一组完整工单链

**本周验收标准：**
- `TaskPoolServiceTest` 通过
- 待排任务池接口能返回至少一组模拟待排任务
- 下游工单被上游依赖阻塞时，状态正确显示为 `blocked_by_dependency`

### 第 3 周：负荷 bucket 与能力判断

**目标：** 先让系统具备“能不能接、能不能落位、是不是高负荷”的基础判断能力。

**执行清单：**
- [ ] 完成 Task 4：负荷 bucket、粗能力、细能力判断
- [ ] 明确资源组、资源、班次、小时 bucket 切分口径
- [ ] 统一粗能力结果：`feasible` / `tight` / `overloaded`
- [ ] 统一细能力结果：`placeable` / `placeable_high_load` / `not_placeable`
- [ ] 明确负荷阈值和 warningLoadRate 的默认值

**本周交付物：**
- `CapacityBucketService`
- `CapacityAssessmentService`
- 驾驶舱能力摘要接口
- 资源负荷计算样例数据

**前置依赖：**
- 第 2 周任务池模型完成
- 资源、班次、产能参数至少有一套样例数据

**本周验收标准：**
- `CapacityAssessmentServiceTest` 通过
- 系统可返回超负荷和高负荷两类判断结果
- 负荷计算结果与资源可用时间一致

### 第 4 周：自动初排与硬约束校验

**目标：** 让系统先能给出一版“可执行的初排结果”，并能拦住明显错误。

**执行清单：**
- [ ] 完成 Task 5：自动初排与硬约束校验
- [ ] 确认自动初排排序口径：先依赖、后资源、再优先级
- [ ] 确认硬约束范围：资源冲突、依赖破坏、非法日历时段
- [ ] 明确软风险范围：高负荷、可能延期、缺料提醒
- [ ] 输出自动初排接口给前端或 Postman 调试

**本周交付物：**
- `InitialSchedulingService`
- `ScheduleValidationService`
- `ScheduleController`
- 初排结果样例

**前置依赖：**
- 第 3 周能力判断结果可用
- 任务、资源、依赖对象齐全

**本周验收标准：**
- `InitialSchedulingServiceTest` 通过
- 父任务早于子任务
- 同一资源不存在重叠排程
- 非法排程可返回硬冲突结果

### 第 5 周：急单重排与确认回写闭环

**目标：** 打通 MVP 最关键的“急单来了怎么办、结果怎么确认回写”闭环。

**执行清单：**
- [ ] 完成 Task 6：急单重排、结构化建议、确认回写
- [ ] 明确急单插入输入：急单任务、时间窗、受影响资源
- [ ] 明确建议输出口径：同组替代、顺延下一时段、人工确认加班、人工复核
- [ ] 明确回写批次对象、回写审计表和失败说明口径
- [ ] 接入 `KingdeeWritebackClient` 的回写调用约定

**本周交付物：**
- `UrgentReplanService`
- `SuggestionService`
- `WritebackService`
- `WritebackBatch`
- SQL Server 回写审计表结构

**前置依赖：**
- 第 4 周自动初排结果稳定
- 金蝶回写字段与目标单据范围已确认

**本周验收标准：**
- `UrgentReplanServiceTest` 与 `WritebackServiceTest` 通过
- 系统能返回受影响任务列表
- 系统能返回至少两类可执行建议
- 遇到硬冲突时禁止发布回写

### 第 6 周：排程主工作台

**目标：** 让计划员先有一个能看、能调、能理解风险的主界面。

**执行清单：**
- [ ] 完成 Task 7：排程主工作台页面
- [ ] 接入待排任务池接口
- [ ] 接入初排结果与风险结果
- [ ] 在右侧侧栏显示主要风险、原因和建议摘要
- [ ] 保证三栏结构稳定可演示

**本周交付物：**
- `PlannerWorkbenchView`
- `TaskPoolPanel`
- `ScheduleBoard`
- `RiskSidePanel`
- 排程草稿前端状态管理

**前置依赖：**
- 第 2-5 周接口可调用
- 至少一套可展示的模拟排程结果

**本周验收标准：**
- `PlannerWorkbenchView.spec.ts` 通过
- 页面可展示待排任务池、排程画板、风险侧栏
- 风险侧栏能显示主要原因或建议摘要

### 第 7 周：急单弹窗、能力分析页与老板驾驶舱

**目标：** 把面向计划员和老板的关键展示层补齐，具备完整演示能力。

**执行清单：**
- [ ] 完成 Task 8：急单弹窗、发布弹窗、能力分析页和老板驾驶舱
- [ ] 接入急单插入和发布确认交互
- [ ] 接入资源负荷总览和驾驶舱卡片
- [ ] 明确老板驾驶舱只看结果、不承担复杂操作
- [ ] 准备一套试点演示脚本

**本周交付物：**
- `UrgentOrderDialog`
- `PublishDialog`
- `CapacityAnalysisView`
- `BossDashboardView`
- `LoadSummaryCards`

**前置依赖：**
- 第 6 周工作台可运行
- 第 3-5 周接口结果稳定

**本周验收标准：**
- `planner-workflow.spec.ts` 与 `dashboard.spec.ts` 可通过
- `/planner` 可打开急单弹窗和发布确认弹窗
- `/dashboard` 可展示资源负荷总览

### 第 8 周：联调、试点与基线冻结

**目标：** 把后端、前端、数据库、金蝶集成真正合在一起，形成可交付试点版本。

**执行清单：**
- [ ] 完成 Task 9：全链路联调并冻结 MVP 基线
- [ ] 使用真实或准真实金蝶样例数据进行联调
- [ ] 校验任务池、初排、急单、回写、驾驶舱全链路
- [ ] 输出试点演示脚本、问题清单、修复清单
- [ ] 形成一版试点部署说明和环境参数表

**本周交付物：**
- 联调通过的 MVP 版本
- 一键校验命令
- 基线提交
- 试点验收清单

**前置依赖：**
- 前 7 周全部完成
- 金蝶联调环境可用
- SQL Server 测试库可稳定访问

**本周验收标准：**
- 后端单测、前端单测、前端 e2e、构建全部通过
- 关键链路可演示：拉任务 → 初排 → 调整 → 急单 → 确认回写 → 驾驶舱查看
- 试点方可基于同一套结果进行计划、车间、老板三角色演示

### 跨阶段持续清单

以下事项不是某一周独有，而是整个周期都要持续执行：

- [ ] 每周至少一次同步金蝶字段口径、回写边界、状态口径
- [ ] 每周至少一次回顾风险项：依赖、班次、资源、回写失败、线程池堆积
- [ ] 每个任务结束后立即运行对应测试，不把问题堆到联调周
- [ ] 保持“少注释、强命名、强测试、强界面解释”的实现风格
- [ ] 所有异常都要输出可解释结果，不允许只有报错码没有原因
- [ ] 所有回写都必须保留批次、状态和失败原因，便于追溯

### 整个周期的里程碑检查点

- **里程碑 1（第 2 周末）：** 能看到待排任务池，工单依赖关系正确
- **里程碑 2（第 4 周末）：** 能输出自动初排结果，并识别硬冲突
- **里程碑 3（第 5 周末）：** 能处理急单并形成确认回写闭环
- **里程碑 4（第 7 周末）：** 能完整演示工作台、急单、驾驶舱
- **里程碑 5（第 8 周末）：** 能完成试点联调并冻结 MVP 基线

---

## 验收映射

- 待排任务池：Task 3
- 自动初排：Task 5
- 人工微调承载界面：Task 7
- 急单半自动重排：Task 6 + Task 8
- 资源/资源组负荷展示：Task 4 + Task 8
- 粗能力/细能力判断：Task 4
- 结构化建议：Task 6
- 确认发布与深度回写：Task 6
- 老板驾驶舱：Task 8
- 全链路测试与基线冻结：Task 9

## 不纳入本计划的范围

以下内容明确不进入第一阶段计划：全工序精细 APS、物料齐套硬约束、多工厂协同排程、黑箱优化算法、IoT 实时驱动重排、自动负荷均衡、复杂并行拆单、复杂换模优化、经营级接单承诺。
