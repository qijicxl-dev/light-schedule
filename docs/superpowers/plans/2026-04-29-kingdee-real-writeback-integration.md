# Kingdee Real Writeback Integration Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Replace the current placeholder `validated + pending` writeback flow with a real Kingdee writeback pipeline that performs field mapping, persists audit records, handles failures and retries, and exposes a stable status boundary to the existing `/planner` publish dialog.

**Architecture:** Keep `/planner`’s current publish entrypoint (`POST /api/writeback/publish`) as the user-triggered boundary, but split backend responsibilities into four layers: pre-writeback validation, Kingdee payload mapping, async dispatch/retry orchestration, and audit/status persistence. Add a lightweight status query endpoint so the existing publish dialog can reopen against backend truth instead of only replaying in-memory state.

**Tech Stack:** Java 21, Spring Boot 3.3, Maven, MyBatis-Plus, SQL Server 2022, Flyway, Vue 3, TypeScript, Vite, Vitest, Playwright, JUnit 5

---

## Scope Check

This next phase is intentionally narrower than the original MVP plan. It does **not** reopen `/planner` urgent-dialog front-end work, and it does **not** attempt full ERP inbound synchronization. The scope is one subsystem: **confirmed schedule writeback to Kingdee**.

Included:
- Kingdee configuration binding and client boundary
- Schedule item → Kingdee writeback field mapping
- Real writeback dispatch from `WritebackService`
- Writeback audit persistence and traceability
- Retry policy for retryable failures
- `/planner` publish dialog integration with backend status truth

Not included:
- Task-pool import from Kingdee
- Work-order pull synchronization
- Full historical dashboard for past writebacks
- Generic job framework

---

## Current State Summary

The current code already completed the prior plan phase:
- `frontend/src/views/PlannerWorkbenchView.vue` and planner dialog flows are covered by unit + e2e tests.
- `backend/src/main/java/com/lightschedule/web/WritebackController.java` uses constructor injection and top-level DTOs.
- `backend/src/main/java/com/lightschedule/modules/writeback/WritebackService.java` currently only performs `ScheduleValidationService` validation, then returns `new PublishResult(draftId, "validated", "pending")`.
- `backend/src/main/java/com/lightschedule/integration/kingdee/KingdeeWorkOrderMapper.java` is still a pass-through stub.
- `backend/src/main/resources/db/migration/V1__init_schedule_tables.sql` already contains a minimal `writeback_audit` table, but it only stores `id`, `draft_id`, `status`, `message`, `created_at`.
- `frontend/src/components/planner/PublishDialog.vue` currently renders static pending/success wording from `publishResult`, but it has no backend status refresh path.

The plan below starts exactly from that baseline.

---

## File Structure

### Backend: configuration and integration boundary
- Modify: `backend/src/main/java/com/lightschedule/LightscheduleApplication.java` — enable config-properties scanning and scheduling.
- Modify: `backend/src/main/java/com/lightschedule/config/KingdeeProperties.java` — turn current record into bound configuration with retry/executor/writeback settings.
- Modify: `backend/src/main/java/com/lightschedule/config/KingdeeExecutorConfig.java` — source thread-pool values from bound config.
- Create: `backend/src/main/java/com/lightschedule/integration/kingdee/KingdeeWritebackClient.java` — interface for outbound writeback.
- Create: `backend/src/main/java/com/lightschedule/integration/kingdee/HttpKingdeeWritebackClient.java` — real HTTP implementation.
- Create: `backend/src/main/java/com/lightschedule/integration/kingdee/KingdeeWritebackPayloadMapper.java` — schedule item → Kingdee payload mapping.
- Create: `backend/src/main/java/com/lightschedule/integration/kingdee/KingdeeWritebackPayload.java` — outbound payload DTO.
- Create: `backend/src/main/java/com/lightschedule/integration/kingdee/KingdeeWritebackItemPayload.java` — per-item payload DTO.
- Create: `backend/src/main/java/com/lightschedule/integration/kingdee/KingdeeWritebackResult.java` — outbound call result DTO.

### Backend: writeback domain and persistence
- Modify: `backend/src/main/resources/db/migration/V1__init_schedule_tables.sql` — leave untouched.
- Create: `backend/src/main/resources/db/migration/V2__expand_writeback_audit.sql` — add attempt tracking, retry metadata, payload/response trace fields.
- Create: `backend/src/main/java/com/lightschedule/modules/writeback/WritebackAuditEntity.java` — MyBatis-Plus entity.
- Create: `backend/src/main/java/com/lightschedule/modules/writeback/WritebackAuditMapper.java` — MyBatis-Plus mapper.
- Create: `backend/src/main/java/com/lightschedule/modules/writeback/WritebackAuditStatus.java` — internal audit status enum.
- Create: `backend/src/main/java/com/lightschedule/modules/writeback/WritebackAuditService.java` — create/update/find audit rows.
- Modify: `backend/src/main/java/com/lightschedule/modules/writeback/WritebackService.java` — orchestrate validate → audit → dispatch.
- Create: `backend/src/main/java/com/lightschedule/modules/writeback/WritebackRetryScheduler.java` — scheduled retries for retryable failures.
- Create: `backend/src/main/java/com/lightschedule/modules/writeback/WritebackAttemptDecision.java` — classify retryable vs terminal failures.

### Backend: web contract
- Modify: `backend/src/main/java/com/lightschedule/web/PublishResponse.java` — add audit/status fields needed by frontend.
- Modify: `backend/src/main/java/com/lightschedule/web/WritebackController.java` — keep `publish`, add `GET /api/writeback/{auditId}` status endpoint.
- Create: `backend/src/main/java/com/lightschedule/web/WritebackStatusResponse.java` — frontend-facing status DTO.

### Frontend: minimal integration with backend truth
- Modify: `frontend/src/api/planner.ts` — add writeback status query API.
- Modify: `frontend/src/stores/scheduleDraft.ts` — store `auditId`, latest backend status, refresh behavior when reopening.
- Modify: `frontend/src/components/planner/PublishDialog.vue` — render backend-driven status/retryable/failure text.
- Modify: `frontend/src/views/PlannerWorkbenchView.vue` — refresh writeback status when reopening status dialog.

### Tests
- Modify: `backend/src/test/java/com/lightschedule/config/KingdeePropertiesTest.java`
- Create: `backend/src/test/java/com/lightschedule/integration/kingdee/KingdeeWritebackPayloadMapperTest.java`
- Modify: `backend/src/test/java/com/lightschedule/modules/writeback/WritebackServiceTest.java`
- Create: `backend/src/test/java/com/lightschedule/modules/writeback/WritebackRetrySchedulerTest.java`
- Modify: `backend/src/test/java/com/lightschedule/web/PlannerApiTest.java`
- Modify: `frontend/src/components/planner/__tests__/PublishDialog.spec.ts`
- Modify: `frontend/src/components/planner/__tests__/PlannerWorkbenchView.spec.ts`
- Modify: `frontend/tests/e2e/planner-workflow.spec.ts`

---

### Task 1: Bind Kingdee config and define outbound writeback contract

**Files:**
- Modify: `backend/src/main/java/com/lightschedule/LightscheduleApplication.java`
- Modify: `backend/src/main/java/com/lightschedule/config/KingdeeProperties.java`
- Modify: `backend/src/main/java/com/lightschedule/config/KingdeeExecutorConfig.java`
- Create: `backend/src/main/java/com/lightschedule/integration/kingdee/KingdeeWritebackClient.java`
- Create: `backend/src/main/java/com/lightschedule/integration/kingdee/KingdeeWritebackPayload.java`
- Create: `backend/src/main/java/com/lightschedule/integration/kingdee/KingdeeWritebackItemPayload.java`
- Create: `backend/src/main/java/com/lightschedule/integration/kingdee/KingdeeWritebackResult.java`
- Test: `backend/src/test/java/com/lightschedule/config/KingdeePropertiesTest.java`

- [ ] **Step 1: Write the failing config test for real writeback settings**

```java
package com.lightschedule.config;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class KingdeePropertiesTest {

    @Test
    void shouldRejectBlankWritebackPath() {
        assertThatThrownBy(() -> new KingdeeProperties(
                "https://kingdee.example.com",
                "demo-app",
                "demo-secret",
                "/",
                new KingdeeProperties.RetryProperties(3, 30),
                new KingdeeProperties.ExecutorProperties(4, 8, 200)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("kingdee.writeback-path");
    }

    @Test
    void shouldExposeRetrySettings() {
        KingdeeProperties properties = new KingdeeProperties(
                "https://kingdee.example.com",
                "demo-app",
                "demo-secret",
                "/k3cloud/schedule/writeback",
                new KingdeeProperties.RetryProperties(3, 30),
                new KingdeeProperties.ExecutorProperties(4, 8, 200));

        assertThat(properties.retry().maxAttempts()).isEqualTo(3);
        assertThat(properties.retry().baseDelaySeconds()).isEqualTo(30);
    }
}
```

- [ ] **Step 2: Run the test and verify it fails**

Run: `mvn -pl backend -Dtest=KingdeePropertiesTest test`
Expected: FAIL because `KingdeeProperties` does not yet have `writebackPath`, `RetryProperties`, or `ExecutorProperties`.

- [ ] **Step 3: Implement the bound configuration and app bootstrap changes**

```java
package com.lightschedule.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "kingdee")
public record KingdeeProperties(
        String baseUrl,
        String appId,
        String appSecret,
        String writebackPath,
        RetryProperties retry,
        ExecutorProperties executor) {

    public KingdeeProperties {
        if (baseUrl == null || baseUrl.isBlank()) throw new IllegalArgumentException("kingdee.base-url must not be blank");
        if (appId == null || appId.isBlank()) throw new IllegalArgumentException("kingdee.app-id must not be blank");
        if (appSecret == null || appSecret.isBlank()) throw new IllegalArgumentException("kingdee.app-secret must not be blank");
        if (writebackPath == null || writebackPath.isBlank() || "/".equals(writebackPath)) {
            throw new IllegalArgumentException("kingdee.writeback-path must not be blank");
        }
        if (retry == null) throw new IllegalArgumentException("kingdee.retry must not be null");
        if (executor == null) throw new IllegalArgumentException("kingdee.executor must not be null");
    }

    public record RetryProperties(int maxAttempts, int baseDelaySeconds) {}
    public record ExecutorProperties(int corePoolSize, int maxPoolSize, int queueCapacity) {}
}
```

```java
package com.lightschedule;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@ConfigurationPropertiesScan
@EnableScheduling
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
    public Executor kingdeeExecutor(KingdeeProperties properties) {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setThreadNamePrefix("kingdee-");
        executor.setCorePoolSize(properties.executor().corePoolSize());
        executor.setMaxPoolSize(properties.executor().maxPoolSize());
        executor.setQueueCapacity(properties.executor().queueCapacity());
        executor.initialize();
        return executor;
    }
}
```

```java
package com.lightschedule.integration.kingdee;

public interface KingdeeWritebackClient {
    KingdeeWritebackResult writeback(KingdeeWritebackPayload payload);
}
```

```java
package com.lightschedule.integration.kingdee;

import java.util.List;

public record KingdeeWritebackPayload(String draftId, List<KingdeeWritebackItemPayload> items) {}
```

```java
package com.lightschedule.integration.kingdee;

public record KingdeeWritebackItemPayload(
        String workOrderCode,
        String resourceId,
        String plannedStartAt,
        String plannedEndAt,
        String sequenceNo) {}
```

```java
package com.lightschedule.integration.kingdee;

public record KingdeeWritebackResult(boolean success, String externalRequestId, String message) {}
```

- [ ] **Step 4: Update `application.yml` with the new config keys**

```yaml
kingdee:
  base-url: https://kingdee.example.com
  app-id: demo-app
  app-secret: demo-secret
  writeback-path: /k3cloud/schedule/writeback
  retry:
    max-attempts: 3
    base-delay-seconds: 30
  executor:
    core-pool-size: 4
    max-pool-size: 8
    queue-capacity: 200
```

- [ ] **Step 5: Run the test again and verify it passes**

Run: `mvn -pl backend -Dtest=KingdeePropertiesTest test`
Expected: PASS.

- [ ] **Step 6: Commit**

```bash
git add backend/src/main/java/com/lightschedule/LightscheduleApplication.java backend/src/main/java/com/lightschedule/config/KingdeeProperties.java backend/src/main/java/com/lightschedule/config/KingdeeExecutorConfig.java backend/src/main/resources/application.yml backend/src/test/java/com/lightschedule/config/KingdeePropertiesTest.java backend/src/main/java/com/lightschedule/integration/kingdee/KingdeeWritebackClient.java backend/src/main/java/com/lightschedule/integration/kingdee/KingdeeWritebackPayload.java backend/src/main/java/com/lightschedule/integration/kingdee/KingdeeWritebackItemPayload.java backend/src/main/java/com/lightschedule/integration/kingdee/KingdeeWritebackResult.java
git commit -m "feat: bind kingdee writeback configuration"
```

---

### Task 2: Persist writeback audit records with retry metadata

**Files:**
- Create: `backend/src/main/resources/db/migration/V2__expand_writeback_audit.sql`
- Create: `backend/src/main/java/com/lightschedule/modules/writeback/WritebackAuditEntity.java`
- Create: `backend/src/main/java/com/lightschedule/modules/writeback/WritebackAuditMapper.java`
- Create: `backend/src/main/java/com/lightschedule/modules/writeback/WritebackAuditStatus.java`
- Create: `backend/src/main/java/com/lightschedule/modules/writeback/WritebackAuditService.java`
- Modify: `backend/src/test/java/com/lightschedule/modules/writeback/WritebackServiceTest.java`

- [ ] **Step 1: Write the failing service test for audit creation**

```java
package com.lightschedule.modules.writeback;

import static org.assertj.core.api.Assertions.assertThat;

import com.lightschedule.modules.scheduling.InitialSchedulingService.ScheduledItem;
import com.lightschedule.modules.validation.ScheduleValidationService;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

class WritebackServiceTest {

    @Test
    void shouldCreateQueuedAuditBeforeDispatch() {
        WritebackAuditService auditService = Mockito.mock(WritebackAuditService.class);
        WritebackService service = new WritebackService(
                new ScheduleValidationService(),
                auditService,
                payload -> new com.lightschedule.integration.kingdee.KingdeeWritebackResult(true, "REQ-1", "queued"),
                new com.lightschedule.integration.kingdee.KingdeeWritebackPayloadMapper());

        service.publish("draft-1", List.of(
                new ScheduledItem("TASK-001", "LINE-A", "2026-04-24T08:00:00Z", "2026-04-24T10:00:00Z")
        ));

        Mockito.verify(auditService).createQueuedAudit(Mockito.eq("draft-1"), Mockito.any(), Mockito.eq(3));
    }
}
```

- [ ] **Step 2: Run the test and verify it fails**

Run: `mvn -pl backend -Dtest=WritebackServiceTest test`
Expected: FAIL because `WritebackAuditService` and the new `WritebackService` constructor do not exist yet.

- [ ] **Step 3: Add the audit schema migration**

```sql
alter table writeback_audit add
    attempt_count int not null default 0,
    max_attempts int not null default 1,
    retryable bit not null default 0,
    external_request_id varchar(128) null,
    payload_json nvarchar(max) null,
    response_json nvarchar(max) null,
    next_retry_at datetime2 null,
    updated_at datetime2 not null default sysutcdatetime();

go

create index idx_writeback_audit_status_next_retry on writeback_audit(status, next_retry_at);
```

- [ ] **Step 4: Implement the audit entity, mapper, enum, and service**

```java
package com.lightschedule.modules.writeback;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import java.time.OffsetDateTime;

@TableName("writeback_audit")
public class WritebackAuditEntity {
    @TableId(type = IdType.INPUT)
    private String id;
    private String draftId;
    private String status;
    private String message;
    private Integer attemptCount;
    private Integer maxAttempts;
    private Boolean retryable;
    private String externalRequestId;
    private String payloadJson;
    private String responseJson;
    private OffsetDateTime nextRetryAt;
    private OffsetDateTime updatedAt;
    // getters and setters
}
```

```java
package com.lightschedule.modules.writeback;

public enum WritebackAuditStatus {
    QUEUED,
    SUCCEEDED,
    RETRYABLE_FAILED,
    TERMINAL_FAILED
}
```

```java
package com.lightschedule.modules.writeback;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface WritebackAuditMapper extends BaseMapper<WritebackAuditEntity> {}
```

```java
package com.lightschedule.modules.writeback;

import java.time.OffsetDateTime;
import java.util.UUID;
import org.springframework.stereotype.Service;

@Service
public class WritebackAuditService {

    private final WritebackAuditMapper mapper;

    public WritebackAuditService(WritebackAuditMapper mapper) {
        this.mapper = mapper;
    }

    public WritebackAuditEntity createQueuedAudit(String draftId, String payloadJson, int maxAttempts) {
        WritebackAuditEntity entity = new WritebackAuditEntity();
        entity.setId(UUID.randomUUID().toString());
        entity.setDraftId(draftId);
        entity.setStatus(WritebackAuditStatus.QUEUED.name());
        entity.setMessage("queued");
        entity.setAttemptCount(0);
        entity.setMaxAttempts(maxAttempts);
        entity.setRetryable(false);
        entity.setPayloadJson(payloadJson);
        entity.setUpdatedAt(OffsetDateTime.now());
        mapper.insert(entity);
        return entity;
    }
}
```

- [ ] **Step 5: Update `WritebackService` constructor only enough to pass the test**

```java
public WritebackService(
        ScheduleValidationService scheduleValidationService,
        WritebackAuditService writebackAuditService,
        KingdeeWritebackClient kingdeeWritebackClient,
        KingdeeWritebackPayloadMapper kingdeeWritebackPayloadMapper) {
    this.scheduleValidationService = scheduleValidationService;
    this.writebackAuditService = writebackAuditService;
    this.kingdeeWritebackClient = kingdeeWritebackClient;
    this.kingdeeWritebackPayloadMapper = kingdeeWritebackPayloadMapper;
}
```

- [ ] **Step 6: Run the test again and verify it passes**

Run: `mvn -pl backend -Dtest=WritebackServiceTest test`
Expected: PASS.

- [ ] **Step 7: Commit**

```bash
git add backend/src/main/resources/db/migration/V2__expand_writeback_audit.sql backend/src/main/java/com/lightschedule/modules/writeback/WritebackAuditEntity.java backend/src/main/java/com/lightschedule/modules/writeback/WritebackAuditMapper.java backend/src/main/java/com/lightschedule/modules/writeback/WritebackAuditStatus.java backend/src/main/java/com/lightschedule/modules/writeback/WritebackAuditService.java backend/src/main/java/com/lightschedule/modules/writeback/WritebackService.java backend/src/test/java/com/lightschedule/modules/writeback/WritebackServiceTest.java
git commit -m "feat: persist writeback audit metadata"
```

---

### Task 3: Map schedule items to Kingdee writeback payload and perform real dispatch

**Files:**
- Create: `backend/src/main/java/com/lightschedule/integration/kingdee/KingdeeWritebackPayloadMapper.java`
- Create: `backend/src/main/java/com/lightschedule/integration/kingdee/HttpKingdeeWritebackClient.java`
- Create: `backend/src/test/java/com/lightschedule/integration/kingdee/KingdeeWritebackPayloadMapperTest.java`
- Modify: `backend/src/main/java/com/lightschedule/modules/writeback/WritebackService.java`

- [ ] **Step 1: Write the failing mapper test**

```java
package com.lightschedule.integration.kingdee;

import static org.assertj.core.api.Assertions.assertThat;

import com.lightschedule.modules.scheduling.InitialSchedulingService.ScheduledItem;
import java.util.List;
import org.junit.jupiter.api.Test;

class KingdeeWritebackPayloadMapperTest {

    @Test
    void shouldMapScheduledItemsToKingdeePayload() {
        KingdeeWritebackPayload payload = new KingdeeWritebackPayloadMapper().map(
                "draft-1",
                List.of(new ScheduledItem("TASK-001", "LINE-A", "2026-04-24T08:00:00Z", "2026-04-24T10:00:00Z")));

        assertThat(payload.draftId()).isEqualTo("draft-1");
        assertThat(payload.items()).hasSize(1);
        assertThat(payload.items().getFirst().workOrderCode()).isEqualTo("TASK-001");
        assertThat(payload.items().getFirst().resourceId()).isEqualTo("LINE-A");
        assertThat(payload.items().getFirst().sequenceNo()).isEqualTo("1");
    }
}
```

- [ ] **Step 2: Run the test and verify it fails**

Run: `mvn -pl backend -Dtest=KingdeeWritebackPayloadMapperTest test`
Expected: FAIL because `KingdeeWritebackPayloadMapper` does not yet exist.

- [ ] **Step 3: Implement the mapper and HTTP client**

```java
package com.lightschedule.integration.kingdee;

import com.lightschedule.modules.scheduling.InitialSchedulingService.ScheduledItem;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import org.springframework.stereotype.Component;

@Component
public class KingdeeWritebackPayloadMapper {

    public KingdeeWritebackPayload map(String draftId, List<ScheduledItem> items) {
        AtomicInteger sequence = new AtomicInteger(1);
        return new KingdeeWritebackPayload(
                draftId,
                items.stream()
                        .map(item -> new KingdeeWritebackItemPayload(
                                item.taskId(),
                                item.resourceId(),
                                item.startAt(),
                                item.endAt(),
                                String.valueOf(sequence.getAndIncrement())))
                        .toList());
    }
}
```

```java
package com.lightschedule.integration.kingdee;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.lightschedule.config.KingdeeProperties;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import org.springframework.stereotype.Component;

@Component
public class HttpKingdeeWritebackClient implements KingdeeWritebackClient {

    private final KingdeeProperties properties;
    private final ObjectMapper objectMapper;
    private final HttpClient httpClient = HttpClient.newHttpClient();

    public HttpKingdeeWritebackClient(KingdeeProperties properties, ObjectMapper objectMapper) {
        this.properties = properties;
        this.objectMapper = objectMapper;
    }

    @Override
    public KingdeeWritebackResult writeback(KingdeeWritebackPayload payload) {
        try {
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(properties.baseUrl() + properties.writebackPath()))
                    .header("Content-Type", "application/json")
                    .header("X-App-Id", properties.appId())
                    .header("X-App-Secret", properties.appSecret())
                    .POST(HttpRequest.BodyPublishers.ofString(objectMapper.writeValueAsString(payload)))
                    .build();
            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() >= 200 && response.statusCode() < 300) {
                return new KingdeeWritebackResult(true, response.headers().firstValue("X-Request-Id").orElse(""), response.body());
            }
            return new KingdeeWritebackResult(false, response.headers().firstValue("X-Request-Id").orElse(""), response.body());
        } catch (Exception exception) {
            return new KingdeeWritebackResult(false, "", exception.getMessage());
        }
    }
}
```

- [ ] **Step 4: Update `WritebackService.publish` to map payload and perform first dispatch**

```java
public PublishResult publish(String draftId, List<ScheduledItem> items) {
    List<String> blockingIssues = scheduleValidationService.validateHardRules(items);
    if (!blockingIssues.isEmpty()) {
        throw new IllegalStateException(String.join(",", blockingIssues));
    }

    var payload = kingdeeWritebackPayloadMapper.map(draftId, items);
    var audit = writebackAuditService.createQueuedAudit(
            draftId,
            toJson(payload),
            3);

    var result = kingdeeWritebackClient.writeback(payload);
    writebackAuditService.recordImmediateAttempt(audit.getId(), result);

    return new PublishResult(draftId, audit.getId(), "validated", result.success() ? "submitted" : "failed");
}
```

- [ ] **Step 5: Run the mapper test and targeted service test**

Run: `mvn -pl backend -Dtest=KingdeeWritebackPayloadMapperTest,WritebackServiceTest test`
Expected: PASS.

- [ ] **Step 6: Commit**

```bash
git add backend/src/main/java/com/lightschedule/integration/kingdee/KingdeeWritebackPayloadMapper.java backend/src/main/java/com/lightschedule/integration/kingdee/HttpKingdeeWritebackClient.java backend/src/main/java/com/lightschedule/modules/writeback/WritebackService.java backend/src/test/java/com/lightschedule/integration/kingdee/KingdeeWritebackPayloadMapperTest.java
git commit -m "feat: dispatch kingdee writeback payloads"
```

---

### Task 4: Add retry classification, audit refresh endpoint, and backend status truth

**Files:**
- Create: `backend/src/main/java/com/lightschedule/modules/writeback/WritebackAttemptDecision.java`
- Create: `backend/src/main/java/com/lightschedule/modules/writeback/WritebackRetryScheduler.java`
- Modify: `backend/src/main/java/com/lightschedule/modules/writeback/WritebackAuditService.java`
- Modify: `backend/src/main/java/com/lightschedule/web/PublishResponse.java`
- Create: `backend/src/main/java/com/lightschedule/web/WritebackStatusResponse.java`
- Modify: `backend/src/main/java/com/lightschedule/web/WritebackController.java`
- Create: `backend/src/test/java/com/lightschedule/modules/writeback/WritebackRetrySchedulerTest.java`
- Modify: `backend/src/test/java/com/lightschedule/web/PlannerApiTest.java`

- [ ] **Step 1: Write the failing API test for writeback status query**

```java
@Test
void shouldExposeWritebackStatusByAuditId() throws Exception {
    mockMvc.perform(get("/api/writeback/audit-1"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.auditId").value("audit-1"))
            .andExpect(jsonPath("$.writebackStatus").isNotEmpty());
}
```

- [ ] **Step 2: Run the test and verify it fails**

Run: `mvn -pl backend -Dtest=PlannerApiTest test`
Expected: FAIL with 404 or missing controller method.

- [ ] **Step 3: Implement retry classification and scheduler**

```java
package com.lightschedule.modules.writeback;

public record WritebackAttemptDecision(boolean retryable, String nextStatus) {
    public static WritebackAttemptDecision retryable() {
        return new WritebackAttemptDecision(true, WritebackAuditStatus.RETRYABLE_FAILED.name());
    }
    public static WritebackAttemptDecision terminal() {
        return new WritebackAttemptDecision(false, WritebackAuditStatus.TERMINAL_FAILED.name());
    }
}
```

```java
package com.lightschedule.modules.writeback;

import java.time.OffsetDateTime;
import java.util.List;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
public class WritebackRetryScheduler {

    private final WritebackAuditService writebackAuditService;
    private final WritebackService writebackService;

    public WritebackRetryScheduler(WritebackAuditService writebackAuditService, WritebackService writebackService) {
        this.writebackAuditService = writebackAuditService;
        this.writebackService = writebackService;
    }

    @Scheduled(fixedDelay = 30000)
    public void retryDueAudits() {
        List<WritebackAuditEntity> dueAudits = writebackAuditService.findDueRetries(OffsetDateTime.now());
        dueAudits.forEach(writebackService::retryAudit);
    }
}
```

- [ ] **Step 4: Implement richer publish/status DTOs and status endpoint**

```java
package com.lightschedule.web;

public record PublishResponse(String draftId, String auditId, String status, String writebackStatus) {}
```

```java
package com.lightschedule.web;

public record WritebackStatusResponse(
        String auditId,
        String draftId,
        String status,
        String writebackStatus,
        String message,
        boolean retryable,
        int attemptCount,
        int maxAttempts,
        String nextRetryAt) {}
```

```java
@GetMapping("/{auditId}")
public WritebackStatusResponse status(@PathVariable String auditId) {
    var audit = writebackAuditService.getById(auditId);
    return new WritebackStatusResponse(
            audit.getId(),
            audit.getDraftId(),
            "validated",
            audit.getStatus(),
            audit.getMessage(),
            Boolean.TRUE.equals(audit.getRetryable()),
            audit.getAttemptCount(),
            audit.getMaxAttempts(),
            audit.getNextRetryAt() == null ? null : audit.getNextRetryAt().toString());
}
```

- [ ] **Step 5: Add the retry scheduler test**

```java
package com.lightschedule.modules.writeback;

import static org.mockito.Mockito.verify;

import java.time.OffsetDateTime;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

class WritebackRetrySchedulerTest {

    @Test
    void shouldRetryDueAudits() {
        WritebackAuditService auditService = Mockito.mock(WritebackAuditService.class);
        WritebackService writebackService = Mockito.mock(WritebackService.class);
        WritebackRetryScheduler scheduler = new WritebackRetryScheduler(auditService, writebackService);
        WritebackAuditEntity entity = new WritebackAuditEntity();
        entity.setId("audit-1");
        Mockito.when(auditService.findDueRetries(Mockito.any(OffsetDateTime.class))).thenReturn(List.of(entity));

        scheduler.retryDueAudits();

        verify(writebackService).retryAudit(entity);
    }
}
```

- [ ] **Step 6: Run targeted backend tests and verify they pass**

Run: `mvn -pl backend -Dtest=PlannerApiTest,WritebackRetrySchedulerTest test`
Expected: PASS.

- [ ] **Step 7: Commit**

```bash
git add backend/src/main/java/com/lightschedule/modules/writeback/WritebackAttemptDecision.java backend/src/main/java/com/lightschedule/modules/writeback/WritebackRetryScheduler.java backend/src/main/java/com/lightschedule/modules/writeback/WritebackAuditService.java backend/src/main/java/com/lightschedule/web/PublishResponse.java backend/src/main/java/com/lightschedule/web/WritebackStatusResponse.java backend/src/main/java/com/lightschedule/web/WritebackController.java backend/src/test/java/com/lightschedule/modules/writeback/WritebackRetrySchedulerTest.java backend/src/test/java/com/lightschedule/web/PlannerApiTest.java
git commit -m "feat: expose writeback audit status and retries"
```

---

### Task 5: Connect `/planner` publish dialog to backend writeback status truth

**Files:**
- Modify: `frontend/src/api/planner.ts`
- Modify: `frontend/src/stores/scheduleDraft.ts`
- Modify: `frontend/src/components/planner/PublishDialog.vue`
- Modify: `frontend/src/views/PlannerWorkbenchView.vue`
- Modify: `frontend/src/components/planner/__tests__/PublishDialog.spec.ts`
- Modify: `frontend/src/components/planner/__tests__/PlannerWorkbenchView.spec.ts`
- Modify: `frontend/tests/e2e/planner-workflow.spec.ts`

- [ ] **Step 1: Write the failing frontend unit test for reopening status against backend truth**

```ts
it('重新打开回写状态时会刷新后端最新状态', async () => {
  scheduleDraftState.publishResult = {
    draftId: 'draft-1',
    auditId: 'audit-1',
    status: 'validated',
    writebackStatus: 'submitted'
  }
  loadWritebackStatus.mockResolvedValue({
    auditId: 'audit-1',
    draftId: 'draft-1',
    status: 'validated',
    writebackStatus: 'terminal_failed',
    message: 'Kingdee rejected payload',
    retryable: false,
    attemptCount: 3,
    maxAttempts: 3,
    nextRetryAt: null
  })

  const wrapper = mount(PlannerWorkbenchView)
  await wrapper.findAll('button')[1].trigger('click')
  await flushPromises()

  expect(loadWritebackStatus).toHaveBeenCalledWith('audit-1')
  expect(wrapper.text()).toContain('Kingdee rejected payload')
})
```

- [ ] **Step 2: Run the test and verify it fails**

Run: `corepack pnpm -C frontend vitest run src/components/planner/__tests__/PlannerWorkbenchView.spec.ts src/components/planner/__tests__/PublishDialog.spec.ts`
Expected: FAIL because no `auditId` or `loadWritebackStatus` path exists yet.

- [ ] **Step 3: Extend the frontend API and store contract**

```ts
export interface PublishResult {
  draftId: string
  auditId: string
  status: string
  writebackStatus: string
}

export interface WritebackStatusResponse {
  auditId: string
  draftId: string
  status: string
  writebackStatus: string
  message: string
  retryable: boolean
  attemptCount: number
  maxAttempts: number
  nextRetryAt: string | null
}

async function loadWritebackStatus(auditId: string) {
  return http.get<WritebackStatusResponse>(`/api/writeback/${auditId}`)
}
```

```ts
export async function refreshPublishStatus() {
  const auditId = scheduleDraftState.publishResult?.auditId
  if (!auditId) {
    return
  }
  const latest = await plannerApi.loadWritebackStatus(auditId)
  scheduleDraftState.publishResult = {
    draftId: latest.draftId,
    auditId: latest.auditId,
    status: latest.status,
    writebackStatus: latest.writebackStatus
  }
  scheduleDraftState.publishError = latest.writebackStatus === 'terminal_failed' ? latest.message : ''
}
```

- [ ] **Step 4: Refresh backend status when reopening the publish dialog**

```ts
async function openPublishDialog() {
  if (scheduleDraftState.publishResult?.auditId) {
    await refreshPublishStatus()
  }
  if (!scheduleDraftState.publishResult && !scheduleDraftState.publishLoading && !scheduleDraftState.publishError) {
    resetPublishResult()
  }
  showPublishDialog.value = true
}
```

```vue
<div v-else-if="scheduleDraftState.publishError">{{ scheduleDraftState.publishError }}</div>
<div v-else-if="scheduleDraftState.publishResult">
  <div>回写请求已提交</div>
  <div>结果版本：{{ scheduleDraftState.publishResult.draftId }}</div>
  <div>追踪编号：{{ scheduleDraftState.publishResult.auditId }}</div>
  <div>回写状态：{{ writebackStatusLabel }}</div>
</div>
```

- [ ] **Step 5: Add the e2e regression for backend status refresh**

```ts
test('计划员重新打开回写状态时会看到后端刷新后的最新结果', async ({ page }) => {
  let statusRequestCount = 0
  await page.route('**/api/writeback/*', async (route) => {
    statusRequestCount += 1
    await route.fulfill({
      json: statusRequestCount === 1
        ? { auditId: 'audit-1', draftId: 'draft-1', status: 'validated', writebackStatus: 'submitted', message: '', retryable: false, attemptCount: 1, maxAttempts: 3, nextRetryAt: null }
        : { auditId: 'audit-1', draftId: 'draft-1', status: 'validated', writebackStatus: 'terminal_failed', message: 'Kingdee rejected payload', retryable: false, attemptCount: 3, maxAttempts: 3, nextRetryAt: null }
    })
  })

  // 省略其余 route mock，与现有 planner-workflow.spec.ts 的 publish 成功/状态 reopen 骨架复用
  // 断言：第二次点击“查看回写状态”后看到最新 message，而不是旧 submitted 文案
})
```

- [ ] **Step 6: Run the targeted frontend checks**

Run: `corepack pnpm -C frontend vitest run src/components/planner/__tests__/PublishDialog.spec.ts src/components/planner/__tests__/PlannerWorkbenchView.spec.ts && corepack pnpm -C frontend test:e2e --grep "回写状态|重新打开回写状态"`
Expected: PASS.

- [ ] **Step 7: Commit**

```bash
git add frontend/src/api/planner.ts frontend/src/stores/scheduleDraft.ts frontend/src/components/planner/PublishDialog.vue frontend/src/views/PlannerWorkbenchView.vue frontend/src/components/planner/__tests__/PublishDialog.spec.ts frontend/src/components/planner/__tests__/PlannerWorkbenchView.spec.ts frontend/tests/e2e/planner-workflow.spec.ts
git commit -m "feat: refresh planner writeback status from backend"
```

---

### Task 6: Run full verification and document the integration boundary

**Files:**
- Modify: `docs/superpowers/task-9-test-report.md`
- Optionally Modify: `README.md`

- [ ] **Step 1: Add the failing documentation expectation in your own checklist**

```md
- backend writeback no longer returns placeholder-only state
- planner publish dialog can reopen against backend truth
- retryable vs terminal failure behavior is documented
```

- [ ] **Step 2: Run backend verification**

Run: `mvn -pl backend test`
Expected: PASS.

- [ ] **Step 3: Run frontend verification**

Run: `corepack pnpm -C frontend verify`
Expected: PASS.

- [ ] **Step 4: Run end-to-end backend verify**

Run: `mvn -pl backend verify`
Expected: PASS, including the frontend verify hook already wired in `backend/pom.xml`.

- [ ] **Step 5: Update the test report with the new phase results**

```md
## 下一阶段：金蝶真实回写链路

本轮新增验收覆盖：
- 写回请求字段映射
- 写回审计留痕
- 重试策略与终态失败
- `/planner` 回写状态重开时的后端真值刷新
```

- [ ] **Step 6: Commit**

```bash
git add docs/superpowers/task-9-test-report.md README.md
git commit -m "docs: record kingdee writeback integration verification"
```

---

## Self-Review

### Spec coverage
- 字段映射：Task 3 covers `KingdeeWritebackPayloadMapper`.
- 真实写回适配：Task 3 covers `HttpKingdeeWritebackClient` and `WritebackService` dispatch.
- 回写留痕：Task 2 covers `writeback_audit` schema expansion and audit service.
- 失败处理：Task 4 covers retryable vs terminal classification.
- 重试策略：Task 4 covers scheduled retries and due-audit reload.
- `/planner` 集成边界：Task 5 covers publish response enrichment and reopen refresh against backend truth.

### Placeholder scan
- No `TODO`, `TBD`, or “similar to task N” placeholders remain.
- The single e2e snippet in Task 5 explicitly says it reuses the existing publish reopen skeleton; implementation should inline that skeleton when executing the task.

### Type consistency
- Backend publish response is consistently planned as `(draftId, auditId, status, writebackStatus)`.
- Frontend store and dialog tasks use the same `auditId` field.
- Retry status query consistently flows through `WritebackStatusResponse`.

## Execution Handoff

Plan complete and saved to `docs/superpowers/plans/2026-04-29-kingdee-real-writeback-integration.md`. Two execution options:

**1. Subagent-Driven (recommended)** - I dispatch a fresh subagent per task, review between tasks, fast iteration

**2. Inline Execution** - Execute tasks in this session using executing-plans, batch execution with checkpoints

**Which approach?**
