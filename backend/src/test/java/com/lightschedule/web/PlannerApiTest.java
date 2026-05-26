package com.lightschedule.web;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.hamcrest.Matchers.startsWith;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

import java.util.NoSuchElementException;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.lightschedule.LightscheduleApplication;
import com.lightschedule.modules.capacity.CapacityAssessmentService.AssessmentResult;
import com.lightschedule.modules.replan.UrgentReplanService.ReplanResult;
import com.lightschedule.modules.writeback.WritebackRetryScheduler;
import com.lightschedule.modules.writeback.WritebackService;
import com.lightschedule.modules.writeback.WritebackService.PublishResult;
import com.lightschedule.modules.writeback.WritebackService.WritebackStatus;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest(classes = LightscheduleApplication.class, properties = "spring.task.scheduling.enabled=false")
@AutoConfigureMockMvc
class PlannerApiTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private WritebackService writebackService;

    @MockBean
    private WritebackRetryScheduler writebackRetryScheduler;

    @MockBean
    private com.lightschedule.modules.scheduling.ScheduleDraftService scheduleDraftService;

    @MockBean
    private com.lightschedule.modules.resource.ResourceCatalogService resourceCatalogService;

    @MockBean
    private com.lightschedule.modules.taskpool.WorkOrderService workOrderService;

    @MockBean
    private com.lightschedule.modules.scheduling.RouteStepService routeStepService;

    @BeforeEach
    void setUpWritebackService() {
        when(writebackService.publish(eq("draft-urgent-1"), anyList()))
                .thenReturn(new PublishResult("draft-urgent-1", "audit-1", "validated", "pending"));
        when(writebackService.status("audit-1"))
                .thenReturn(new WritebackStatus("audit-1", "draft-1", "validated", "submitted", "queued", false, 1, 3, null));
        when(resourceCatalogService.list())
                .thenReturn(java.util.List.of(
                        new com.lightschedule.modules.resource.ResourceCatalogService.ResourceDefinition("LINE-A", "冲压组", true),
                        new com.lightschedule.modules.resource.ResourceCatalogService.ResourceDefinition("LINE-B", "冲压组", true),
                        new com.lightschedule.modules.resource.ResourceCatalogService.ResourceDefinition("LINE-C", "装配组", false)));
        when(resourceCatalogService.listDefaultPlannerResourceIds())
                .thenReturn(java.util.List.of("LINE-A", "LINE-B"));
        when(resourceCatalogService.getGroupName("LINE-A")).thenReturn("冲压组");
        when(resourceCatalogService.getGroupName("LINE-B")).thenReturn("冲压组");
        when(resourceCatalogService.getGroupName("LINE-C")).thenReturn("装配组");
        when(workOrderService.loadAll())
                .thenReturn(java.util.List.of(
                        new com.lightschedule.domain.model.WorkOrder("WO-001", "released", 20, "2026-04-24T08:00:00Z", "ROUTE-01", false, java.util.List.of(), "low")));
        when(routeStepService.loadTasksForRouteIds(java.util.List.of("ROUTE-01"), "LINE-A"))
                .thenReturn(java.util.List.of(
                        new com.lightschedule.modules.scheduling.PlannerScenarioService.PlannerTask("TASK-001", "LINE-A", 120, java.util.List.of()),
                        new com.lightschedule.modules.scheduling.PlannerScenarioService.PlannerTask("TASK-002", "LINE-A", 90, java.util.List.of("TASK-001"))));
        when(routeStepService.listRoutes())
                .thenReturn(java.util.List.of("ROUTE-01"));
        when(routeStepService.listRouteSteps("ROUTE-01"))
                .thenReturn(java.util.List.of(
                        new com.lightschedule.modules.scheduling.RouteStepService.RouteStepDetail("TASK-001", 120, java.util.List.of()),
                        new com.lightschedule.modules.scheduling.RouteStepService.RouteStepDetail("TASK-002", 90, java.util.List.of("TASK-001"))));
    }

    @Test
    void shouldExposeNonEmptyTaskPoolForPlanner() throws Exception {
        mockMvc.perform(get("/api/task-pool"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].workOrderCode").value("WO-001"))
                .andExpect(jsonPath("$[0].dueAt").value("2026-04-24T08:00:00Z"))
                .andExpect(jsonPath("$[0].urgent").value(false))
                .andExpect(jsonPath("$[0].materialRisk").value("low"))
                .andExpect(jsonPath("$[0].readiness").value("ready"));
    }

    @Test
    void shouldExposeOrderedDraftScheduleTimelineForPlanner() throws Exception {
        mockMvc.perform(post("/api/schedules/draft"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.draftId").value(startsWith("draft-")))
                .andExpect(jsonPath("$.items[0].taskId").value("TASK-001"))
                .andExpect(jsonPath("$.items[0].resourceId").value("LINE-A"))
                .andExpect(jsonPath("$.items[0].resourceGroupName").value("冲压组"))
                .andExpect(jsonPath("$.items[0].startAt").value("2026-04-24T08:00:00Z"))
                .andExpect(jsonPath("$.items[0].endAt").value("2026-04-24T10:00:00Z"))
                .andExpect(jsonPath("$.items[1].taskId").value("TASK-002"))
                .andExpect(jsonPath("$.items[1].resourceId").value("LINE-A"))
                .andExpect(jsonPath("$.items[1].resourceGroupName").value("冲压组"))
                .andExpect(jsonPath("$.items[1].startAt").value("2026-04-24T10:00:00Z"))
                .andExpect(jsonPath("$.items[1].endAt").value("2026-04-24T11:30:00Z"));
    }

    @Test
    void shouldAcceptUrgentReplanRequestBodyForPlanner() throws Exception {
        mockMvc.perform(post("/api/replans/urgent")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "urgentTaskId": "WO-URGENT-001",
                                  "urgentResourceId": "LINE-A",
                                  "urgentStartAt": "2026-04-24T09:30:00Z",
                                  "urgentEndAt": "2026-04-24T10:30:00Z",
                                  "items": [
                                    {
                                      "taskId": "TASK-001",
                                      "resourceId": "LINE-A",
                                      "startAt": "2026-04-24T08:00:00Z",
                                      "endAt": "2026-04-24T10:00:00Z"
                                    }
                                  ]
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.urgentTaskId").value("WO-URGENT-001"))
                .andExpect(jsonPath("$.affectedTaskIds[0]").value("TASK-001"))
                .andExpect(jsonPath("$.affectedTaskIds[1]").value("WO-URGENT-001"))
                .andExpect(jsonPath("$.suggestions[0].action").value("reassign_same_group"))
                .andExpect(jsonPath("$.suggestions[0].reason").value("冲压组仍有剩余能力"));
    }

    @Test
    void shouldIncludeDependentTasksInUrgentReplanImpactChain() throws Exception {
        mockMvc.perform(post("/api/replans/urgent")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "urgentTaskId": "WO-URGENT-001",
                                  "urgentResourceId": "LINE-A",
                                  "urgentStartAt": "2026-04-24T09:30:00Z",
                                  "urgentEndAt": "2026-04-24T10:30:00Z",
                                  "items": [
                                    {
                                      "taskId": "TASK-001",
                                      "resourceId": "LINE-A",
                                      "startAt": "2026-04-24T08:00:00Z",
                                      "endAt": "2026-04-24T10:00:00Z"
                                    },
                                    {
                                      "taskId": "TASK-002",
                                      "resourceId": "LINE-B",
                                      "startAt": "2026-04-24T10:30:00Z",
                                      "endAt": "2026-04-24T12:00:00Z"
                                    }
                                  ]
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.affectedTaskIds[0]").value("TASK-001"))
                .andExpect(jsonPath("$.affectedTaskIds[1]").value("WO-URGENT-001"))
                .andExpect(jsonPath("$.affectedTaskIds[2]").value("TASK-002"));
    }

    @Test
    void shouldAcceptPublishRequestBodyForPlanner() throws Exception {
        mockMvc.perform(post("/api/writeback/publish")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "draftId": "draft-urgent-1",
                                  "items": [
                                    {
                                      "taskId": "TASK-001",
                                      "resourceId": "LINE-A",
                                      "startAt": "2026-04-24T08:00:00Z",
                                      "endAt": "2026-04-24T10:00:00Z"
                                    }
                                  ]
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.draftId").value("draft-urgent-1"))
                .andExpect(jsonPath("$.status").value("validated"))
                .andExpect(jsonPath("$.writebackStatus").value("pending"));
    }

    @Test
    void shouldExposeWritebackStatusByAuditId() throws Exception {
        mockMvc.perform(get("/api/writeback/audit-1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.auditId").value("audit-1"))
                .andExpect(jsonPath("$.writebackStatus").value("submitted"));
    }

    @Test
    void shouldExposeRetriedSubmittedWritebackTruthByAuditId() throws Exception {
        when(writebackService.status("audit-2"))
                .thenReturn(new WritebackStatus(
                        "audit-2",
                        "draft-2",
                        "validated",
                        "submitted",
                        "accepted by kingdee",
                        false,
                        2,
                        3,
                        null));

        mockMvc.perform(get("/api/writeback/audit-2"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.auditId").value("audit-2"))
                .andExpect(jsonPath("$.writebackStatus").value("submitted"))
                .andExpect(jsonPath("$.message").value("accepted by kingdee"))
                .andExpect(jsonPath("$.retryable").value(false))
                .andExpect(jsonPath("$.attemptCount").value(2))
                .andExpect(jsonPath("$.maxAttempts").value(3))
                .andExpect(jsonPath("$.nextRetryAt").doesNotExist());
    }

    @Test
    void shouldExposeRetryableFailedWritebackTruthByAuditId() throws Exception {
        when(writebackService.status("audit-3"))
                .thenReturn(new WritebackStatus(
                        "audit-3",
                        "draft-3",
                        "validated",
                        "RETRYABLE_FAILED",
                        "kingdee timeout",
                        true,
                        2,
                        3,
                        "2026-04-24T10:30Z"));

        mockMvc.perform(get("/api/writeback/audit-3"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.auditId").value("audit-3"))
                .andExpect(jsonPath("$.writebackStatus").value("RETRYABLE_FAILED"))
                .andExpect(jsonPath("$.message").value("kingdee timeout"))
                .andExpect(jsonPath("$.retryable").value(true))
                .andExpect(jsonPath("$.attemptCount").value(2))
                .andExpect(jsonPath("$.maxAttempts").value(3))
                .andExpect(jsonPath("$.nextRetryAt").value("2026-04-24T10:30Z"));
    }

    @Test
    void shouldExposeTerminalFailedWritebackStatusTruthByAuditId() throws Exception {
        when(writebackService.status("audit-4"))
                .thenReturn(new WritebackStatus(
                        "audit-4",
                        "draft-4",
                        "validated",
                        "TERMINAL_FAILED",
                        "Kingdee rejected payload",
                        false,
                        3,
                        3,
                        null));

        mockMvc.perform(get("/api/writeback/audit-4"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.auditId").value("audit-4"))
                .andExpect(jsonPath("$.writebackStatus").value("TERMINAL_FAILED"))
                .andExpect(jsonPath("$.message").value("Kingdee rejected payload"));
    }

    @Test
    void shouldReturnNotFoundWhenWritebackAuditDoesNotExist() throws Exception {
        when(writebackService.status("audit-404"))
                .thenThrow(new NoSuchElementException("writeback audit not found"));

        mockMvc.perform(get("/api/writeback/audit-404"))
                .andExpect(status().isNotFound());
    }

    @Test
    void shouldInjectPlannerScenarioServiceIntoTaskPoolController() {
        List<String> fieldNames = java.util.Arrays.stream(TaskPoolController.class.getDeclaredFields())
                .map(Field::getName)
                .toList();

        assertEquals(List.of("plannerScenarioService", "taskPoolService"), fieldNames);
    }

    @Test
    void shouldInjectPlannerScenarioServiceIntoScheduleController() {
        List<String> fieldNames = java.util.Arrays.stream(ScheduleController.class.getDeclaredFields())
                .map(Field::getName)
                .toList();

        assertEquals(List.of("plannerScenarioService", "initialSchedulingService", "resourceGroupService", "scheduleDraftService", "objectMapper"), fieldNames);
    }

    @Test
    void shouldInjectUrgentReplanServiceIntoController() {
        List<String> fieldNames = java.util.Arrays.stream(ReplanController.class.getDeclaredFields())
                .map(Field::getName)
                .toList();

        assertEquals(List.of("urgentReplanService"), fieldNames);
    }

    @Test
    void shouldInjectWritebackServiceIntoController() {
        List<String> fieldNames = java.util.Arrays.stream(WritebackController.class.getDeclaredFields())
                .map(Field::getName)
                .toList();

        assertEquals(List.of("writebackService"), fieldNames);
    }

    @Test
    void shouldInjectPlannerScenarioAndAssessmentServicesIntoDashboardController() {
        List<String> fieldNames = java.util.Arrays.stream(DashboardController.class.getDeclaredFields())
                .map(Field::getName)
                .toList();

        assertEquals(List.of("plannerScenarioService", "capacityAssessmentService", "workOrderService", "resourceCatalogService"), fieldNames);
    }

    @Test
    void shouldInjectCapacityAnalysisOverviewServiceIntoController() {
        List<String> fieldNames = java.util.Arrays.stream(CapacityAnalysisController.class.getDeclaredFields())
                .map(Field::getName)
                .toList();

        assertEquals(List.of("capacityAnalysisOverviewService"), fieldNames);
    }

    @Test
    void shouldInjectWorkOrderServiceIntoController() {
        List<String> fieldNames = java.util.Arrays.stream(WorkOrderController.class.getDeclaredFields())
                .map(Field::getName)
                .toList();

        assertEquals(List.of("workOrderService"), fieldNames);
    }

    @Test
    void shouldInjectResourceCatalogServiceIntoResourceController() {
        List<String> fieldNames = java.util.Arrays.stream(ResourceController.class.getDeclaredFields())
                .map(Field::getName)
                .toList();

        assertEquals(List.of("resourceCatalogService"), fieldNames);
    }

    @Test
    void shouldInjectRouteStepServiceIntoRouteController() {
        List<String> fieldNames = java.util.Arrays.stream(RouteController.class.getDeclaredFields())
                .map(Field::getName)
                .toList();

        assertEquals(List.of("routeStepService"), fieldNames);
    }

    @Test
    void shouldExposeResourceList() throws Exception {
        mockMvc.perform(get("/api/resources"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].resourceId").value("LINE-A"))
                .andExpect(jsonPath("$[0].groupName").value("冲压组"))
                .andExpect(jsonPath("$[0].defaultPlannerResource").value(true))
                .andExpect(jsonPath("$[2].resourceId").value("LINE-C"))
                .andExpect(jsonPath("$[2].defaultPlannerResource").value(false));
    }

    @Test
    void shouldExposeRouteList() throws Exception {
        mockMvc.perform(get("/api/routes"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0]").value("ROUTE-01"));
    }

    @Test
    void shouldExposeRouteSteps() throws Exception {
        mockMvc.perform(get("/api/routes/ROUTE-01/steps"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].stepId").value("TASK-001"))
                .andExpect(jsonPath("$[0].requiredMinutes").value(120))
                .andExpect(jsonPath("$[0].dependencyStepIds").isEmpty())
                .andExpect(jsonPath("$[1].stepId").value("TASK-002"))
                .andExpect(jsonPath("$[1].requiredMinutes").value(90))
                .andExpect(jsonPath("$[1].dependencyStepIds[0]").value("TASK-001"));
    }

    @Test
    void shouldUseTopLevelRequestDtosForPlannerControllers() throws Exception {
        Method publish = WritebackController.class.getDeclaredMethod("publish", PublishRequest.class);
        Method urgent = ReplanController.class.getDeclaredMethod("urgent", UrgentReplanRequest.class);

        assertFalse(publish.getParameterTypes()[0].isMemberClass());
        assertFalse(urgent.getParameterTypes()[0].isMemberClass());
    }

    @Test
    void shouldUseTopLevelResponseDtosForPlannerControllers() throws Exception {
        Method list = TaskPoolController.class.getDeclaredMethod("list");
        Method draft = ScheduleController.class.getDeclaredMethod("draft");
        Method summary = DashboardController.class.getDeclaredMethod("capacitySummary");
        Method publish = WritebackController.class.getDeclaredMethod("publish", PublishRequest.class);
        Method urgent = ReplanController.class.getDeclaredMethod("urgent", UrgentReplanRequest.class);

        assertEquals(List.class, list.getReturnType());
        assertEquals("java.util.List<com.lightschedule.web.TaskPoolItemResponse>", list.getGenericReturnType().getTypeName());
        assertEquals(Class.forName("com.lightschedule.web.DraftScheduleResponse"), draft.getReturnType());
        assertEquals(Class.forName("com.lightschedule.web.DashboardCapacitySummaryResponse"), summary.getReturnType());
        assertFalse(draft.getReturnType().isMemberClass());
        assertFalse(summary.getReturnType().isMemberClass());
        assertFalse(publish.getReturnType().isMemberClass());
        assertFalse(urgent.getReturnType().isMemberClass());
        assertEquals("java.util.List<com.lightschedule.web.DraftScheduleItemResponse>",
                ((java.lang.reflect.ParameterizedType) draft.getReturnType()
                        .getRecordComponents()[1]
                        .getGenericType())
                        .getTypeName());
        assertEquals("java.util.List<com.lightschedule.web.UrgentReplanSuggestionResponse>",
                ((java.lang.reflect.ParameterizedType) urgent.getReturnType()
                        .getRecordComponents()[2]
                        .getGenericType())
                        .getTypeName());
        assertNotEquals(Object.class, draft.getReturnType());
        assertNotEquals(Object.class, summary.getReturnType());
        assertNotEquals(AssessmentResult.class, summary.getReturnType());
        assertNotEquals(PublishResult.class, publish.getReturnType());
        assertNotEquals(ReplanResult.class, urgent.getReturnType());
    }

    @Test
    void shouldUseTopLevelResponseDtosForCapacityAnalysisController() throws Exception {
        Method overview = CapacityAnalysisController.class.getDeclaredMethod("overview");

        assertEquals(Class.forName("com.lightschedule.web.CapacityAnalysisOverviewResponse"), overview.getReturnType());
        assertFalse(overview.getReturnType().isMemberClass());
    }

    @Test
    void shouldExposeCapacityAnalysisOverview() throws Exception {
        mockMvc.perform(get("/api/capacity-analysis/overview"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.trends[0].resourceId").value("LINE-A"))
                .andExpect(jsonPath("$.trends[0].bucketLabel").value("2026-04-24 08:00"))
                .andExpect(jsonPath("$.trends[0].status").value("feasible"))
                .andExpect(jsonPath("$.trends[0].loadRate").value(0.44))
                .andExpect(jsonPath("$.groupDiffs[0].groupName").value("冲压组"))
                .andExpect(jsonPath("$.groupDiffs[0].gapRate").value(0.44))
                .andExpect(jsonPath("$.peakPeriods[0].bucketLabel").value("2026-04-24 08:00"))
                .andExpect(jsonPath("$.peakPeriods[0].status").value("tight"))
                .andExpect(jsonPath("$.peakPeriods[0].loadRate").value(1.0));
    }

    @Test
    void shouldExposeCapacitySummaryForDashboard() throws Exception {
        mockMvc.perform(get("/api/dashboard/capacity-summary"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("feasible"))
                .andExpect(jsonPath("$.loadRate").value(0.44));
    }

    @Test
    void shouldExposeSavedDraftByDraftId() throws Exception {
        when(scheduleDraftService.findById("draft-1"))
                .thenReturn(new com.lightschedule.modules.scheduling.ScheduleDraftEntity() {{
                    setId("draft-1");
                    setDraftPayload("""
                            {
                              "draftId": "draft-1",
                              "items": [
                                {"taskId": "TASK-001", "resourceId": "LINE-A", "resourceGroupName": "冲压组", "startAt": "2026-04-24T08:00:00Z", "endAt": "2026-04-24T10:00:00Z"}
                              ]
                            }
                            """);
                }});

        mockMvc.perform(get("/api/schedules/draft/draft-1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.draftId").value("draft-1"))
                .andExpect(jsonPath("$.items[0].taskId").value("TASK-001"))
                .andExpect(jsonPath("$.items[0].resourceGroupName").value("冲压组"));
    }

    @Test
    void shouldReturnNotFoundWhenDraftDoesNotExist() throws Exception {
        when(scheduleDraftService.findById("draft-404"))
                .thenThrow(new java.util.NoSuchElementException("schedule draft not found"));

        mockMvc.perform(get("/api/schedules/draft/draft-404"))
                .andExpect(status().isNotFound());
    }
}
