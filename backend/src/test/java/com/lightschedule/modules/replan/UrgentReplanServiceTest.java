package com.lightschedule.modules.replan;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.lightschedule.modules.resource.ResourceCatalogService;
import com.lightschedule.modules.resource.ResourceEntity;
import com.lightschedule.modules.resource.ResourceGroupService;
import com.lightschedule.modules.resource.ResourceMapper;
import com.lightschedule.modules.scheduling.InitialSchedulingService.ScheduledItem;
import com.lightschedule.domain.model.WorkOrder;
import com.lightschedule.modules.scheduling.PlannerScenarioService;
import com.lightschedule.modules.scheduling.RouteStepService;
import com.lightschedule.modules.taskpool.WorkOrderService;
import java.util.List;
import org.junit.jupiter.api.Test;

class UrgentReplanServiceTest {

    private static WorkOrderService workOrderServiceWithMockData() {
        WorkOrderService service = mock(WorkOrderService.class);
        when(service.loadAll()).thenReturn(List.of(
                new WorkOrder("WO-001", "released", 20, "2026-04-24T08:00:00Z", "ROUTE-01", false, List.of(), "low")));
        return service;
    }

    private static RouteStepService routeStepServiceWithMockData() {
        RouteStepService service = mock(RouteStepService.class);
        when(service.loadTasksForRouteIds(List.of("ROUTE-01"), "LINE-A")).thenReturn(List.of(
                new PlannerScenarioService.PlannerTask("TASK-001", "LINE-A", 120, List.of()),
                new PlannerScenarioService.PlannerTask("TASK-002", "LINE-A", 90, List.of("TASK-001"))));
        return service;
    }

    private static ResourceCatalogService catalogWithMockData() {
        ResourceMapper mapper = mock(ResourceMapper.class);
        ResourceEntity entityA = new ResourceEntity();
        entityA.setResourceId("LINE-A");
        entityA.setGroupName("冲压组");
        entityA.setDefaultPlanner(true);
        ResourceEntity entityB = new ResourceEntity();
        entityB.setResourceId("LINE-B");
        entityB.setGroupName("冲压组");
        entityB.setDefaultPlanner(true);
        when(mapper.selectList(null)).thenReturn(List.of(entityA, entityB));
        return new ResourceCatalogService(mapper);
    }

    @Test
    void shouldReturnSameGroupSuggestionForSingleOverloadedResource() {
        UrgentReplanService service = new UrgentReplanService(
                new SuggestionService(new ResourceGroupService(catalogWithMockData())),
                new PlannerScenarioService(catalogWithMockData(), workOrderServiceWithMockData(), routeStepServiceWithMockData()));

        var result = service.replan("WO-URGENT-001", List.of(
                new ScheduledItem("TASK-001", "LINE-A", "2026-04-24T08:00:00Z", "2026-04-24T10:00:00Z", List.of()),
                new ScheduledItem("TASK-002", "LINE-A", "2026-04-24T09:30:00Z", "2026-04-24T11:30:00Z", List.of()),
                new ScheduledItem("TASK-003", "LINE-B", "2026-04-24T08:30:00Z", "2026-04-24T09:30:00Z", List.of())
        ));

        assertThat(result.affectedTaskIds()).containsExactly("TASK-001", "TASK-002");
        assertThat(result.suggestions().stream().map(SuggestionService.Suggestion::action))
                .containsExactly("reassign_same_group");
        assertThat(result.suggestions().stream().map(SuggestionService.Suggestion::reason))
                .containsExactly("冲压组仍有剩余能力");
    }

    @Test
    void shouldNotSuggestReplanWhenNoResourceIsRepeated() {
        UrgentReplanService service = new UrgentReplanService(
                new SuggestionService(new ResourceGroupService(catalogWithMockData())),
                new PlannerScenarioService(catalogWithMockData(), workOrderServiceWithMockData(), routeStepServiceWithMockData()));

        var result = service.replan("WO-URGENT-001", List.of(
                new ScheduledItem("TASK-001", "LINE-A", "2026-04-24T08:00:00Z", "2026-04-24T10:00:00Z", List.of()),
                new ScheduledItem("TASK-002", "LINE-B", "2026-04-24T08:30:00Z", "2026-04-24T09:30:00Z", List.of())
        ));

        assertThat(result.affectedTaskIds()).isEmpty();
        assertThat(result.suggestions()).isEmpty();
    }

    @Test
    void shouldNotTreatSequentialTasksOnSameResourceAsOverloaded() {
        UrgentReplanService service = new UrgentReplanService(
                new SuggestionService(new ResourceGroupService(catalogWithMockData())),
                new PlannerScenarioService(catalogWithMockData(), workOrderServiceWithMockData(), routeStepServiceWithMockData()));

        var result = service.replan("WO-URGENT-001", List.of(
                new ScheduledItem("TASK-001", "LINE-A", "2026-04-24T08:00:00Z", "2026-04-24T10:00:00Z", List.of()),
                new ScheduledItem("TASK-002", "LINE-A", "2026-04-24T10:00:00Z", "2026-04-24T11:30:00Z", List.of()),
                new ScheduledItem("TASK-003", "LINE-B", "2026-04-24T08:30:00Z", "2026-04-24T09:30:00Z", List.of())
        ));

        assertThat(result.affectedTaskIds()).isEmpty();
        assertThat(result.suggestions()).isEmpty();
    }

    @Test
    void shouldIncludeDependentTasksWhenUpstreamTaskIsDirectlyImpacted() {
        UrgentReplanService service = new UrgentReplanService(
                new SuggestionService(new ResourceGroupService(catalogWithMockData())),
                new PlannerScenarioService(catalogWithMockData(), workOrderServiceWithMockData(), routeStepServiceWithMockData()));

        var result = service.replan("WO-URGENT-001", List.of(
                new ScheduledItem("TASK-001", "LINE-A", "2026-04-24T08:00:00Z", "2026-04-24T10:00:00Z", List.of()),
                new ScheduledItem("TASK-URGENT", "LINE-A", "2026-04-24T09:30:00Z", "2026-04-24T10:30:00Z", List.of()),
                new ScheduledItem("TASK-002", "LINE-B", "2026-04-24T10:30:00Z", "2026-04-24T12:00:00Z", List.of())
        ));

        assertThat(result.affectedTaskIds()).containsExactly("TASK-001", "TASK-URGENT", "TASK-002");
    }

    @Test
    void shouldEscalateSuggestionsWhenMultipleResourcesAreOverloaded() {
        UrgentReplanService service = new UrgentReplanService(
                new SuggestionService(new ResourceGroupService(catalogWithMockData())),
                new PlannerScenarioService(catalogWithMockData(), workOrderServiceWithMockData(), routeStepServiceWithMockData()));

        var result = service.replan("WO-URGENT-001", List.of(
                new ScheduledItem("TASK-001", "LINE-A", "2026-04-24T08:00:00Z", "2026-04-24T10:00:00Z", List.of()),
                new ScheduledItem("TASK-002", "LINE-A", "2026-04-24T09:30:00Z", "2026-04-24T11:30:00Z", List.of()),
                new ScheduledItem("TASK-003", "LINE-B", "2026-04-24T08:30:00Z", "2026-04-24T09:30:00Z", List.of()),
                new ScheduledItem("TASK-004", "LINE-B", "2026-04-24T09:00:00Z", "2026-04-24T10:30:00Z", List.of())
        ));

        assertThat(result.suggestions().stream().map(SuggestionService.Suggestion::action))
                .containsExactly("move_next_slot", "manual_overtime_review");
    }
}
